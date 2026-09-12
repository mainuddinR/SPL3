# PENTACET CodeBERT Training Package

## 1. Dataset Location
**The official PENTACET 30K dataset is read-only.**
Location: `D:\8th semester\SPL3\data\pentacet\controlled_30k\`

**Note:** The 30K dataset is a controlled balanced subset with 15,000 SATD and 15,000 NON-SATD records; this is not the natural PENTACET class distribution.

## 2. Training Package Location
Location: `D:\8th semester\SPL3\ml-service\codebert_training\`
This package is designed to be zipped and exported to Google Colab for training.

## 3. Dataset Schema
- `project_name`
- `comment_content`
- `comment_preceding_code`
- `comment_succeeding_code`
- `satd_affliction`
- (Other metadata preserved for reference)

## 4. Binary Label Definition
- `SATD (1)`: Non-empty `satd_affliction`
- `NON-SATD (0)`: Empty `satd_affliction`

## 5. Input Format
The `input_builder.py` constructs text exactly like this:
```
COMMENT: <comment>
PRECEDING_CODE: <preceding code>
SUCCEEDING_CODE: <succeeding code>
```

## 6. 512-Token Truncation Strategy
Because CodeBERT accepts a maximum of 512 tokens:
1. The **Comment** is tokenized and preserved completely (assuming it's < 512 tokens).
2. The remaining token budget is split equally between **Preceding Code** and **Succeeding Code**.
3. Preceding code is truncated from the top (keeping the bottom lines closest to the comment).
4. Succeeding code is truncated from the bottom (keeping the top lines closest to the comment).
5. All pieces are assembled using `[CLS]` and `[SEP]` tokens.

## 7. Smoke-Test Procedure
`python smoke_test.py`
This will run the tokenizer on 20 representative records from the `train.csv` split, verifying that the input builder, truncation, and tokenizer logic work without crashing.

## 8. Future Colab Setup
1. Upload this directory and the `controlled_30k` dataset directory to Colab.
2. Adjust `config.py` paths to match Colab (`/content/dataset/...`).
3. Run `pip install -r requirements.txt`.

## 9. Future Training Command
`python train.py`
This script uses Hugging Face `Trainer` to automatically run training, evaluate on the validation set, and save the best model based on F1 score.

## 10. Future Evaluation Command
`python evaluate.py`
This uses `test.csv` (which the model has never seen) to compute final Accuracy, Precision, Recall, and F1.
