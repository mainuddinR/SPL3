# SATD Detection ML Service

This FastAPI service classifies source-code comments as SATD or non-SATD. The project uses a fine-tuned CodeBERT checkpoint together with surrounding source-code context.

## Architecture

Spring Boot calls the ML API for inference. The service is separate from the Angular frontend and Oracle persistence layer.

In Docker Compose, the service starts on port **8001** with `CLASSIFIER_TYPE=codebert`. The trained model is mounted read-only from `ml-service/models/codebert_satd/best_model/`; its weights are not stored in Git.

## API

- `GET /health` — health check
- `POST /api/v1/predict` — SATD inference endpoint

Request and response schemas are defined in `app/schemas/prediction.py`.

## Running locally

Create a Python environment and install dependencies from `requirements.txt`. For direct Uvicorn runs, select the classifier and port explicitly:

```powershell
$env:CLASSIFIER_TYPE = "codebert"
$env:MODEL_PATH = "models/codebert_satd/best_model"
uvicorn app.main:app --host 127.0.0.1 --port 8001
```

The `mock` classifier is kept for development tests; it is **not** the model used by the Docker application.

## Training and tests

The training code and input formatting are in `codebert_training/`. The current training dataset is the controlled 30K PENTACET subset, split by project to avoid overlap between train, validation and test projects.

The final data preparation summary is in `reports/pentacet_final_30k_dataset_report.md`.

```powershell
python -m pytest tests
```

The checkpoint and the dataset files remain local and are excluded from the repository.
