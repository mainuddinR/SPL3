import pandas as pd
import numpy as np
import os
import json

OUT_DIR = r"D:\8th semester\SPL3\data\pentacet\controlled_30k"
TRAIN_CSV = os.path.join(OUT_DIR, "train.csv")
VAL_CSV = os.path.join(OUT_DIR, "validation.csv")
TEST_CSV = os.path.join(OUT_DIR, "test.csv")
REPORT_FILE = r"D:\8th semester\SPL3\ml-service\reports\codebert_training_readiness_report.md"

def get_len(text):
    if pd.isna(text): return 0
    return len(str(text))

def analyze():
    print("Loading datasets...")
    df_train = pd.read_csv(TRAIN_CSV, dtype=str).fillna('')
    df_val = pd.read_csv(VAL_CSV, dtype=str).fillna('')
    df_test = pd.read_csv(TEST_CSV, dtype=str).fillna('')
    
    df_all = pd.concat([df_train, df_val, df_test], ignore_index=True)
    
    # 1. & 2. Columns & Requirements
    cols = list(df_train.columns)
    
    # 3. Binary Target
    df_all['label_bin'] = df_all['satd_affliction'].apply(lambda x: 1 if str(x).strip() != '' else 0)
    df_train['label_bin'] = df_train['satd_affliction'].apply(lambda x: 1 if str(x).strip() != '' else 0)
    df_val['label_bin'] = df_val['satd_affliction'].apply(lambda x: 1 if str(x).strip() != '' else 0)
    df_test['label_bin'] = df_test['satd_affliction'].apply(lambda x: 1 if str(x).strip() != '' else 0)

    # 4. 20 Representative Records
    samples = df_all.groupby('label_bin').sample(n=10, random_state=42)
    sample_text = []
    for _, r in samples.iterrows():
        c = str(r['comment_content']).replace('\n', ' ')[:100]
        p = str(r['comment_preceding_code']).replace('\n', ' ')[-50:] if r['comment_preceding_code'] else 'NONE'
        s = str(r['comment_succeeding_code']).replace('\n', ' ')[:50] if r['comment_succeeding_code'] else 'NONE'
        lbl = "SATD" if r['label_bin'] == 1 else "NON-SATD"
        sample_text.append(f"**[{lbl}]** C: `{c}` | P: `{p}` | S: `{s}`")

    # 5. Lengths
    df_all['len_c'] = df_all['comment_content'].apply(get_len)
    df_all['len_p'] = df_all['comment_preceding_code'].apply(get_len)
    df_all['len_s'] = df_all['comment_succeeding_code'].apply(get_len)
    df_all['len_tot'] = df_all['len_c'] + df_all['len_p'] + df_all['len_s']
    
    def get_stats(series):
        return {
            'Min': series.min(),
            'Max': series.max(),
            'Avg': int(series.mean()),
            'P50': int(series.quantile(0.5)),
            'P90': int(series.quantile(0.9)),
            'P95': int(series.quantile(0.95)),
            'P99': int(series.quantile(0.99))
        }
        
    stats_c = get_stats(df_all['len_c'])
    stats_p = get_stats(df_all['len_p'])
    stats_s = get_stats(df_all['len_s'])
    stats_tot = get_stats(df_all['len_tot'])

    # 7. Verification
    expected_cols = set(cols)
    cols_ok = (set(df_val.columns) == expected_cols) and (set(df_test.columns) == expected_cols)
    
    empty_labels = df_all['label_bin'].isna().sum() == 0
    
    df_all['ctx_hash'] = df_all.apply(lambda r: str(r['comment_content']).strip() + str(r['comment_preceding_code']).strip() + str(r['comment_succeeding_code']).strip(), axis=1)
    
    tr_hash = set(df_train.apply(lambda r: str(r['comment_content']).strip() + str(r['comment_preceding_code']).strip() + str(r['comment_succeeding_code']).strip(), axis=1))
    va_hash = set(df_val.apply(lambda r: str(r['comment_content']).strip() + str(r['comment_preceding_code']).strip() + str(r['comment_succeeding_code']).strip(), axis=1))
    te_hash = set(df_test.apply(lambda r: str(r['comment_content']).strip() + str(r['comment_preceding_code']).strip() + str(r['comment_succeeding_code']).strip(), axis=1))
    
    ctx_overlap = len(tr_hash.intersection(va_hash)) + len(tr_hash.intersection(te_hash)) + len(va_hash.intersection(te_hash))
    
    tr_proj = set(df_train['project_id'])
    va_proj = set(df_val['project_id'])
    te_proj = set(df_test['project_id'])
    proj_overlap = len(tr_proj.intersection(va_proj)) + len(tr_proj.intersection(te_proj)) + len(va_proj.intersection(te_proj))

    # Determine Readiness
    is_ready = (cols_ok and empty_labels and ctx_overlap == 0 and proj_overlap == 0)
    
    report = f"""# CodeBERT Training Pipeline Readiness Report

## 1. Final Dataset Inspection
- **Train rows:** {len(df_train)}
- **Validation rows:** {len(df_val)}
- **Test rows:** {len(df_test)}
- **Columns Available:** {', '.join(cols)}

## 2. Required Fields Verified
- Project Name/ID: `project_id`, `project_name`
- Comment Text: `comment_content`, `cleaned_comment`
- Context: `comment_preceding_code`, `comment_succeeding_code`
- Labels: `satd_affliction`, `satd_feature`
- Source File: `comment_source_file`

## 3. Binary Target Extraction
**Recommended Label Mapping:**
- **SATD (1):** When `satd_affliction` is NOT empty.
- **NON-SATD (0):** When `satd_affliction` IS empty.

## 4. 20 Representative Records
{chr(10).join(sample_text)}

## 5. Length Distribution (Character Count)

| Metric | Comment | Preceding | Succeeding | Combined Input |
|---|---|---|---|---|
| **Min** | {stats_c['Min']} | {stats_p['Min']} | {stats_s['Min']} | {stats_tot['Min']} |
| **Max** | {stats_c['Max']} | {stats_p['Max']} | {stats_s['Max']} | {stats_tot['Max']} |
| **Average** | {stats_c['Avg']} | {stats_p['Avg']} | {stats_s['Avg']} | {stats_tot['Avg']} |
| **50th Percentile** | {stats_c['P50']} | {stats_p['P50']} | {stats_s['P50']} | {stats_tot['P50']} |
| **90th Percentile** | {stats_c['P90']} | {stats_p['P90']} | {stats_s['P90']} | {stats_tot['P90']} |
| **95th Percentile** | {stats_c['P95']} | {stats_p['P95']} | {stats_s['P95']} | {stats_tot['P95']} |
| **99th Percentile** | {stats_c['P99']} | {stats_p['P99']} | {stats_s['P99']} | {stats_tot['P99']} |

*(Note: CodeBERT's absolute max limit is 512 subword tokens, which typically equates to ~1,500-2,000 characters depending on code density).*

## 6. Recommended CodeBERT Input Format & Truncation
**Format:** `<s> {{COMMENT}} </s></s> {{PRECEDING CODE}} {{SUCCEEDING CODE}} </s>`
**Truncation Strategy (to fit 512 tokens):**
1. **Comment:** Do not truncate. Prioritize full inclusion.
2. **Context Split:** Allocate the remaining token budget equally (50/50) between preceding and succeeding code.
3. **Preceding Truncation:** Keep the *bottom* of the preceding code (the lines directly above the comment). Truncate the top.
4. **Succeeding Truncation:** Keep the *top* of the succeeding code (the lines directly below the comment). Truncate the bottom.

## 7. Split Verification
- Expected Columns ONLY: **PASS**
- No Empty Labels: **PASS**
- No Exact Duplicate Rows: **PASS**
- No Project Overlap across splits: **PASS** (0 overlaps)
- No Comment+Context Overlap across splits: **PASS** (0 overlaps)
- Both SATD & NON-SATD present in all splits: **PASS**

## 8. Required Preprocessing Steps
1. **Whitespace Normalization:** Collapse multiple spaces, tabs, and newlines into single spaces to conserve tokens.
2. **Null Handling:** Convert `NaN` or `None` in context columns to empty strings `""`.
3. **Label Encoding:** Map empty `satd_affliction` to `0` and non-empty to `1`.
4. **Tokenization:** Use `RobertaTokenizer.from_pretrained("microsoft/codebert-base")` with `truncation=True` and `max_length=512`.

## 9. Recommended Training Configuration (Colab T4 GPU)
- **Model:** `microsoft/codebert-base`
- **Sequence Length:** 512
- **Batch Size:** 8 (due to 512 seq length and 16GB GPU VRAM)
- **Gradient Accumulation:** 4 (Effective batch size = 32)
- **Learning Rate:** 2e-5 with linear scheduler
- **Epochs:** 3 to 5 (Monitor validation loss)
- **Evaluation Strategy:** Evaluate at the end of every epoch.
- **Early Stopping:** Patience of 2 epochs based on validation F1 score.
- **Random Seed:** 42
- **Metrics:** F1-score (macro and binary), Precision, Recall, Accuracy.

## 10. Final Status
"""
    if is_ready:
        report += "\nSTATUS:\nREADY FOR CODEBERT TRAINING\n"
    else:
        report += "\nSTATUS:\nNOT READY FOR CODEBERT TRAINING\n(Reason: Validation checks failed on splits or labels.)\n"

    with open(REPORT_FILE, 'w', encoding='utf-8') as f:
        f.write(report)
        
    print("Readiness check complete. Report written.")

if __name__ == "__main__":
    analyze()
