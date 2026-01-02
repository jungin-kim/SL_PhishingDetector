# inference_server.py  (Python 3.7 호환)
import os
import torch
import torch.nn as nn
from typing import List, Optional  # ✅ Python 3.7에서는 typing.List 사용
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from transformers import T5Tokenizer, T5ForConditionalGeneration
from transformers.models.t5.modeling_t5 import T5LayerNorm

# =========================
# Config
# =========================
SPLIT_LAYER = 3
PRETRAINED = "t5-small"
SERVER_MODEL_DIR = r"download_model/server_model_dir"
MAX_LEN = 128

# =========================
# Model
# =========================
class ServerModel(nn.Module):
    def __init__(self, pretrained_model_name=PRETRAINED, split_layer=SPLIT_LAYER):
        super().__init__()
        self.model = T5ForConditionalGeneration.from_pretrained(pretrained_model_name)
        self.split_layer = split_layer
        self.layer_norm = T5LayerNorm(self.model.config.d_model)
        self.dropout = nn.Dropout(0.1)
        self.classification_head = nn.Linear(self.model.config.d_model, 2)

    def forward(self, hidden_states, attention_mask=None):
        for i, layer_module in enumerate(self.model.encoder.block):
            if i >= self.split_layer:
                hidden_states = layer_module(hidden_states, attention_mask=attention_mask)[0]
        hidden_states = self.layer_norm(hidden_states)
        hidden_states = self.dropout(hidden_states)
        pooled = hidden_states[:, 0, :]
        logits = self.classification_head(pooled)
        return logits

# =========================
# Lazy singletons
# =========================
app = FastAPI(title="Split Learning Phishing Server")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"], allow_methods=["*"], allow_headers=["*"], allow_credentials=True
)

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
_server_model: Optional[ServerModel] = None
_tokenizer: Optional[T5Tokenizer] = None

def get_server_model() -> ServerModel:
    global _server_model
    if _server_model is None:
        model = ServerModel(pretrained_model_name=PRETRAINED, split_layer=SPLIT_LAYER)
        # HF 디렉토리 로드 시도
        if os.path.isdir(SERVER_MODEL_DIR):
            try:
                model.model = T5ForConditionalGeneration.from_pretrained(SERVER_MODEL_DIR)
                print(f"[INFO] Loaded HF-style weights from: {SERVER_MODEL_DIR}")
            except Exception as e:
                print(f"[WARN] HF dir load failed: {e}")
        # state_dict도 있으면 추가로 시도
        pt_path = os.path.join(SERVER_MODEL_DIR, "server_model.pt")
        if os.path.exists(pt_path):
            try:
                sd = torch.load(pt_path, map_location=device)
                model.load_state_dict(sd, strict=False)
                print(f"[INFO] Loaded state_dict (strict=False) from: {pt_path}")
            except Exception as e:
                print(f"[WARN] state_dict load failed: {e}")

        model.to(device)
        model.eval()
        _server_model = model
    return _server_model

def get_tokenizer() -> T5Tokenizer:
    global _tokenizer
    if _tokenizer is None:
        _tokenizer = T5Tokenizer.from_pretrained(PRETRAINED)
    return _tokenizer

# =========================
# Schemas (✅ typing.List 사용)
# =========================
class TextRequest(BaseModel):
    text: str

class TokensResponse(BaseModel):
    input_ids: List[int]
    attention_mask: List[int]
    max_len: int = MAX_LEN
    vocab_size: int

class SmashedDataRequest(BaseModel):
    smashed_data: List[float]  # flattened or [S,D] serialized

# =========================
# Endpoints
# =========================
@app.get("/health")
def health():
    return {"status": "ok", "device": str(device)}

@app.post("/tokenize", response_model=TokensResponse)
def tokenize(data: TextRequest):
    try:
        tok = get_tokenizer()
        enc = tok(
            data.text,
            padding="max_length",
            truncation=True,
            max_length=MAX_LEN,
            return_tensors="pt"
        )
        ids = enc["input_ids"][0].tolist()
        mask = enc["attention_mask"][0].tolist()
        return TokensResponse(
            input_ids=ids,
            attention_mask=mask,
            max_len=MAX_LEN,
            vocab_size=tok.vocab_size
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Tokenizer failed: {e}")

@app.post("/predict/")
def predict(data: SmashedDataRequest):
    try:
        tens = torch.tensor(data.smashed_data, dtype=torch.float32, device=device)

        # [1, S, D]로 맞추기
        if tens.ndim == 1:
            if tens.numel() % MAX_LEN != 0:
                raise ValueError("Flattened smashed length {} not divisible by seq_len={}".format(tens.numel(), MAX_LEN))
            d_model = int(tens.numel() // MAX_LEN)
            tens = tens.view(1, MAX_LEN, d_model)
        elif tens.ndim == 2:
            tens = tens.unsqueeze(0)
        elif tens.ndim != 3:
            raise ValueError("Invalid smashed_data shape: {}".format(tuple(tens.shape)))

        attn = torch.ones((tens.size(0), tens.size(1)), dtype=torch.long, device=device)
        model = get_server_model()
        with torch.no_grad():
            logits = model(hidden_states=tens, attention_mask=attn)
            probs = torch.softmax(logits, dim=1)
            p_phish = float(probs[0, 1].item())

        return {"phishing_probability": p_phish, "is_phishing": p_phish > 0.5}
    except Exception as e:
        raise HTTPException(status_code=500, detail="Inference failed: {}".format(e))
