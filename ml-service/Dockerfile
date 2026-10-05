FROM python:3.11-slim
WORKDIR /app

COPY requirements.txt ./
RUN python -m pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu --extra-index-url https://pypi.org/simple 'torch==2.14.0+cpu' \
    && pip install --no-cache-dir -r requirements.txt 'transformers==5.17.0' 'pandas==3.0.5'

COPY app/ app/
COPY codebert_training/config.py codebert_training/input_builder.py codebert_training/tokenizer_utils.py codebert_training/

ENV CLASSIFIER_TYPE=codebert \
    MODEL_NAME=microsoft/codebert-base \
    MODEL_PATH=/app/models/codebert_satd/best_model \
    HF_HUB_OFFLINE=1 \
    TRANSFORMERS_OFFLINE=1
EXPOSE 8001
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8001/health', timeout=3)"
CMD ["sh", "-c", "test -s /app/models/codebert_satd/best_model/model.safetensors && exec uvicorn app.main:app --host 0.0.0.0 --port 8001"]
