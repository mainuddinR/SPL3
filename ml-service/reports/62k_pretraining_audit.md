# Pre-Training Audit: 62K Technical Debt Dataset

## 1. Dataset Structure
- **Total Rows:** 62275
- **Columns:** projectname, classification, commenttext
- **Data Types:**
  - `projectname`: str
  - `classification`: str
  - `commenttext`: str

## 2. Data Quality
- **Missing Values:**
  - None

- **Exact Duplicate Rows:** 23238
- **Duplicate (Comment + Context) Rows:** 0
- **Empty/Invalid Samples:** 0
- **Extremely Long Samples (>2000 chars):** 0 (May exceed CodeBERT 512 token limit and require truncation)

## 3. Labels & Balancing
- **Label Column:** `classification`
- **Exact Label Values:** {'WITHOUT_CLASSIFICATION': 58204, 'DESIGN': 2703, 'IMPLEMENTATION': 757, 'DEFECT': 472, 'TEST': 85, 'DOCUMENTATION': 54}
- **SATD:** 4071 (6.5%)
- **NON-SATD:** 58204 (93.5%)

## 4. Input Mapping
- **Recommended CodeBERT Input Format:** `[COMMENT] {row['commenttext']} [CODE] {row['code']}`
- **Recommended Label:** `classification`

## 5. Splitting Strategy
- **Project Column Exists:** Yes (`projectname`)
- **Unique Projects:** 10
- **Existing Split Column:** None
- **Leakage in Existing Split:** No
- **Recommended Strategy:** Project-level stratified split on 'projectname' (e.g., 80% projects for train, 10% for val, 10% for test) to prevent data leakage.

## 6. Hardware & Training Recommendations
- **Total RAM:** 15.7 GB (Available: 5.0 GB)
- **D: Free Space:** 29.9 GB
- **GPU Info:** No NVIDIA GPU found or nvidia-smi not available.

**Recommended Training Settings (Based on Hardware):**
- **Hardware feasibility:** CPU ONLY. Training will be extremely slow. Consider smaller batch sizes or Colab/Kaggle.
- **Max Sequence Length:** 512 (Truncate context if necessary)
- **Batch Size:** 8 to 16 (CPU limitation)
- **Gradient Accumulation:** 2 to 4 (To simulate batch size of 32)
- **Number of Epochs:** 3 to 5
- **Learning Rate:** 2e-5 to 5e-5
- **Storage:** Safe to store models on `D:\` (29.9 GB free). CodeBERT checkpoints are ~500MB each.

## 7. Conclusion

NOT READY FOR CODEBERT TRAINING

### Required Fixes Before Training:
- 23238 exact duplicate rows found.
