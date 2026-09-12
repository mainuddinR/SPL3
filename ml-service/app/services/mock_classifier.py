from app.services.classifier_interface import SATDClassifier
from app.schemas.prediction import PredictionRequest, PredictionResponse, DebtCategory

class MockSatdClassifier(SATDClassifier):
    """
    Temporary deterministic development stub.
    This is NOT the final SATD classifier and does not use CodeBERT.
    """
    def predict(self, request: PredictionRequest) -> PredictionResponse:
        comment_upper = request.comment.upper()
        if "TODO" in comment_upper or "FIXME" in comment_upper or "HACK" in comment_upper:
            return PredictionResponse(
                is_satd=True,
                debt_category=DebtCategory.DESIGN,
                confidence=0.90
            )
        return PredictionResponse(
            is_satd=False,
            debt_category=None,
            confidence=0.95
        )
