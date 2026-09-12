import pandas as pd
import numpy as np
import os
import shutil
import psutil
import subprocess

CSV_PATH = r"D:\8th semester\SPL3\technical_debt_dataset.csv"
REPORT_PATH = r"D:\8th semester\SPL3\ml-service\reports\62k_pretraining_audit.md"

def get_gpu_info():
    try:
        output = subprocess.check_output(['nvidia-smi', '--query-gpu=name,memory.total', '--format=csv,noheader'], encoding='utf-8')
        return output.strip()
    except:
        return "No NVIDIA GPU found or nvidia-smi not available."

def audit():
    if not os.path.exists(CSV_PATH):
        with open(REPORT_PATH, 'w') as f:
            f.write("ERROR: technical_debt_dataset.csv not found!\nNOT READY FOR CODEBERT TRAINING")
        return

    # Read CSV
    df = pd.read_csv(CSV_PATH)
    
    # Basic Stats
    total_rows = len(df)
    columns = list(df.columns)
    dtypes = df.dtypes.to_dict()
    missing_values = df.isnull().sum().to_dict()
    
    # Label Column & Counts
    label_cols = [c for c in columns if 'label' in c.lower() or 'satd' in c.lower() or 'class' in c.lower()]
    label_col = 'classification' if 'classification' in columns else (label_cols[0] if label_cols else None)
    
    label_counts = {}
    satd_count = 0
    nonsatd_count = 0
    if label_col:
        label_counts = df[label_col].value_counts().to_dict()
        # Guess SATD vs NON-SATD based on values
        for k, v in label_counts.items():
            k_str = str(k).upper()
            if k_str in ['WITHOUT_CLASSIFICATION', 'NON-SATD', '0', 'FALSE']:
                nonsatd_count += v
            else:
                satd_count += v

    # Duplicates
    exact_dupes = df.duplicated().sum()
    
    comment_col = 'commenttext' if 'commenttext' in columns else 'comment'
    context_col = 'method' if 'method' in columns else 'code'
    
    dup_comment_context = 0
    if comment_col in columns and context_col in columns:
        dup_comment_context = df.duplicated(subset=[comment_col, context_col]).sum()

    # Empty/Invalid & Long Samples
    empty_samples = 0
    long_samples = 0
    if comment_col in columns and context_col in columns:
        empty_mask = df[comment_col].fillna('').str.strip().eq('') | df[context_col].fillna('').str.strip().eq('')
        empty_samples = empty_mask.sum()
        
        # very rough estimate for length: CodeBERT max is usually 512 tokens. Let's say 2000 chars is long.
        long_mask = df[context_col].fillna('').str.len() > 2000
        long_samples = long_mask.sum()

    # Project Info
    project_col = 'projectname' if 'projectname' in columns else ('project' if 'project' in columns else None)
    unique_projects = []
    if project_col:
        unique_projects = df[project_col].nunique()
        
    split_col = None
    for c in columns:
        if 'split' in c.lower() or 'set' in c.lower() or 'partition' in c.lower():
            split_col = c
            break
            
    project_split_leakage = False
    if project_col and split_col:
        # Check if any project is in multiple splits
        cross_tab = pd.crosstab(df[project_col], df[split_col])
        leakage = (cross_tab > 0).sum(axis=1) > 1
        project_split_leakage = leakage.any()

    # Hardware
    ram_gb = psutil.virtual_memory().total / (1024**3)
    free_ram_gb = psutil.virtual_memory().available / (1024**3)
    disk_free_gb = shutil.disk_usage("D:\\").free / (1024**3)
    gpu_info = get_gpu_info()
    has_gpu = "No NVIDIA" not in gpu_info
    
    # Recommendations
    rec_split = "Random split (Stratified 80/10/10) if no project column exists."
    if project_col:
        rec_split = f"Project-level stratified split on '{project_col}' (e.g., 80% projects for train, 10% for val, 10% for test) to prevent data leakage."

    # Readiness
    issues = []
    if missing_values.get(comment_col, 0) > 0 or missing_values.get(context_col, 0) > 0:
        issues.append("Missing values in comment or context columns.")
    if exact_dupes > 0:
        issues.append(f"{exact_dupes} exact duplicate rows found.")
    if empty_samples > 0:
        issues.append(f"{empty_samples} empty/invalid samples found.")
    if project_split_leakage:
        issues.append("Existing train/test split has project leakage.")
        
    readiness = "READY FOR CODEBERT TRAINING" if not issues else "NOT READY FOR CODEBERT TRAINING"

    # Report Formatting
    report = f"""# Pre-Training Audit: 62K Technical Debt Dataset

## 1. Dataset Structure
- **Total Rows:** {total_rows}
- **Columns:** {', '.join(columns)}
- **Data Types:**
"""
    for c, t in dtypes.items():
        report += f"  - `{c}`: {t}\n"

    report += f"""
## 2. Data Quality
- **Missing Values:**
"""
    for c, m in missing_values.items():
        if m > 0:
            report += f"  - `{c}`: {m}\n"
    if sum(missing_values.values()) == 0:
        report += "  - None\n"
        
    report += f"""
- **Exact Duplicate Rows:** {exact_dupes}
- **Duplicate (Comment + Context) Rows:** {dup_comment_context}
- **Empty/Invalid Samples:** {empty_samples}
- **Extremely Long Samples (>2000 chars):** {long_samples} (May exceed CodeBERT 512 token limit and require truncation)

## 3. Labels & Balancing
- **Label Column:** `{label_col}`
- **Exact Label Values:** {label_counts}
- **SATD:** {satd_count} ({satd_count/total_rows*100:.1f}%)
- **NON-SATD:** {nonsatd_count} ({nonsatd_count/total_rows*100:.1f}%)

## 4. Input Mapping
- **Recommended CodeBERT Input Format:** `[COMMENT] {{row['{comment_col}']}} [CODE] {{row['{context_col}']}}`
- **Recommended Label:** `{label_col}`

## 5. Splitting Strategy
- **Project Column Exists:** {'Yes (`' + project_col + '`)' if project_col else 'No'}
- **Unique Projects:** {unique_projects if project_col else 'N/A'}
- **Existing Split Column:** {split_col if split_col else 'None'}
- **Leakage in Existing Split:** {'Yes' if project_split_leakage else 'No'}
- **Recommended Strategy:** {rec_split}

## 6. Hardware & Training Recommendations
- **Total RAM:** {ram_gb:.1f} GB (Available: {free_ram_gb:.1f} GB)
- **D: Free Space:** {disk_free_gb:.1f} GB
- **GPU Info:** {gpu_info}

**Recommended Training Settings (Based on Hardware):**
- **Hardware feasibility:** {'GPU is available, training is highly feasible.' if has_gpu else 'CPU ONLY. Training will be extremely slow. Consider smaller batch sizes or Colab/Kaggle.'}
- **Max Sequence Length:** 512 (Truncate context if necessary)
- **Batch Size:** 8 to 16 {'(Keep small for GPU memory)' if has_gpu else '(CPU limitation)'}
- **Gradient Accumulation:** 2 to 4 (To simulate batch size of 32)
- **Number of Epochs:** 3 to 5
- **Learning Rate:** 2e-5 to 5e-5
- **Storage:** Safe to store models on `D:\\` ({disk_free_gb:.1f} GB free). CodeBERT checkpoints are ~500MB each.

## 7. Conclusion

{readiness}

"""
    if issues:
        report += "### Required Fixes Before Training:\n"
        for i in issues:
            report += f"- {i}\n"

    with open(REPORT_PATH, 'w') as f:
        f.write(report)

if __name__ == "__main__":
    audit()
