from abc import ABC, abstractmethod
from app.schemas.prediction import PredictionRequest, PredictionResponse

class SATDClassifier(ABC):
    @abstractmethod
    def predict(self, request: PredictionRequest) -> PredictionResponse:
        pass
