from pydantic import BaseModel, Field
from typing import Optional
from enum import Enum

class DebtCategory(str, Enum):
    DESIGN = "DESIGN"
    DEFECT = "DEFECT"
    TEST = "TEST"
    REQUIREMENT = "REQUIREMENT"
    DOCUMENTATION = "DOCUMENTATION"

class PredictionRequest(BaseModel):
    comment: str = Field(..., min_length=1)
    surrounding_code: Optional[str] = ""
    language: str = Field(..., min_length=1)

class PredictionResponse(BaseModel):
    is_satd: bool
    debt_category: Optional[DebtCategory] = None
    confidence: float = Field(..., ge=0.0, le=1.0)

class SatdDetectRequest(BaseModel):
    comment: str = Field(..., min_length=1)
    preceding_code: Optional[str] = ""
    succeeding_code: Optional[str] = ""

class SatdDetectResponse(BaseModel):
    label: str
    satd_probability: float = Field(..., ge=0.0, le=1.0)
    non_satd_probability: float = Field(..., ge=0.0, le=1.0)
    confidence: float = Field(..., ge=0.0, le=1.0)
