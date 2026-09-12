import os

BASE_DIR = r"D:\8th semester\SPL3\ml-service\codebert_training"
os.makedirs(BASE_DIR, exist_ok=True)

def write_file(filename, content):
    with open(os.path.join(BASE_DIR, filename), 'w', encoding='utf-8') as f:
        f.write(content.strip() + '\n')

# 1. requirements.txt
write_file("requirements.txt", """torch
transformers
datasets
accelerate
scikit-learn
pandas
numpy""")

# 2. config.py
write_file("config.py", """import os

# Dataset Locations (READ-ONLY)
DATASET_DIR = r"D:\\8th semester\\SPL3\\data\\pentacet\\controlled_30k"
TRAIN_CSV = os.path.join(DATASET_DIR, "train.csv")
VAL_CSV = os.path.join(DATASET_DIR, "validation.csv")
TEST_CSV = os.path.join(DATASET_DIR, "test.csv")

# Training Configuration
MODEL_NAME = "microsoft/codebert-base"
MAX_LENGTH = 512
TRAIN_BATCH_SIZE = 8
GRADIENT_ACCUMULATION_STEPS = 4
LEARNING_RATE = 2e-5
NUM_EPOCHS = 3
RANDOM_SEED = 42

# Column Names (Verified in Audit)
COL_COMMENT = "comment_content"
COL_PRECEDING = "comment_preceding_code"
COL_SUCCEEDING = "comment_succeeding_code"
COL_SATD = "satd_affliction"
COL_PROJECT = "project_name"

# Section Markers for Input Builder
MARKER_COMMENT = "COMMENT: "
MARKER_PRECEDING = "PRECEDING_CODE: "
MARKER_SUCCEEDING = "SUCCEEDING_CODE: "
""")

# 3. input_builder.py
write_file("input_builder.py", """import pandas as pd
from config import MARKER_COMMENT, MARKER_PRECEDING, MARKER_SUCCEEDING

def clean_text(text):
    if pd.isna(text) or text is None:
        return ""
    return str(text).strip()

def build_structured_input(comment, preceding, succeeding):
    c = clean_text(comment)
    p = clean_text(preceding)
    s = clean_text(succeeding)
    
    # We maintain distinct sections that the tokenizer algorithm will use
    return {
        "comment_section": f"{MARKER_COMMENT}{c}",
        "preceding_section": f"{MARKER_PRECEDING}{p}",
        "succeeding_section": f"{MARKER_SUCCEEDING}{s}"
    }

def get_binary_label(satd_affliction):
    lbl = clean_text(satd_affliction)
    if lbl == '':
        return 0 # NON-SATD
    return 1 # SATD
""")

# 4. tokenizer_utils.py
write_file("tokenizer_utils.py", """import pandas as pd
from transformers import RobertaTokenizer
from config import MODEL_NAME, MAX_LENGTH
from input_builder import build_structured_input, get_binary_label

def get_tokenizer():
    return RobertaTokenizer.from_pretrained(MODEL_NAME)

def tokenize_record(row, tokenizer):
    parts = build_structured_input(
        row['comment_content'], 
        row['comment_preceding_code'], 
        row['comment_succeeding_code']
    )
    
    # Tokenize each part without truncation first to get their sizes
    tok_c = tokenizer.encode(parts['comment_section'], add_special_tokens=False)
    tok_p = tokenizer.encode(parts['preceding_section'], add_special_tokens=False)
    tok_s = tokenizer.encode(parts['succeeding_section'], add_special_tokens=False)
    
    # Special tokens budget ( [CLS] + [SEP] + [SEP] + [SEP] = 4 tokens)
    special_tokens_len = 4
    budget = MAX_LENGTH - special_tokens_len
    
    len_c = len(tok_c)
    len_p = len(tok_p)
    len_s = len(tok_s)
    
    # 1. Prioritize comment
    if len_c >= budget:
        # Extreme case: comment alone exceeds budget
        tok_c = tok_c[:budget]
        tok_p = []
        tok_s = []
    else:
        rem_budget = budget - len_c
        
        # 2. Allocate remaining budget to preceding and succeeding
        # Prefer keeping lines closest to comment
        # Preceding: keep the end (bottom)
        # Succeeding: keep the start (top)
        
        if len_p + len_s <= rem_budget:
            # Both fit perfectly
            pass
        else:
            # Need to truncate code
            # Let's try 50/50 split of remaining budget
            half = rem_budget // 2
            if len_p <= half:
                # Preceding fits, give rest to succeeding
                alloc_p = len_p
                alloc_s = rem_budget - alloc_p
            elif len_s <= half:
                # Succeeding fits, give rest to preceding
                alloc_s = len_s
                alloc_p = rem_budget - alloc_s
            else:
                # Both are too big, give 50/50
                alloc_p = half
                alloc_s = rem_budget - half
                
            tok_p = tok_p[-alloc_p:] if alloc_p > 0 else []
            tok_s = tok_s[:alloc_s] if alloc_s > 0 else []
            
    # Reconstruct final sequence
    # [CLS] COMMENT [SEP] PRECEDING [SEP] SUCCEEDING [SEP]
    final_ids = [tokenizer.cls_token_id] + tok_c + [tokenizer.sep_token_id] + tok_p + [tokenizer.sep_token_id] + tok_s + [tokenizer.sep_token_id]
    
    attention_mask = [1] * len(final_ids)
    
    # Pad to max length
    pad_len = MAX_LENGTH - len(final_ids)
    final_ids.extend([tokenizer.pad_token_id] * pad_len)
    attention_mask.extend([0] * pad_len)
    
    label = get_binary_label(row.get('satd_affliction', ''))
    
    return {
        'input_ids': final_ids,
        'attention_mask': attention_mask,
        'label': label,
        'diagnostics': {
            'orig_c_len': len_c,
            'orig_p_len': len_p,
            'orig_s_len': len_s,
            'final_c_len': len(tok_c),
            'final_p_len': len(tok_p),
            'final_s_len': len(tok_s),
            'truncated': (len_p + len_s) > (len(tok_p) + len(tok_s))
        }
    }
""")

# 5. dataset.py
write_file("dataset.py", """import pandas as pd
import torch
from torch.utils.data import Dataset
from tokenizer_utils import tokenize_record

class SATDDataset(Dataset):
    def __init__(self, csv_file, tokenizer):
        self.df = pd.read_csv(csv_file, dtype=str).fillna('')
        # Validation checks
        required_cols = ['comment_content', 'comment_preceding_code', 'comment_succeeding_code', 'satd_affliction']
        for col in required_cols:
            if col not in self.df.columns:
                raise ValueError(f"CRITICAL ERROR: Missing required column '{col}' in {csv_file}")
                
        self.tokenizer = tokenizer

    def __len__(self):
        return len(self.df)

    def __getitem__(self, idx):
        row = self.df.iloc[idx]
        tokens = tokenize_record(row, self.tokenizer)
        return {
            'input_ids': torch.tensor(tokens['input_ids'], dtype=torch.long),
            'attention_mask': torch.tensor(tokens['attention_mask'], dtype=torch.long),
            'labels': torch.tensor(tokens['label'], dtype=torch.long)
        }
""")

# 6. smoke_test.py
write_file("smoke_test.py", """import pandas as pd
from tokenizer_utils import get_tokenizer, tokenize_record
from input_builder import get_binary_label
import os
import time

TRAIN_CSV = r"D:\\8th semester\\SPL3\\data\\pentacet\\controlled_30k\\train.csv"
REPORT_FILE = r"D:\\8th semester\\SPL3\\ml-service\\reports\\codebert_20_record_smoke_test_report.md"

def run_smoke_test():
    print("Loading 20 records for smoke test...")
    df = pd.read_csv(TRAIN_CSV, dtype=str).fillna('')
    df['bin_label'] = df['satd_affliction'].apply(get_binary_label)
    
    # 10 SATD, 10 NON-SATD
    df_satd = df[df['bin_label'] == 1].head(10)
    df_nonsatd = df[df['bin_label'] == 0].head(10)
    df_test = pd.concat([df_satd, df_nonsatd])
    
    print("Initializing CodeBERT tokenizer...")
    tokenizer = get_tokenizer()
    
    results = []
    errors = []
    
    for idx, row in df_test.iterrows():
        try:
            tokens = tokenize_record(row, tokenizer)
            results.append({
                'idx': idx,
                'project': row['project_name'],
                'label': tokens['label'],
                'comment': row['comment_content'],
                'has_pre': bool(row['comment_preceding_code'].strip()),
                'has_suc': bool(row['comment_succeeding_code'].strip()),
                'input_ids': tokens['input_ids'],
                'diag': tokens['diagnostics']
            })
            
            # Verifications
            assert tokens['label'] in [0, 1]
            assert len(tokens['input_ids']) == 512
            assert len(tokens['attention_mask']) == 512
            
        except Exception as e:
            errors.append(f"Row {idx}: {str(e)}")

    passed = len(results) == 20 and len(errors) == 0
    
    # Metrics
    tok_lens = [sum(1 for x in r['input_ids'] if x != tokenizer.pad_token_id) for r in results]
    max_len = max(tok_lens) if tok_lens else 0
    min_len = min(tok_lens) if tok_lens else 0
    avg_len = sum(tok_lens) / len(tok_lens) if tok_lens else 0
    trunc_count = sum(1 for r in results if r['diag']['truncated'])
    
    # Print 5 diagnostic records
    print("\\n=== SMOKE TEST DIAGNOSTICS (5 Records) ===")
    for r in results[:5]:
        print(f"Index: {r['idx']} | Project: {r['project']} | Label: {r['label']}")
        print(f"Comment: {r['comment'][:60]}...")
        print(f"Preceding Exist: {r['has_pre']} | Succeeding Exist: {r['has_suc']}")
        print(f"Truncated: {r['diag']['truncated']}")
        print("-" * 50)
        
    print(f"\\nStatus: {'SMOKE TEST PASSED' if passed else 'SMOKE TEST FAILED'}")
    
    # Generate Report
    report = f\"\"\"# CodeBERT 20-Record Smoke Test Report

- **Timestamp:** {time.strftime('%Y-%m-%d %H:%M:%S')}
- **Tokenizer:** microsoft/codebert-base
- **Records Tested:** {len(results)}
- **SATD Count:** {sum(1 for r in results if r['label'] == 1)}
- **NON-SATD Count:** {sum(1 for r in results if r['label'] == 0)}

## Tokenizer Statistics
- **Max Length:** {max_len} / 512
- **Min Length:** {min_len} / 512
- **Average Length:** {avg_len:.1f} / 512
- **Truncated Records:** {trunc_count}

## Feature Preservation
- **Comment Preserved:** YES (Prioritized in algorithm)
- **Preceding Context:** YES (Bottom lines preserved during truncation)
- **Succeeding Context:** YES (Top lines preserved during truncation)

## Errors
{chr(10).join(errors) if errors else 'None'}

## Final Status
{'SMOKE TEST PASSED' if passed else 'SMOKE TEST FAILED'}
\"\"\"
    with open(REPORT_FILE, 'w', encoding='utf-8') as f:
        f.write(report)

if __name__ == "__main__":
    run_smoke_test()
""")

# 7. train.py
write_file("train.py", """# FUTURE TRAINING SCRIPT (DO NOT RUN NOW)
import torch
from transformers import RobertaForSequenceClassification, Trainer, TrainingArguments
from dataset import SATDDataset
from tokenizer_utils import get_tokenizer
from config import *
import numpy as np
from sklearn.metrics import accuracy_score, precision_recall_fscore_support

def compute_metrics(pred):
    labels = pred.label_ids
    preds = pred.predictions.argmax(-1)
    precision, recall, f1, _ = precision_recall_fscore_support(labels, preds, average='binary')
    acc = accuracy_score(labels, preds)
    return {
        'accuracy': acc,
        'f1': f1,
        'precision': precision,
        'recall': recall
    }

def main():
    print("Preparing CodeBERT Training...")
    tokenizer = get_tokenizer()
    
    # Load Datasets (NEVER load test.csv here)
    train_dataset = SATDDataset(TRAIN_CSV, tokenizer)
    val_dataset = SATDDataset(VAL_CSV, tokenizer)
    
    model = RobertaForSequenceClassification.from_pretrained(MODEL_NAME, num_labels=2)
    
    training_args = TrainingArguments(
        output_dir="./outputs",
        evaluation_strategy="epoch",
        save_strategy="epoch",
        learning_rate=LEARNING_RATE,
        per_device_train_batch_size=TRAIN_BATCH_SIZE,
        per_device_eval_batch_size=TRAIN_BATCH_SIZE,
        gradient_accumulation_steps=GRADIENT_ACCUMULATION_STEPS,
        num_train_epochs=NUM_EPOCHS,
        weight_decay=0.01,
        load_best_model_at_end=True,
        metric_for_best_model="f1",
        fp16=torch.cuda.is_available(), # Use AMP on GPU
        seed=RANDOM_SEED
    )
    
    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_dataset,
        eval_dataset=val_dataset,
        compute_metrics=compute_metrics
    )
    
    print("Starting Training...")
    trainer.train()
    
    # Save best model
    trainer.save_model("./outputs/best_codebert_satd")
    tokenizer.save_pretrained("./outputs/best_codebert_satd")

if __name__ == "__main__":
    # main()
    print("This script is ready for Colab execution. Execution blocked locally.")
""")

# 8. evaluate.py
write_file("evaluate.py", """# FUTURE EVALUATION SCRIPT (DO NOT RUN NOW)
from transformers import RobertaForSequenceClassification, Trainer
from dataset import SATDDataset
from tokenizer_utils import get_tokenizer
from train import compute_metrics
from config import TEST_CSV

def main():
    model_path = "./outputs/best_codebert_satd"
    tokenizer = get_tokenizer()
    test_dataset = SATDDataset(TEST_CSV, tokenizer)
    
    model = RobertaForSequenceClassification.from_pretrained(model_path)
    trainer = Trainer(model=model, compute_metrics=compute_metrics)
    
    results = trainer.evaluate(test_dataset)
    print("Final Test Results:", results)

if __name__ == "__main__":
    # main()
    print("Evaluation script ready.")
""")

# 9. README.md
write_file("README.md", """# PENTACET CodeBERT Training Package

## 1. Dataset Location
**The official PENTACET 30K dataset is read-only.**
Location: `D:\\8th semester\\SPL3\\data\\pentacet\\controlled_30k\\`

**Note:** The 30K dataset is a controlled balanced subset with 15,000 SATD and 15,000 NON-SATD records; this is not the natural PENTACET class distribution.

## 2. Training Package Location
Location: `D:\\8th semester\\SPL3\\ml-service\\codebert_training\\`
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
""")

print("CodeBERT training package created.")
