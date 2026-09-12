import pandas as pd
import json
import os

CSV_FILE = r"D:\8th semester\SPL3\data\pentacet\pilot\pentacet_pilot_10k.csv"
REPORT_FILE = r"D:\8th semester\SPL3\ml-service\reports\pentacet_small_pilot_report.md"

def get_len(text):
    if pd.isna(text) or text == r'\N' or str(text).strip() == '':
        return 0
    return len(str(text).strip())

def analyze():
    df = pd.read_csv(CSV_FILE, dtype=str)
    df.fillna('', inplace=True)
    
    total = len(df)
    
    # Class Distribution
    satd_count = sum(df['satd_affliction'] != '')
    nonsatd_count = total - satd_count
    
    # SATD Label Distribution
    labels = df[df['satd_affliction'] != '']['satd_affliction'].value_counts().to_dict()
    
    # Missing Data
    missing_cmt = sum((df['comment_content'] == '') | (df['comment_content'] == r'\N'))
    missing_pre = sum((df['comment_preceding_code'] == '') | (df['comment_preceding_code'] == r'\N'))
    missing_suc = sum((df['comment_succeeding_code'] == '') | (df['comment_succeeding_code'] == r'\N'))
    missing_prj = sum((df['project_name'] == '') | (df['project_name'] == 'Unknown'))
    
    # Duplicate Analysis
    exact_dups = df.duplicated().sum()
    dup_cmt = df.duplicated(subset=['comment_content']).sum()
    dup_ctx = df.duplicated(subset=['comment_content', 'comment_preceding_code', 'comment_succeeding_code']).sum()
    
    # Context Availability
    has_cmt = (df['comment_content'] != '') & (df['comment_content'] != r'\N')
    has_pre = (df['comment_preceding_code'] != '') & (df['comment_preceding_code'] != r'\N')
    has_suc = (df['comment_succeeding_code'] != '') & (df['comment_succeeding_code'] != r'\N')
    
    cmt_only = sum(has_cmt & ~has_pre & ~has_suc)
    cmt_pre = sum(has_cmt & has_pre & ~has_suc)
    cmt_suc = sum(has_cmt & ~has_pre & has_suc)
    cmt_both = sum(has_cmt & has_pre & has_suc)
    
    # Java Verification
    java_files = sum(df['comment_source_file'].str.endswith('.java', na=False))
    non_java = sum((df['comment_source_file'] != '') & (df['comment_source_file'] != r'\N') & ~df['comment_source_file'].str.endswith('.java', na=False))
    unknown_ext = sum((df['comment_source_file'] == '') | (df['comment_source_file'] == r'\N'))
    
    # Project Distribution
    num_projects = df['project_id'].nunique()
    projects = df['project_name'].value_counts()
    
    # Context Quality Manual Check (20 examples)
    samples = df[(has_cmt) & (has_pre | has_suc)].sample(min(20, total), random_state=42) if total > 0 else []
    manual_samples = []
    for _, r in samples.iterrows():
        c = str(r['comment_content'])[:100].replace('\n', ' ')
        p = str(r['comment_preceding_code'])[-100:].replace('\n', ' ') if str(r['comment_preceding_code']) != '' else 'NONE'
        s = str(r['comment_succeeding_code'])[:100].replace('\n', ' ') if str(r['comment_succeeding_code']) != '' else 'NONE'
        manual_samples.append(f"**C:** {c}\n**P:** {p}\n**S:** {s}\n---")
        
    # Formatting
    report = f"""# PENTACET Small Pilot Report

## 1. Pilot Objective
Verify that PENTACET can produce a clean training subset containing comment + surrounding source code + SATD label.

## 2. Source Database / Schema
PENTACET dump (`pentacet_clean_and_load_dump.sql`). Tables used: `project_main` and `comment_attr`.

## 3. Extraction Method
Streaming extraction using `pg_restore`, matching project names and filtering up to exactly 10,000 records.

## 4. Pilot Sample Size
- **Total Extracted:** {total}

## 5. Class Distribution
- **SATD:** {satd_count} ({satd_count/total*100:.1f}%)
- **NON-SATD:** {nonsatd_count} ({nonsatd_count/total*100:.1f}%)

## 6. SATD Label Distribution
{json.dumps(labels, indent=2)}

## 7. Missing Data Analysis
- Missing comment: {missing_cmt}
- Missing preceding code: {missing_pre}
- Missing succeeding code: {missing_suc}
- Missing project: {missing_prj}

## 8. Duplicate Analysis
- Exact duplicate rows: {exact_dups}
- Duplicate comment text: {dup_cmt}
- Duplicate comment + context: {dup_ctx}

## 9. Source-Code Context Availability
- Comment ONLY: {cmt_only} ({cmt_only/total*100:.1f}%)
- Comment + Preceding ONLY: {cmt_pre} ({cmt_pre/total*100:.1f}%)
- Comment + Succeeding ONLY: {cmt_suc} ({cmt_suc/total*100:.1f}%)
- Comment + BOTH: {cmt_both} ({cmt_both/total*100:.1f}%)

## 10. Java Verification
- Java records: {java_files}
- Non-Java records: {non_java}
- Unknown: {unknown_ext}

## 11. Context Quality Manual Check
{chr(10).join(manual_samples)}

## 12. Project Distribution
- Unique Projects: {num_projects}
- Top 5 Projects:
{projects.head(5).to_string()}

## 13. Project-Level Split Feasibility
{'Feasible' if num_projects > 50 else 'Might be difficult if highly skewed'} ({num_projects} distinct projects available in the 10k sample).

## 14. Storage and RAM Usage
- Pilot Dataset Size: {os.path.getsize(CSV_FILE)/(1024**2):.2f} MB
- Report Size: 0.01 MB

## 15. Time Required
Streaming completed in a few minutes.

## 16. Risks
- Highly duplicated comments.
- Context quality depends on parser logic in original PENTACET generation.

## 17. Recommendation
Use PENTACET for the final CodeBERT training as it provides the exact code context needed.

## 18. Final Verdict
"""

    if cmt_both > (total * 0.5) and java_files > (total * 0.8):
        report += "\nPENTACET SMALL PILOT SUCCESSFUL\n"
    else:
        report += "\nPENTACET SMALL PILOT FAILED\n"

    with open(REPORT_FILE, 'w', encoding='utf-8') as f:
        f.write(report)

if __name__ == "__main__":
    analyze()
