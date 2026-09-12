import pytest
import os
import torch
from fastapi.testclient import TestClient
from app.main import app
from app.services.codebert_classifier import CodeBertSatdClassifier
from app.schemas.prediction import SatdDetectRequest
from app.core.config import settings

# For isolated unit tests
MODEL_PATH = r"D:\8th semester\SPL3\ml-service\models\codebert_satd\best_model"

@pytest.fixture(scope="module")
def classifier():
    # Only run if model exists
    if not os.path.exists(MODEL_PATH):
        pytest.skip("Model not found locally.")
    return CodeBertSatdClassifier(model_name="microsoft/codebert-base", model_path=MODEL_PATH)

def test_model_loading(classifier):
    assert classifier.model is not None
    assert classifier.tokenizer is not None
    # Verify it is in eval mode
    assert not classifier.model.training
    print(f"Loaded model from: {classifier.model_path}")

def test_device_selection(classifier):
    # Should use CUDA if available, else CPU
    expected_device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    assert classifier.device.type == expected_device.type
    print(f"Selected device: {classifier.device}")

def test_satd_inference(classifier):
    req = SatdDetectRequest(
        comment="// TODO: Fix this workaround later",
        preceding_code="int x = 5;",
        succeeding_code="x += 1;"
    )
    res = classifier.detect_satd(req)
    assert res.label in ["SATD", "NON-SATD"]
    assert 0.0 <= res.satd_probability <= 1.0
    assert 0.0 <= res.non_satd_probability <= 1.0
    assert 0.0 <= res.confidence <= 1.0
    print(f"SATD Inference Result: {res}")

def test_non_satd_inference(classifier):
    req = SatdDetectRequest(
        comment="// Initialize the counter",
        preceding_code="",
        succeeding_code="int i = 0;"
    )
    res = classifier.detect_satd(req)
    assert res.label in ["SATD", "NON-SATD"]
    print(f"NON-SATD Inference Result: {res}")

def test_empty_input_handling(classifier):
    req = SatdDetectRequest(
        comment="   ",
        preceding_code="",
        succeeding_code=""
    )
    with pytest.raises(ValueError):
        classifier.detect_satd(req)

def test_long_input_truncation(classifier):
    req = SatdDetectRequest(
        comment="// Long method ahead",
        preceding_code="int a = 1;\n" * 300,
        succeeding_code="int b = 2;\n" * 300
    )
    # Should not crash
    res = classifier.detect_satd(req)
    assert res.label in ["SATD", "NON-SATD"]
    
    # Verify token limit under the hood
    from tokenizer_utils import tokenize_record
    from config import COL_COMMENT, COL_PRECEDING, COL_SUCCEEDING, COL_SATD
    row = {
        COL_COMMENT: req.comment,
        COL_PRECEDING: req.preceding_code,
        COL_SUCCEEDING: req.succeeding_code,
        COL_SATD: ""
    }
    tokenized = tokenize_record(row, classifier.tokenizer)
    token_count = len(tokenized['input_ids'])
    assert token_count <= 512
    print(f"Long input token count: {token_count}")

def test_fastapi_endpoint():
    if not os.path.exists(MODEL_PATH):
        pytest.skip("Model not found locally.")
        
    client = TestClient(app)
    # Temporary override classifier type to codebert for the test
    settings.classifier_type = "codebert"
    settings.model_path = MODEL_PATH
    
    response = client.post(
        "/api/v1/satd-detect",
        json={
            "comment": "// HACK: Quick fix",
            "preceding_code": "if (true) {",
            "succeeding_code": "}"
        }
    )
    assert response.status_code == 200
    data = response.json()
    assert "label" in data
    assert "satd_probability" in data
    assert "non_satd_probability" in data
    assert "confidence" in data
    assert data["label"] in ["SATD", "NON-SATD"]

def test_fastapi_empty_comment():
    client = TestClient(app)
    response = client.post(
        "/api/v1/satd-detect",
        json={
            "comment": "   ",
            "preceding_code": "",
            "succeeding_code": ""
        }
    )
    assert response.status_code == 422
