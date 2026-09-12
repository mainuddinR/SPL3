import json
import os

NOTEBOOK_PATH = r"D:\8th semester\SPL3\ml-service\codebert_training\CodeBERT_SATD_30K_FULL_TRAINING.ipynb"

def create_cell(cell_type, source):
    return {
        "cell_type": cell_type,
        "metadata": {},
        "source": source if isinstance(source, list) else [source],
        **({"execution_count": None, "outputs": []} if cell_type == "code" else {})
    }

def build_notebook():
    cells = []
    
    # 1. Project Configuration
    cells.append(create_cell("markdown", "# 1. Project Configuration\nDefine the constants and output directories for Google Colab."))
    cells.append(create_cell("code", """PROJECT_DRIVE_ROOT = "/content/drive/MyDrive/SPL3"
DATASET_DIR = f"{PROJECT_DRIVE_ROOT}/data/pentacet/controlled_30k"
PACKAGE_DIR = f"{PROJECT_DRIVE_ROOT}/ml-service/codebert_training"
OUTPUT_DIR = f"{PACKAGE_DIR}/outputs/full_30k"

TRAIN_CSV = f"{DATASET_DIR}/train.csv"
VAL_CSV = f"{DATASET_DIR}/validation.csv"
TEST_CSV = f"{DATASET_DIR}/test.csv"
"""))

    # 2. Google Drive Mount
    cells.append(create_cell("markdown", "# 2. Google Drive Mount\nMount Google Drive to access the dataset and save the trained model."))
    cells.append(create_cell("code", """from google.colab import drive
import os

drive.mount('/content/drive')

# Validate paths
assert os.path.exists(TRAIN_CSV), f"Train CSV missing: {TRAIN_CSV}"
assert os.path.exists(VAL_CSV), f"Validation CSV missing: {VAL_CSV}"
assert os.path.exists(TEST_CSV), f"Test CSV missing: {TEST_CSV}"
assert os.path.exists(PACKAGE_DIR), f"Package directory missing: {PACKAGE_DIR}"

# Add package directory to sys.path so we can import our existing modules
import sys
if PACKAGE_DIR not in sys.path:
    sys.path.append(PACKAGE_DIR)
"""))

    # 3. Dependency Installation
    cells.append(create_cell("markdown", "# 3. Dependency Installation\nInstall the required dependencies directly using the requirements.txt from the package."))
    cells.append(create_cell("code", """!pip install -r "{PACKAGE_DIR}/requirements.txt"
"""))

    # 4. GPU Check
    cells.append(create_cell("markdown", "# 4. GPU Check\nVerify that an NVIDIA GPU is attached and ready."))
    cells.append(create_cell("code", """import torch
print(f"CUDA available: {torch.cuda.is_available()}")
if torch.cuda.is_available():
    print(f"GPU device: {torch.cuda.get_device_name(0)}")
else:
    print("WARNING: No GPU found. Training will be extremely slow.")
"""))

    # 5. Package Import
    cells.append(create_cell("markdown", "# 5. Package Import\nImport the custom dataset builder, tokenizer utilities, and input formatter from our package."))
    cells.append(create_cell("code", """import pandas as pd
import json
import time
from sklearn.metrics import accuracy_score, precision_recall_fscore_support, roc_auc_score, confusion_matrix
from transformers import RobertaForSequenceClassification, Trainer, TrainingArguments, EarlyStoppingCallback
import matplotlib.pyplot as plt

# Import custom package modules
from tokenizer_utils import get_tokenizer, tokenize_record
from input_builder import get_binary_label
from dataset import SATDDataset
from config import MARKER_COMMENT, MARKER_PRECEDING, MARKER_SUCCEEDING
"""))

    # 6. Dataset Loading
    cells.append(create_cell("markdown", "# 6. Dataset Loading\nLoad the train, validation, and test datasets. Do NOT modify the data or rebalance classes."))
    cells.append(create_cell("code", """print("Loading datasets...")
df_train = pd.read_csv(TRAIN_CSV, dtype=str).fillna('')
df_val = pd.read_csv(VAL_CSV, dtype=str).fillna('')
df_test = pd.read_csv(TEST_CSV, dtype=str).fillna('')

print(f"Train size: {len(df_train)}")
print(f"Validation size: {len(df_val)}")
print(f"Test size: {len(df_test)}")
"""))

    # 7. Schema/Label Verification
    cells.append(create_cell("markdown", "# 7. Schema/Label Verification\nVerify that the required columns are present and compute the binary labels based on `satd_affliction`."))
    cells.append(create_cell("code", """def verify_schema(df, name):
    required_cols = ['project_name', 'comment_content', 'comment_preceding_code', 'comment_succeeding_code', 'satd_affliction']
    for col in required_cols:
        assert col in df.columns, f"Missing {col} in {name}"
    
    df['label'] = df['satd_affliction'].apply(get_binary_label)
    satd_count = sum(df['label'] == 1)
    nonsatd_count = sum(df['label'] == 0)
    print(f"{name} Verified | SATD: {satd_count} | NON-SATD: {nonsatd_count}")
    return df

df_train = verify_schema(df_train, "Train")
df_val = verify_schema(df_val, "Validation")
df_test = verify_schema(df_test, "Test")
"""))

    # 8. Tokenization
    cells.append(create_cell("markdown", "# 8. Tokenization\nInitialize the CodeBERT tokenizer and prepare the PyTorch Datasets using the existing robust truncation algorithm (max_length=512)."))
    cells.append(create_cell("code", """tokenizer = get_tokenizer()

# Using the SATDDataset class from our package
# We temporarily patch the CSV paths in the dataset class instances to point to the Colab Drive paths
class ColabSATDDataset(SATDDataset):
    def __init__(self, df, tokenizer):
        self.df = df
        self.tokenizer = tokenizer

train_dataset = ColabSATDDataset(df_train, tokenizer)
val_dataset = ColabSATDDataset(df_val, tokenizer)
test_dataset = ColabSATDDataset(df_test, tokenizer)

print("Tokenization setup complete.")
"""))

    # 9. CodeBERT Model Setup
    cells.append(create_cell("markdown", "# 9. CodeBERT Model Setup\nInitialize `microsoft/codebert-base` for binary sequence classification."))
    cells.append(create_cell("code", """model = RobertaForSequenceClassification.from_pretrained("microsoft/codebert-base", num_labels=2)
"""))

    # 10. Training
    cells.append(create_cell("markdown", "# 10. Training\nDefine hyperparameters, evaluation metrics, and run HuggingFace Trainer."))
    cells.append(create_cell("code", """def compute_metrics(pred):
    labels = pred.label_ids
    preds = pred.predictions.argmax(-1)
    probs = torch.nn.functional.softmax(torch.tensor(pred.predictions), dim=-1)[:, 1].numpy()
    
    precision, recall, f1, _ = precision_recall_fscore_support(labels, preds, average='binary')
    acc = accuracy_score(labels, preds)
    roc_auc = roc_auc_score(labels, probs)
    return {
        'accuracy': acc,
        'f1': f1,
        'precision': precision,
        'recall': recall,
        'roc_auc': roc_auc
    }

training_args = TrainingArguments(
    output_dir=f"{OUTPUT_DIR}/checkpoints",
    evaluation_strategy="epoch",
    save_strategy="epoch",
    learning_rate=2e-5,
    per_device_train_batch_size=8,
    per_device_eval_batch_size=8,
    gradient_accumulation_steps=4,
    num_train_epochs=5,
    weight_decay=0.01,
    load_best_model_at_end=True,
    metric_for_best_model="f1",
    fp16=torch.cuda.is_available(),
    seed=42,
    logging_dir=f"{OUTPUT_DIR}/logs",
    logging_steps=100
)

trainer = Trainer(
    model=model,
    args=training_args,
    train_dataset=train_dataset,
    eval_dataset=val_dataset,
    compute_metrics=compute_metrics,
    callbacks=[EarlyStoppingCallback(early_stopping_patience=2)]
)

print("Starting Training...")
start_time = time.time()
train_result = trainer.train()
training_time = time.time() - start_time
print(f"Training completed in {training_time/60:.2f} minutes.")
"""))

    # 11. Best Model Selection
    cells.append(create_cell("markdown", "# 11. Best Model Selection\nThe HuggingFace Trainer automatically loads the best model based on validation F1 at the end of training."))
    cells.append(create_cell("code", """print(f"Best model validation F1 loaded.")
# We will save it shortly
"""))

    # 12. Test Evaluation
    cells.append(create_cell("markdown", "# 12. Test Evaluation\nEvaluate the *best* model exactly ONCE on the official `test.csv` split."))
    cells.append(create_cell("code", """print("Running Final Evaluation on Test Set...")
test_results = trainer.evaluate(test_dataset)

# Get raw predictions for Confusion Matrix and detailed report
predictions = trainer.predict(test_dataset)
test_preds = predictions.predictions.argmax(-1)
test_labels = predictions.label_ids

cm = confusion_matrix(test_labels, test_preds)
tn, fp, fn, tp = cm.ravel()

test_acc = test_results['eval_accuracy']
test_prec = test_results['eval_precision']
test_rec = test_results['eval_recall']
test_f1 = test_results['eval_f1']
test_roc = test_results['eval_roc_auc']

print(f"Test Accuracy: {test_acc:.4f}")
print(f"Test Precision: {test_prec:.4f}")
print(f"Test Recall: {test_rec:.4f}")
print(f"Test F1: {test_f1:.4f}")
print(f"Test ROC-AUC: {test_roc:.4f}")
print(f"Confusion Matrix: TP={tp}, TN={tn}, FP={fp}, FN={fn}")
"""))

    # 13. Save Outputs
    cells.append(create_cell("markdown", "# 13. Save Outputs\nSave the best model, tokenizer, and metrics to Google Drive."))
    cells.append(create_cell("code", """import json
os.makedirs(f"{OUTPUT_DIR}/best_model", exist_ok=True)

trainer.save_model(f"{OUTPUT_DIR}/best_model")
tokenizer.save_pretrained(f"{OUTPUT_DIR}/best_model")

metrics_dict = {
    "test_accuracy": test_acc,
    "test_precision": test_prec,
    "test_recall": test_rec,
    "test_f1": test_f1,
    "test_roc_auc": test_roc,
    "confusion_matrix": {"TP": int(tp), "TN": int(tn), "FP": int(fp), "FN": int(fn)},
    "training_time_minutes": training_time / 60
}

with open(f"{OUTPUT_DIR}/final_test_metrics.json", "w") as f:
    json.dump(metrics_dict, f, indent=4)
    
pd.DataFrame(cm, index=['Actual 0', 'Actual 1'], columns=['Predicted 0', 'Predicted 1']).to_csv(f"{OUTPUT_DIR}/confusion_matrix.csv")

print(f"All outputs saved to {OUTPUT_DIR}")
"""))

    # 14. Final Report
    cells.append(create_cell("markdown", "# 14. Final Report\nGenerate the markdown report explicitly requested."))
    cells.append(create_cell("code", """report = f\"\"\"# PENTACET 30K CodeBERT Final Training Report

## 1. Dataset
Official PENTACET Controlled 30K Dataset

## 2. Train/Validation/Test Sizes
- Train: {len(df_train)}
- Validation: {len(df_val)}
- Test: {len(df_test)}

## 3. Label Definition
- SATD (1) = Non-empty `satd_affliction`
- NON-SATD (0) = Empty `satd_affliction`

## 4. Model
`microsoft/codebert-base`

## 5. Input Structure
`COMMENT: <comment> PRECEDING_CODE: <preceding_code> SUCCEEDING_CODE: <succeeding_code>`

## 6. Tokenization/Truncation
- Max length: 512
- Comment strictly prioritized. Remaining budget split 50/50 between preceding (bottom-truncated) and succeeding (top-truncated) code.

## 7. Training Hyperparameters
- Learning Rate: 2e-5
- Batch Size: 8 (Gradient Accumulation: 4) -> Effective: 32
- Epochs: 5
- Seed: 42
- Early Stopping: Patience 2 based on Validation F1
- FP16: {'Yes' if torch.cuda.is_available() else 'No'}

## 8. Epoch-by-Epoch Validation Metrics
(Refer to the Trainer logs in `{OUTPUT_DIR}/logs`)

## 9. Best Epoch
(Model automatically reverted to the checkpoint with highest Validation F1)

## 10. Final Test Metrics
- Accuracy: {test_acc:.4f}
- Precision: {test_prec:.4f}
- Recall: {test_rec:.4f}
- F1-Score: {test_f1:.4f}

## 11. Confusion Matrix
(See `confusion_matrix.csv`)

## 12. TP/TN/FP/FN
- True Positives (TP): {tp}
- True Negatives (TN): {tn}
- False Positives (FP): {fp}
- False Negatives (FN): {fn}

## 13. ROC-AUC
- ROC-AUC: {test_roc:.4f}

## 14. Training Duration
- {training_time / 60:.2f} minutes

## 15. GPU Information
- GPU: {torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU'}

## 16. Saved Model Location
- `{OUTPUT_DIR}/best_model`
\"\"\"

report_path = f"{PROJECT_DRIVE_ROOT}/ml-service/reports/codebert_30k_final_training_report.md"
with open(report_path, "w", encoding='utf-8') as f:
    f.write(report)

print("="*40)
print("FULL 30K CODEBERT TRAINING COMPLETED")
print("="*40)
print(f"Best validation F1 loaded.")
print(f"Test Accuracy: {test_acc:.4f}")
print(f"Test Precision: {test_prec:.4f}")
print(f"Test Recall: {test_rec:.4f}")
print(f"Test F1: {test_f1:.4f}")
print(f"Test ROC-AUC: {test_roc:.4f}")
print(f"Confusion Matrix: TP={tp}, TN={tn}, FP={fp}, FN={fn}")
print(f"Best model path: {OUTPUT_DIR}/best_model")
"""))

    notebook = {
        "metadata": {
            "kernelspec": {
                "display_name": "Python 3",
                "language": "python",
                "name": "python3"
            },
            "language_info": {
                "codemirror_mode": {"name": "ipython", "version": 3},
                "file_extension": ".py",
                "mimetype": "text/x-python",
                "name": "python",
                "nbconvert_exporter": "python",
                "pygments_lexer": "ipython3",
                "version": "3.10.12"
            }
        },
        "nbformat": 4,
        "nbformat_minor": 4,
        "cells": cells
    }

    with open(NOTEBOOK_PATH, 'w', encoding='utf-8') as f:
        json.dump(notebook, f, indent=1)
        
if __name__ == "__main__":
    build_notebook()
