from flask import Flask, send_file
from transformers import T5ForConditionalGeneration
import torch
import os

app = Flask(__name__)

# 설정값
ORIGINAL_MODEL_DIR = "download_model/client_model_dir"
LITE_MODEL = "client_part.ptl"
SPLIT_LAYER = 4

# 클라이언트 측 인코더 정의
class ClientEncoder(torch.nn.Module):
    def __init__(self, full_model, split_layer):
        super().__init__()
        self.encoder = full_model.get_encoder()
        self.split_layer = split_layer
        self.partial_layers = torch.nn.ModuleList(self.encoder.block[:split_layer])
        self.encoder_embed = self.encoder.embed_tokens
        self.encoder_norm = self.encoder.final_layer_norm
        self.encoder_dropout = self.encoder.dropout

    def forward(self, input_ids, attention_mask):
        embeddings = self.encoder_embed(input_ids)
        hidden_states = self.encoder_dropout(embeddings)

        for i in range(self.split_layer):
            hidden_states = self.partial_layers[i](hidden_states, attention_mask=attention_mask)[0]

        hidden_states = self.encoder_norm(hidden_states)
        return hidden_states

# 모델 다운로드 엔드포인트
@app.route("/download_model", methods=["GET"])
def download():
    if not os.path.exists(LITE_MODEL):
        print("⚠️ Lite 모델이 존재하지 않음 → 새로 생성 중")

        # 원본 T5 모델 불러오기
        model = T5ForConditionalGeneration.from_pretrained(ORIGINAL_MODEL_DIR)
        model.eval()

        # Split된 client encoder 생성
        client_model = ClientEncoder(model, SPLIT_LAYER)
        client_model.eval()

        # dummy 입력
        dummy_input_ids = torch.ones(1, 128, dtype=torch.long)
        dummy_attention_mask = torch.ones(1, 128, dtype=torch.long)

        # TorchScript trace 변환 후 저장
        traced_model = torch.jit.trace(client_model, (dummy_input_ids, dummy_attention_mask))
        traced_model._save_for_lite_interpreter(LITE_MODEL)

        print("✅ 모델 변환 및 저장 완료")

    # 모델 파일 존재 시 사이즈 출력
    #file_size = os.path.getsize(LITE_MODEL)
    #print(f"📦 Serving model file: {LITE_MODEL}, size = {file_size} bytes")

    #return send_file(LITE_MODEL, mimetype='application/octet-stream', as_attachment=True)
    print("📤 [SERVER] 모델 전송 시작")
    return send_file(
        LITE_MODEL,
        as_attachment=True,
        conditional=False  # 강제로 전체 파일 전송
    )

# 서버 실행
if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8000)
