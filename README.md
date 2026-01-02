# SL_PhishingDetector

**Split Learning 기반 피싱 URL 탐지 시스템**

## 📖 프로젝트 개요
**SL_PhishingDetector**는 딥러닝 모델(T5-small)을 모바일 기기(Client)와 서버(Server)로 분할하여 구동하는 **Split Learning(분할 학습)** 프레임워크 기반의 피싱 탐지 시스템입니다.

기존의 클라우드 전송 방식과 달리, 모델의 초기 연산(Front Layers)을 모바일 기기에서 수행하고, 중간 결과값(Smashed Data)만을 서버로 전송하여 나머지 연산(Back Layers)을 수행합니다. 이를 통해 데이터 프라이버시를 강화하고 서버의 연산 부하를 분산시키는 것을 목표로 합니다.

---

## 🏗 시스템 아키텍처 (System Architecture)

이 프로젝트는 **T5-small** 모델을 기반으로 하며, 특정 레이어(Split Layer=3)를 기준으로 모델을 두 부분으로 나누어 협업 추론(Cooperative Inference)을 수행합니다.

### 🔄 전체 동작 플로우 (Workflow)
1.  **URL 감지 및 입력**: 
    * 사용자가 앱에 URL을 직접 입력하거나, 수신된 SMS에서 `SmsReceiver`가 URL을 자동 추출합니다.
2.  **Tokenization (Server)**:
    * 모바일 경량화를 위해 토크나이저(Vocab)는 서버에 위치합니다.
    * 클라이언트가 텍스트를 보내면 서버(`inference_server.py`)가 토큰화된 ID(`input_ids`)를 반환합니다.
3.  **Client Inference (Front Part)**:
    * 클라이언트는 **PyTorch Mobile** 모델(`client_part.ptl`)을 사용하여 모델의 앞단(Layer 0~2) 연산을 수행합니다.
    * 이 과정에서 생성된 중간 결과물을 **Smashed Data**라고 합니다.
4.  **Data Transmission**:
    * 클라이언트는 Smashed Data를 서버로 업로드합니다.
5.  **Server Inference (Back Part)**:
    * 서버는 Smashed Data를 이어받아 나머지 모델(Layer 3~End) 연산을 수행합니다.
    * 최종적으로 피싱 확률을 계산하여 클라이언트에 반환합니다.

---

## 📂 디렉토리 및 코드 구조

### 📱 1. PhishingDetector_client (Android)
안드로이드 기반의 클라이언트 앱으로, 사용자 인터페이스와 로컬 모델 연산을 담당합니다.

* **`MainActivity.kt`**
    * 앱의 메인 화면 및 로직을 담당합니다.
    * 서버 통신(`OkHttp`)을 통해 토큰화 요청 및 Smashed Data 전송을 관리합니다.
    * PyTorch Mobile 모듈을 로드하고 `forward` 연산을 수행합니다.
* **`sms/SmsReceiver.kt`**
    * SMS 수신을 감지하는 `BroadcastReceiver`입니다.
    * 문자 메시지 본문에서 정규식(`Regex`)을 이용해 URL을 추출하고, 알림(Notification)을 생성합니다.
* **`ml/ClientModelLoader.kt`**
    * 클라이언트용 모델 파일(`.ptl`)을 관리합니다.
    * 앱 실행 시 로컬에 모델이 없으면 서버(`BASE/download_model`)로부터 모델을 다운로드하여 초기화합니다.
* **`utils/NetworkUtils.kt`**
    * 네트워크 연결 상태를 확인하는 유틸리티 클래스입니다.

### 🖥️ 2. PhishingDetector_server (Python Server)
FastAPI 기반의 추론 서버로, 토큰화 및 모델의 뒷단 연산을 처리합니다.

* **`inference_server.py`**
    * 메인 서버 애플리케이션입니다.
    * **Endpoints**:
        * `/tokenize`: 텍스트를 받아 T5 Tokenizer로 변환 (`max_length=128`).
        * `/predict/`: Smashed Data를 받아 최종 피싱 여부를 판별.
    * **Logic**: 클라이언트로부터 받은 데이터를 PyTorch Tensor로 변환 후, `ServerModel`에 주입하여 추론합니다.
* **`server_model.py`**
    * 서버 측 모델 클래스(`ServerModel`)가 정의되어 있습니다.
    * Hugging Face의 `T5ForConditionalGeneration` 모델에서 Encoder의 뒷부분과 Decoder, Head를 포함합니다.

---

## 🛠 기술 스택 (Tech Stack)

### Client (Android)
* **Language**: Kotlin
* **ML Engine**: PyTorch Mobile (LiteModuleLoader)
* **Networking**: OkHttp3
* **UI**: XML Layouts (Activity based)

### Server (Python)
* **Language**: Python 3.7+
* **Framework**: FastAPI, Uvicorn
* **ML Engine**: PyTorch, Hugging Face Transformers
* **Model**: T5-small (Pre-trained)

---

## 🚀 설치 및 실행 (Setup & Usage)

### 전제 조건
* **Server**: Python 3.7 이상, PyTorch, Transformers, FastAPI 설치 필요.
* **Client**: Android Studio, Android SDK 설치 필요.
* **Model Files**: `client_part.ptl` 및 서버 모델 가중치 파일이 준비되어 있어야 합니다.

### 실행 방법
1.  **서버 실행**:
    ```bash
    cd PhishingDetector_server
    uvicorn inference_server:app --host 0.0.0.0 --port 5000 --reload
    ```
2.  **클라이언트 실행**:
    * Android Studio에서 `PhishingDetector_client` 프로젝트를 엽니다.
    * `MainActivity.kt`의 `BASE` URL을 서버 IP 주소로 수정합니다. (에뮬레이터 사용 시 `10.0.2.2` 유지)
    * 앱을 빌드하고 실행합니다.

### ⚠️ 주의사항
* **네트워크 설정**: 클라이언트와 서버가 통신할 수 있도록 네트워크 환경(포트 포워딩, 방화벽 등)을 확인해야 합니다.
* **대용량 파일**: 모델 파일(`.ptl`, `.bin` 등)은 GitHub 용량 제한으로 인해 리포지토리에 포함되지 않았을 수 있습니다. 별도로 준비하여 지정된 경로(`download_model/`)에 위치시켜야 합니다.

---
*Created for Split Learning Phishing Detection Project*
