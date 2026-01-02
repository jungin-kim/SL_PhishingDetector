import torch

try:
    model = torch.jit.load("client_part.ptl", map_location="cpu")
    print("✅ 모델 정상 로드됨.")
except Exception as e:
    print("❌ 모델 로드 실패:", e)
