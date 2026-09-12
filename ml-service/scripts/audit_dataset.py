import csv
import sys
import os
import random
import hashlib

# Add app path for LeakageFilter
sys.path.append(r"D:\8th semester\SPL3\ml-service")
from app.data_pipeline.leakage_filter import ProjectLeakageFilter

CSV_FILE = r"D:\8th semester\SPL3\data\pentacet\technical_debt_train.csv"
BLOCKLIST_FILE = r"D:\8th semester\SPL3\ml-service\data\m62_project_blocklist.json"
REPORT_FILE = r"D:\8th semester\SPL3\data\pentacet\pentacet_audit_report.md"

def get_hash(val):
    return hashlib.md5(val.encode('utf-8', errors='ignore')).digest()

def audit():
    maxInt = sys.maxsize
    while True:
        try:
            csv.field_size_limit(maxInt)
            break
        except OverflowError:
            maxInt = int(maxInt/10)
    
    leakage_filter = ProjectLeakageFilter(BLOCKLIST_FILE)
    
    total_rows = 0
    unique_projects = set()
    unique_comments_hash = set()
    unique_combo_hash = set()
    exact_row_hash = set()
    
    satd_count = 0
    non_satd_count = 0
    missing_comment = 0
    missing_context = 0
    m62_leakage = 0
    java_count = 0
    
    exact_duplicates = 0
    comment_duplicates = 0
    combo_duplicates = 0
    
    label_dist = {}
    feature_dist = {}
    
    sum_comment_len = 0
    sum_context_len = 0
    min_context = float('inf')
    max_context = 0
    
    empty_context = 0
    non_empty_context = 0
    
    samples = []
    
    print("Starting iterative audit of the CSV...")
    with open(CSV_FILE, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for i, row in enumerate(reader):
            total_rows += 1
            if total_rows % 500000 == 0:
                print(f"Processed {total_rows} rows...")
            
            pname = row.get("project_name", "")
            purl = row.get("project_url", "")
            lang = row.get("language", "")
            comment = row.get("comment", "")
            context = row.get("surrounding_code", "")
            label = row.get("satd_label", "")
            feature = row.get("satd_feature", "")
            
            # Leakage Check
            l_stat, _ = leakage_filter.check_record(purl)
            if l_stat in ["EXCLUDE", "FLAGGED"]:
                m62_leakage += 1
                
            if lang.lower() == "java":
                java_count += 1
                
            if not comment.strip():
                missing_comment += 1
                
            if not context.strip():
                missing_context += 1
                
            unique_projects.add(purl)
            
            # Hashing for memory
            c_hash = get_hash(comment)
            comb_hash = get_hash(comment + context)
            row_str = "".join([v for k,v in row.items()])
            row_hash = get_hash(row_str)
            
            if c_hash in unique_comments_hash:
                comment_duplicates += 1
            else:
                unique_comments_hash.add(c_hash)
                
            if comb_hash in unique_combo_hash:
                combo_duplicates += 1
            else:
                unique_combo_hash.add(comb_hash)
                
            if row_hash in exact_row_hash:
                exact_duplicates += 1
            else:
                exact_row_hash.add(row_hash)
                
            # Labels
            if label.strip():
                satd_count += 1
            else:
                non_satd_count += 1
                
            label_dist[label] = label_dist.get(label, 0) + 1
            feature_dist[feature] = feature_dist.get(feature, 0) + 1
            
            # Lengths
            clen = len(comment)
            ctxlen = len(context)
            sum_comment_len += clen
            sum_context_len += ctxlen
            if ctxlen < min_context: min_context = ctxlen
            if ctxlen > max_context: max_context = ctxlen
            
            if not context.strip():
                empty_context += 1
            else:
                non_empty_context += 1
                
            # Reservoir Sampling
            if i < 20:
                samples.append(row)
            else:
                r = random.randint(0, i)
                if r < 20:
                    samples[r] = row
                    
    # Generate Report
    with open(REPORT_FILE, 'w', encoding='utf-8') as f:
        f.write("# PENTACET Extraction Audit Report\n\n")
        f.write("## 1. Verify extraction correctness\n")
        f.write(f"- Total rows extracted: {total_rows}\n")
        f.write(f"- Unique projects: {len(unique_projects)}\n")
        f.write(f"- Unique comments: {len(unique_comments_hash)}\n")
        f.write(f"- Unique comment + context combinations: {len(unique_combo_hash)}\n")
        f.write(f"- SATD records (non-empty label): {satd_count}\n")
        f.write(f"- NON-SATD records (empty label): {non_satd_count}\n")
        f.write(f"- Records with missing comment: {missing_comment}\n")
        f.write(f"- Records with missing surrounding_code: {missing_context}\n")
        f.write(f"- Records removed by M62 leakage filter (post-extraction): {m62_leakage} (M62 leakage remaining = {m62_leakage})\n")
        f.write(f"- Java records: {java_count}\n")
        
        f.write("\n## 2. Duplicate analysis\n")
        f.write(f"- Exact duplicate rows: {exact_duplicates}\n")
        f.write(f"- Duplicate comment text: {comment_duplicates}\n")
        f.write(f"- Duplicate comment + surrounding_code: {combo_duplicates}\n")
        f.write("Note: Duplicates are highly likely due to identical boilerplate headers (e.g. Apache licenses), auto-generated code, or copy-pasted utility functions across different files/projects.\n")
        
        f.write("\n## 3. Label analysis\n")
        for k, v in sorted(label_dist.items(), key=lambda x: x[1], reverse=True):
            f.write(f"- Label '{k}': {v} ({(v/total_rows)*100:.2f}%)\n")
        for k, v in sorted(feature_dist.items(), key=lambda x: x[1], reverse=True)[:20]:
            f.write(f"- Feature '{k}': {v} ({(v/total_rows)*100:.2f}%)\n")
            
        f.write("\n## 4. Context quality\n")
        f.write(f"- Avg comment length: {sum_comment_len/max(1, total_rows):.2f} chars\n")
        f.write(f"- Avg context length: {sum_context_len/max(1, total_rows):.2f} chars\n")
        f.write(f"- Min/Max context length: {min_context} / {max_context}\n")
        f.write(f"- Empty context: {(empty_context/total_rows)*100:.2f}%\n")
        
        f.write("\n## 5. Samples (10 shown)\n")
        for s in samples[:10]:
            f.write(f"\n### Sample\n")
            f.write(f"**Project:** {s.get('project_name')} ({s.get('project_url')})\n")
            f.write(f"**File:** {s.get('filename')} (Line: {s.get('line_number')})\n")
            f.write(f"**Label:** {s.get('satd_label')} | **Feature:** {s.get('satd_feature')}\n")
            f.write("```java\n// COMMENT:\n")
            f.write(str(s.get('comment', '')))
            f.write("\n\n// CONTEXT:\n")
            ctx = str(s.get('surrounding_code', ''))
            f.write(ctx[:500] + ("\n...(truncated)" if len(ctx) > 500 else ""))
            f.write("\n```\n")

    print(f"Audit complete. Report saved to {REPORT_FILE}")

if __name__ == "__main__":
    audit()
