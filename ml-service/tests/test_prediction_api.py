from fastapi.testclient import TestClient
from app.main import app
from app.core.config import settings
import pytest

client = TestClient(app)

@pytest.fixture(autouse=True)
def override_settings():
    settings.classifier_type = "mock"
    yield
    
def test_valid_satd_todo():
    response = client.post("/api/v1/predict", json={
        "comment": "TODO: fix this",
        "surrounding_code": "def test(): pass",
        "language": "Java"
    })
    assert response.status_code == 200
    data = response.json()
    assert data["is_satd"] == True
    assert data["debt_category"] == "DESIGN"
    assert 0.0 <= data["confidence"] <= 1.0

def test_valid_non_satd():
    response = client.post("/api/v1/predict", json={
        "comment": "Initializes the service",
        "surrounding_code": "",
        "language": "Java"
    })
    assert response.status_code == 200
    data = response.json()
    assert data["is_satd"] == False
    assert data["debt_category"] is None
    assert 0.0 <= data["confidence"] <= 1.0

def test_empty_comment():
    response = client.post("/api/v1/predict", json={
        "comment": "   ",
        "surrounding_code": "",
        "language": "Java"
    })
    assert response.status_code == 422

def test_missing_comment():
    response = client.post("/api/v1/predict", json={
        "surrounding_code": "",
        "language": "Java"
    })
    assert response.status_code == 422

def test_missing_language():
    response = client.post("/api/v1/predict", json={
        "comment": "TODO: fix",
        "surrounding_code": ""
    })
    assert response.status_code == 422

def test_classifier_exception():
    settings.classifier_type = "codebert"
    response = client.post("/api/v1/predict", json={
        "comment": "TODO",
        "language": "Java"
    })
    assert response.status_code == 501
    settings.classifier_type = "mock"
