# SATD Detection ML Service

This is the independent FastAPI ML microservice for Automated Code Review and Technical Debt (SATD) classification.

## Project Structure
This service is intentionally isolated from the Spring Boot orchestration backend. It exposes inference endpoints for SATD classification.

## Prerequisites
- Python 3.10+
- `pip`

## Installation
Create a virtual environment and install dependencies:
```bash
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install -r requirements.txt
```

## Running the Application
Start the FastAPI server via Uvicorn:
```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```
Alternatively, configure `PORT` in `.env`.

## Endpoints
### Health Check
```bash
curl http://localhost:8000/health
# Response: {"status": "ok"}
```

### Prediction API
```bash
curl -X POST http://localhost:8000/api/v1/predict \
  -H "Content-Type: application/json" \
  -d '{"comment":"TODO: refactor this method","surrounding_code":"public void process() { ... }","language":"Java"}'
```

## Classifier Selection (Mock vs CodeBERT)
By default, the service uses a **Mock Classifier**. This mock is a deterministic development stub that flags keywords like `TODO`, `FIXME`, or `HACK` as SATD. 

**IMPORTANT**: The mock classifier is NOT the final ML model. 
A separate training pipeline will later utilize our 62K CSV dataset to fine-tune a Hugging Face CodeBERT model. Once the model is trained, it will replace the mock classifier.

To select the classifier, use the `.env` file or environment variables:
```env
CLASSIFIER_TYPE=mock      # Uses MockSatdClassifier (Default)
CLASSIFIER_TYPE=codebert  # Uses CodeBertSatdClassifier
MODEL_NAME=microsoft/codebert-base
MODEL_PATH=/path/to/local/model
```
