from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="SATD Detection ML Service", description="Automated Code Review ML backend", version="1.0.0")

class CodeRequest(BaseModel):
    code_snippet: str
    language: str = "java"

class PredictionResponse(BaseModel):
    is_satd: bool
    severity_score: float
    suggestion: str

@app.get("/")
def read_root():
    return {"message": "Welcome to the SATD Detection ML Service"}

@app.post("/api/v1/predict", response_model=PredictionResponse)
def predict_satd(request: CodeRequest):
    # TODO: Integrate loaded CodeBERT model here
    return PredictionResponse(
        is_satd=False,
        severity_score=0.0,
        suggestion="No technical debt detected (placeholder)."
    )
