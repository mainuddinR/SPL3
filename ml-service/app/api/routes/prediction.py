from fastapi import APIRouter, HTTPException, Depends
from app.schemas.prediction import PredictionRequest, PredictionResponse, SatdDetectRequest, SatdDetectResponse
from app.services.classifier_interface import SATDClassifier
from app.services.mock_classifier import MockSatdClassifier
from app.services.codebert_classifier import CodeBertSatdClassifier
from app.core.config import settings
import logging

router = APIRouter()
logger = logging.getLogger(__name__)

def get_classifier() -> SATDClassifier:
    if settings.classifier_type == "mock":
        return MockSatdClassifier()
    elif settings.classifier_type == "codebert":
        return CodeBertSatdClassifier(model_name=settings.model_name, model_path=settings.model_path)
    raise ValueError(f"Unknown classifier type: {settings.classifier_type}")

@router.post("/predict", response_model=PredictionResponse)
def predict_satd(request: PredictionRequest, classifier: SATDClassifier = Depends(get_classifier)):
    if not request.comment or not request.comment.strip():
        raise HTTPException(status_code=422, detail="Comment cannot be empty or whitespace only")
    try:
        return classifier.predict(request)
    except NotImplementedError as e:
        logger.error(f"Classifier not implemented: {e}")
        raise HTTPException(status_code=501, detail=str(e))
    except Exception as e:
        logger.error(f"Prediction failed: {e}")
        raise HTTPException(status_code=500, detail="Internal server error during prediction")

@router.post("/satd-detect", response_model=SatdDetectResponse)
def detect_satd(request: SatdDetectRequest, classifier: SATDClassifier = Depends(get_classifier)):
    if not request.comment or not request.comment.strip():
        raise HTTPException(status_code=422, detail="Comment cannot be empty or whitespace only")
    try:
        if not hasattr(classifier, 'detect_satd'):
            raise NotImplementedError(f"{type(classifier).__name__} does not implement detect_satd.")
        return classifier.detect_satd(request)
    except NotImplementedError as e:
        logger.error(f"Classifier not implemented: {e}")
        raise HTTPException(status_code=501, detail=str(e))
    except ValueError as e:
        logger.error(f"Validation error: {e}")
        raise HTTPException(status_code=422, detail=str(e))
    except Exception as e:
        logger.error(f"SATD detection failed: {e}")
        raise HTTPException(status_code=500, detail="Internal server error during prediction")
