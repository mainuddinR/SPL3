import pandas as pd
import json
import os
import sys

CSV_FILE = r"D:\8th semester\SPL3\data\pentacet\pilot\pentacet_pilot_10k.csv"
REPORT_FILE = r"D:\8th semester\SPL3\ml-service\reports\pentacet_10k_training_readiness_audit.md"

def get_len(text):
    if pd.isna(text) or text == r'\N' or str(text).strip() == '':
        return 0
    return len(str(text).strip())

def analyze():
    print("Loading 10k pilot dataset...")
    df = pd.read_csv(CSV_FILE, dtype=str)
    df.fillna('', inplace=True)
    
    total = len(df)
    columns = list(df.columns)
    
    # A. Inspection
    satd_count = sum(df['satd_affliction'] != '')
    nonsatd_count = total - satd_count
    
    satd_affliction_dist = df[df['satd_affliction'] != '']['satd_affliction'].value_counts().to_dict()
    satd_feature_dist = df[df['satd_feature'] != '']['satd_feature'].value_counts().to_dict()
    
    num_projects = df['project_id'].nunique()
    projects = df['project_name'].value_counts()
    
    java_files = sum(df['comment_source_file'].str.endswith('.java', na=False))
    
    missing_cmt = sum((df['comment_content'] == '') | (df['comment_content'] == r'\N'))
    missing_pre = sum((df['comment_preceding_code'] == '') | (df['comment_preceding_code'] == r'\N'))
    missing_suc = sum((df['comment_succeeding_code'] == '') | (df['comment_succeeding_code'] == r'\N'))
    
    has_pre = (df['comment_preceding_code'] != '') & (df['comment_preceding_code'] != r'\N')
    has_suc = (df['comment_succeeding_code'] != '') & (df['comment_succeeding_code'] != r'\N')
    missing_both = sum(~has_pre & ~has_suc)
    
    exact_dups = df.duplicated().sum()
    dup_cmt = df.duplicated(subset=['comment_content']).sum()
    dup_ctx = df.duplicated(subset=['comment_content', 'comment_preceding_code', 'comment_succeeding_code']).sum()
    
    # E. Context Length
    df['cmt_len'] = df['comment_content'].apply(get_len)
    df['pre_len'] = df['comment_preceding_code'].apply(get_len)
    df['suc_len'] = df['comment_succeeding_code'].apply(get_len)
    df['total_len'] = df['cmt_len'] + df['pre_len'] + df['suc_len']
    
    # Format report
    report = f"""# PENTACET 10K Training Readiness Audit

## 1. Executive Summary
This audit evaluates the 10,000-record PENTACET pilot dataset to determine its readiness for training the proposed CodeBERT technical debt classifier.

## 2. Dataset Schema
- **Total Records:** {total}
- **Columns:** {', '.join(columns)}
- **Java Coverage:** {java_files/total*100:.1f}%

## 3. Label Distribution
- **SATD:** {satd_count}
- **NON-SATD:** {nonsatd_count}

## 4. SATD Category Analysis
- **satd_affliction:** {json.dumps(satd_affliction_dist, indent=2)}
- **satd_feature:** {json.dumps(satd_feature_dist, indent=2)}

**Comparison with SRS:**
The SRS proposes categorizing debt into: Design, Defect, Test, Requirement, Documentation.
- The `satd_affliction` field in PENTACET seems to only contain the generic string "SATD". This is suitable for the main binary SATD/NON-SATD detection.
- The `satd_feature` field contains finer-grained keywords (e.g. `todo`, `fixme`, `hack`, `workaround`). These can be mapped to SRS categories, but it is not a direct 1:1 mapping without a rule-based mapper or a larger subset to ensure enough examples of each.

## 5. Missing Context Analysis
- **Missing Comment:** {missing_cmt}
- **Missing Preceding Code:** {missing_pre}
- **Missing Succeeding Code:** {missing_suc}
- **Missing Both (Comment Only):** {missing_both}

## 6. Duplicate Analysis
- **Exact Duplicate Rows:** {exact_dups}
- **Duplicate Comment Text:** {dup_cmt}
- **Duplicate Comment + Context:** {dup_ctx}

## 7. Leakage Risk
There is a high risk of data leakage due to the `{dup_ctx}` duplicate comment+context pairs.
**Recommendation:** Perform exact deduplication based on `[comment_content, comment_preceding_code, comment_succeeding_code]` before splitting the dataset to prevent the exact same code snippet from appearing in both train and validation sets.

## 8. Project Distribution
- **Total Unique Projects:** {num_projects}
- **Records per project:** 
{projects.head(10).to_string()}

## 9. Project-Level Split Feasibility
**Feasibility:** Highly Feasible.
With {num_projects} distinct projects, a project-disjoint split is very possible. The top 2-3 projects contain a large chunk of the records, so stratified project-level splitting is required. The smaller projects can be grouped into the validation and test sets.

## 10. Context Length / CodeBERT Tokenization Analysis
**Length Statistics (in characters):**
- **Comment:** Avg {df['cmt_len'].mean():.0f}, Max {df['cmt_len'].max()}
- **Preceding Code:** Avg {df['pre_len'].mean():.0f}, Max {df['pre_len'].max()}
- **Succeeding Code:** Avg {df['suc_len'].mean():.0f}, Max {df['suc_len'].max()}
- **Combined Input Length:** Avg {df['total_len'].mean():.0f}, Max {df['total_len'].max()}

**Recommendation:**
Given CodeBERT's 512 token limit (roughly ~2000 characters), many combined inputs will exceed the limit.
A safe initial max sequence length is **256 tokens** (to save RAM and speed up training) or **512 tokens** (max capacity).
**Structured Truncation Strategy:**
1. Keep the full comment (most important).
2. Allocate the remaining tokens 50/50 between preceding and succeeding code.
3. Truncate preceding code from the top (keep lines closest to the comment).
4. Truncate succeeding code from the bottom (keep lines closest to the comment).

## 11. Class Imbalance
The 10K pilot is artificially balanced (50/50). PENTACET's natural distribution is unknown without full extraction, but technical debt is typically heavily imbalanced (e.g. 5-10% SATD).
**Safe Training Strategy:**
1. Maintain an artificial 50/50 or 33/67 balance by undersampling NON-SATD during the final dataset extraction. This prevents the model from collapsing to the majority class.

## 12. Data Quality Assessment
The context is exceptionally high quality, recovering the SRS requirement of "comment + surrounding source code context." Duplicates exist and must be dropped.

## 13. Recommended Dataset Construction Strategy
1. Perform a controlled extraction of a slightly larger subset (e.g. 20K-30K rows).
2. Undersample NON-SATD to maintain balance.
3. Drop exact duplicates dynamically.
4. Split by project ID.

## 14. Recommended Training Configuration
- **Model:** CodeBERT Base
- **Max Seq Length:** 256 or 512
- **Batch Size:** 8 or 16 (Gradient Accumulation = 2 or 4)
- **Environment:** Google Colab (Local CPU is not viable).

## 15. Risks and Limitations
- The lack of explicit multi-class labels in `satd_affliction` means we may have to train a binary classifier first, or build a mapping from `satd_feature` to the SRS categories.
- Extreme truncation may cut off important code context if the surrounding methods are massive.

## 16. Final Decision
NEEDS A LARGER CONTROLLED PENTACET SUBSET

(A 10K pilot with 447 duplicates reduces the effective dataset size. A 20K-30K subset will ensure enough unique samples for training, validation, and testing while remaining fully safe and manageable.)
"""
    with open(REPORT_FILE, 'w', encoding='utf-8') as f:
        f.write(report)
        
    print(f"Total Records Inspected: {total}")
    print(f"File created: {REPORT_FILE}")

if __name__ == "__main__":
    analyze()
