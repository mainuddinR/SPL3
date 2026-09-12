from app.services.classifier_interface import SATDClassifier
from app.schemas.prediction import PredictionRequest, PredictionResponse, SatdDetectRequest, SatdDetectResponse
import logging
import torch
import sys
import os
from transformers import RobertaForSequenceClassification

# Add ml-service/codebert_training to path to import tokenizer_utils and input_builder
current_dir = os.path.dirname(os.path.abspath(__file__))
# current_dir is ml-service/app/services
ml_service_dir = os.path.abspath(os.path.join(current_dir, "..", ".."))
codebert_training_dir = os.path.join(ml_service_dir, "codebert_training")
if codebert_training_dir not in sys.path:
    sys.path.append(codebert_training_dir)

from tokenizer_utils import get_tokenizer, tokenize_record

logger = logging.getLogger(__name__)

class CodeBertSatdClassifier(SATDClassifier):
    def __init__(self, model_name: str, model_path: str = None):
        self.model_name = model_name
        self.model_path = model_path
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        
        load_path = model_path if model_path and os.path.exists(model_path) else model_name
        logger.info(f"Loading CodeBERT model from {load_path} onto {self.device}...")
        try:
            from transformers import RobertaTokenizer
            self.tokenizer = RobertaTokenizer.from_pretrained(load_path)
            self.model = RobertaForSequenceClassification.from_pretrained(
                load_path,
                num_labels=2
            )
            self.model.to(self.device)
            self.model.eval()
            logger.info("Model loaded successfully.")
        except Exception as e:
            logger.error(f"Failed to load CodeBERT model: {e}")
            raise RuntimeError(f"Failed to load model: {e}")

    def predict(self, request: PredictionRequest) -> PredictionResponse:
        # Backward compatibility for existing predict endpoint
        # The existing endpoint uses `surrounding_code` rather than preceding/succeeding separately.
        # We will split it roughly in half if provided, or pass as succeeding.
        detect_req = SatdDetectRequest(
            comment=request.comment,
            preceding_code="",
            succeeding_code=request.surrounding_code or ""
        )
        result = self.detect_satd(detect_req)
        return PredictionResponse(
            is_satd=(result.label == "SATD"),
            confidence=result.confidence
        )
        
    def detect_satd(self, request: SatdDetectRequest) -> SatdDetectResponse:
        if not request.comment or not request.comment.strip():
            raise ValueError("Comment cannot be empty.")
            
        # 1. Build a dummy row dictionary simulating the CSV row structure
        # config.py in codebert_training expects specific column names.
        from config import COL_COMMENT, COL_PRECEDING, COL_SUCCEEDING, COL_SATD
        row = {
            COL_COMMENT: request.comment,
            COL_PRECEDING: request.preceding_code or "",
            COL_SUCCEEDING: request.succeeding_code or "",
            COL_SATD: "" # Dummy for label calculation, won't be used for input_ids
        }
        
        # 2. Tokenize using the exact training tokenization logic
        # tokenize_record enforces the 512-token limit correctly.
        try:
            tokenized = tokenize_record(row, self.tokenizer)
        except Exception as e:
            logger.error(f"Tokenization error: {e}")
            raise RuntimeError(f"Tokenization error: {e}")
            
        input_ids = torch.tensor([tokenized['input_ids']], dtype=torch.long).to(self.device)
        attention_mask = torch.tensor([tokenized['attention_mask']], dtype=torch.long).to(self.device)
        
        # 3. Inference
        with torch.no_grad():
            outputs = self.model(input_ids=input_ids, attention_mask=attention_mask)
            logits = outputs.logits
            probs = torch.nn.functional.softmax(logits, dim=-1)[0].cpu().numpy()
            
        non_satd_prob = float(probs[0])
        satd_prob = float(probs[1])
        
        label = "SATD" if satd_prob >= 0.5 else "NON-SATD"
        confidence = max(satd_prob, non_satd_prob)
        
        logger.debug(f"Prediction: {label} (SATD: {satd_prob:.4f}, NON-SATD: {non_satd_prob:.4f})")
        
        return SatdDetectResponse(
            label=label,
            satd_probability=satd_prob,
            non_satd_probability=non_satd_prob,
            confidence=confidence
        )
