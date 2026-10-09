from app.api.routes import prediction
from app.core.config import settings
from app.main import app
from fastapi.testclient import TestClient


def test_classifier_initialized_once_for_same_real_config(monkeypatch):
    loaded = []
    class FakeModel:
        pass
    def factory(model_name, model_path):
        loaded.append((model_name, model_path))
        return FakeModel()
    monkeypatch.setattr(prediction, "CodeBertSatdClassifier", factory)
    prediction._classifier_for_config.cache_clear()
    first = prediction._classifier_for_config("codebert", "test-model", "/tmp/test")
    second = prediction._classifier_for_config("codebert", "test-model", "/tmp/test")
    assert first is second
    assert loaded == [("test-model", "/tmp/test")]
    prediction._classifier_for_config.cache_clear()


def test_model_cache_does_not_cross_mock_and_codebert_configuration(monkeypatch):
    prediction._classifier_for_config.cache_clear()
    monkeypatch.setattr(prediction, "CodeBertSatdClassifier", lambda **kwargs: object())
    mock = prediction._classifier_for_config("mock", "test-model", None)
    real = prediction._classifier_for_config("codebert", "test-model", None)
    assert mock is not real
    assert prediction._classifier_for_config("codebert", "test-model", None) is real
    prediction._classifier_for_config.cache_clear()


def test_mock_health_does_not_load_codebert(monkeypatch):
    monkeypatch.setattr(settings, "classifier_type", "mock")
    prediction._classifier_for_config.cache_clear()
    with TestClient(app) as client:
        assert client.get("/health").json() == {"status": "ok"}
    prediction._classifier_for_config.cache_clear()
