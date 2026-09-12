# CodeBERT Inference Pipeline

This module integrates the fine-tuned CodeBERT model for Self-Admitted Technical Debt (SATD) detection into the FastAPI backend.

## Model
The actual fine-tuned model weights MUST be placed in `ml-service/models/codebert_satd/best_model`. The service expects:
- `config.json`
- `model.safetensors` (or `pytorch_model.bin`)
- `tokenizer.json` / `tokenizer_config.json` / `vocab.json` / `merges.txt`

It uses the `microsoft/codebert-base` architecture configured for sequence classification (2 labels).

## Preprocessing
Inference preprocessing strictly matches the tokenization logic used during model training on the PENTACET 30K dataset.
It relies on `codebert_training/tokenizer_utils.py` and `codebert_training/input_builder.py`.

The input structure is constructed as:
`[CLS] COMMENT [SEP] PRECEDING_CODE [SEP] SUCCEEDING_CODE [SEP]`

CodeBERT has a hard limit of **512 tokens**. The preprocessing logic dynamically truncates the source code to ensure that the comment is preserved and the total length does not exceed 512 tokens, guaranteeing that inference will never crash on long inputs.

## Device Strategy (CPU/GPU)
The classifier automatically selects `cuda` if an NVIDIA GPU is available and properly configured with PyTorch. Otherwise, it gracefully falls back to `cpu`. 

## API Endpoint

**Endpoint:** `POST /api/v1/satd-detect`

**Request Schema (`SatdDetectRequest`):**
```json
{
  "comment": "// TODO: refactor this hack",
  "preceding_code": "int x = 0;",
  "succeeding_code": "x++;"
}
```

**Response Schema (`SatdDetectResponse`):**
```json
{
  "label": "SATD",
  "satd_probability": 0.9998,
  "non_satd_probability": 0.0002,
  "confidence": 0.9998
}
```

## How to Start the Service
From the `ml-service` directory, run:
```bash
uvicorn app.main:app --reload
```
The model is loaded **only once** upon the initialization of the `CodeBertSatdClassifier` object, using `model.eval()` and `torch.no_grad()` to maximize inference efficiency.

## How to Test
From the `ml-service` directory, run:
```bash
pytest tests/test_codebert_inference.py -s
```
This requires the local model weights to be present in the designated directory.
