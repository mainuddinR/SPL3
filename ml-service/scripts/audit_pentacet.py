import pandas as pd
import json
import os
import sys

# Add the app directory to sys.path so we can import leakage_filter
sys.path.append(r"D:\8th semester\SPL3\ml-service")
from app.data_pipeline.leakage_filter import ProjectLeakageFilter

def audit_pentacet():
    file_path = r"D:\8th semester\SPL3\data\pentacet\pentacet_satd_comments.tsv"
    
    print(f"File size: {os.path.getsize(file_path) / (1024*1024):.2f} MB")
    print(f"Reading {file_path}...")
    
    try:
        # Read the TSV
        df = pd.read_csv(file_path, sep='\t', on_bad_lines='skip', low_memory=False)
    except Exception as e:
        print(f"Error reading TSV: {e}")
        return

    print(f"\n--- BASIC SCHEMA ---")
    print(f"Rows: {len(df)}")
    print(f"Columns: {len(df.columns)}")
    print(f"Column Names: {list(df.columns)}")
    print(f"Data Types:\n{df.dtypes}")
    print(f"\nFirst 2 records:\n{df.head(2).to_dict(orient='records')}")

    # Identify project, comment, labels, context
    cols = [c.lower() for c in df.columns]
    
    project_col = next((c for c in df.columns if 'project' in c.lower() or 'repo' in c.lower() or 'url' in c.lower()), None)
    comment_col = next((c for c in df.columns if 'comment' in c.lower() and 'id' not in c.lower() and 'type' not in c.lower()), None)
    label_col = next((c for c in df.columns if 'satd' in c.lower() or 'label' in c.lower() or 'class' in c.lower() or 'category' in c.lower()), None)
    
    pre_ctx_col = next((c for c in df.columns if 'pre' in c.lower() and 'context' in c.lower() or 'code' in c.lower()), None)
    post_ctx_col = next((c for c in df.columns if 'post' in c.lower() or 'succe' in c.lower() and 'context' in c.lower()), None)
    lang_col = next((c for c in df.columns if 'lang' in c.lower()), None)

    print(f"\n--- IDENTIFIED COLUMNS ---")
    print(f"Project Col: {project_col}")
    print(f"Comment Col: {comment_col}")
    print(f"Label Col: {label_col}")
    print(f"Pre-Context Col: {pre_ctx_col}")
    print(f"Post-Context Col: {post_ctx_col}")
    print(f"Language Col: {lang_col}")

    # Context analysis
    print(f"\n--- CONTEXT ANALYSIS ---")
    if pre_ctx_col and post_ctx_col:
        df['combined_context'] = df[pre_ctx_col].fillna('') + "\n" + df[post_ctx_col].fillna('')
        ctx_len = df['combined_context'].str.len()
        print(f"Average Context Size: {ctx_len.mean():.2f}")
        print(f"Min Context Size: {ctx_len.min()}")
        print(f"Max Context Size: {ctx_len.max()}")
        print(f"Missing/Empty Context %: {(ctx_len == 0).mean() * 100:.2f}%")
    else:
        print("Pre/Post Context columns not confidently identified.")

    # Label analysis
    print(f"\n--- LABEL ANALYSIS ---")
    if label_col:
        print(df[label_col].value_counts(dropna=False))
    else:
        print("Label column not found.")

    # Java filter
    print(f"\n--- JAVA FILTER ---")
    if lang_col:
        java_count = (df[lang_col].str.lower() == 'java').sum()
        print(f"Java records: {java_count}")
        print(f"Non-Java/Unknown records: {len(df) - java_count}")
    else:
        print("Language column not found, assuming all are from Java repositories based on PENTACET description.")

    # Duplicates
    print(f"\n--- DUPLICATE ANALYSIS ---")
    print(f"Exact row duplicates: {df.duplicated().sum()}")
    if comment_col:
        print(f"Duplicate comment texts: {df.duplicated(subset=[comment_col]).sum()}")
        if pre_ctx_col and post_ctx_col:
            print(f"Duplicate comment+context: {df.duplicated(subset=[comment_col, pre_ctx_col, post_ctx_col]).sum()}")

    # Leakage Test
    print(f"\n--- LEAKAGE FILTER TEST ---")
    if project_col:
        blocklist_path = r"D:\8th semester\SPL3\ml-service\data\m62_project_blocklist.json"
        lf = ProjectLeakageFilter(blocklist_path)
        for proj in df[project_col].dropna().unique():
            lf.check_record(str(proj))
        print(lf.generate_report())
    else:
        print("Cannot run leakage filter: project column missing.")

if __name__ == "__main__":
    audit_pentacet()
