import os
import sys
import subprocess
import csv
import time
import shutil
import psutil
import hashlib
from collections import defaultdict

DUMP_FILE = r"D:\8th semester\SPL3\data\pentacet\pentacet_clean_and_load_dump.sql"
PG_RESTORE = r"D:\8th semester\SPL3\tools\pgsql\bin\pg_restore.exe"

OUT_DIR = r"D:\8th semester\SPL3\data\pentacet\controlled_20k"
os.makedirs(OUT_DIR, exist_ok=True)
OUT_CSV = os.path.join(OUT_DIR, "pentacet_controlled_20k.csv")
TRAIN_CSV = os.path.join(OUT_DIR, "train.csv")
VAL_CSV = os.path.join(OUT_DIR, "validation.csv")
TEST_CSV = os.path.join(OUT_DIR, "test.csv")

def check_safety():
    free_gb = shutil.disk_usage("D:\\").free / (1024**3)
    if free_gb < 5.0:
        print(f"CRITICAL WARNING: D: free space dropped below 5 GB ({free_gb:.2f} GB remaining). Stopping safely.")
        sys.exit(1)

def stream_table(table_name):
    cmd = [PG_RESTORE, "-a", "-t", table_name, "-f", "-", DUMP_FILE]
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, text=True, encoding='utf-8', errors='ignore')
    
    in_copy = False
    row_count = 0
    for line in proc.stdout:
        if not in_copy:
            if line.startswith(f"COPY public.{table_name} "):
                in_copy = True
            continue
            
        if line.startswith(r"\."):
            break
            
        parts = line.strip('\n').split('\t')
        yield row_count, parts
        row_count += 1
    proc.wait()

def run_extraction():
    start_time = time.time()
    check_safety()
    
    print("Loading project_main...")
    project_map = {}
    for _, parts in stream_table("project_main"):
        if len(parts) >= 11:
            project_map[parts[0]] = {'name': parts[1], 'language': parts[2]}

    # Get column indexes
    col_indexes = {}
    cmd_header = [PG_RESTORE, "-s", "-t", "comment_attr", "-f", "-", DUMP_FILE]
    proc_header = subprocess.Popen(cmd_header, stdout=subprocess.PIPE, text=True, encoding='utf-8', errors='ignore')
    for line in proc_header.stdout:
        if line.strip().startswith("CREATE TABLE public.comment_attr"):
            idx = 0
            for col_line in proc_header.stdout:
                col_line = col_line.strip()
                if col_line.startswith(")"): break
                col_name = col_line.split()[0].strip('"')
                col_indexes[col_name] = idx
                idx += 1
            break
    proc_header.wait()
    
    c_cid = col_indexes.get("comment_id", 0)
    c_pid = col_indexes.get("project_id", 14)
    c_content = col_indexes.get("comment_content", 2)
    c_cleaned = col_indexes.get("cleaned_comment", 21)
    c_line = col_indexes.get("comment_line_no", 4)
    c_file = col_indexes.get("comment_source_file", 19)
    c_prec = col_indexes.get("comment_preceding_code", 11)
    c_succ = col_indexes.get("comment_succeeding_code", 10)
    c_satd = col_indexes.get("satd_affliction", 23)
    c_feat = col_indexes.get("satd_feature", 24)

    satd_records = []
    nonsatd_records = []
    seen_hashes = set()
    total_scanned = 0
    max_len = max([c_cid, c_pid, c_content, c_cleaned, c_line, c_file, c_prec, c_succ, c_satd, c_feat])
    
    print("Streaming comment_attr...")
    for row_idx, parts in stream_table("comment_attr"):
        total_scanned += 1
        if len(parts) <= max_len: continue
        
        # Filters: Java only
        c_source = parts[c_file]
        if not c_source.endswith('.java'): continue
            
        # Filters: Non-empty comment
        content = parts[c_content]
        if not content or content == r'\N' or content.strip() == '': continue
            
        prec = parts[c_prec] if parts[c_prec] != r'\N' else ''
        succ = parts[c_succ] if parts[c_succ] != r'\N' else ''
        
        # Deduplication Hash
        # hash(comment + prec + succ)
        ctx_hash = hashlib.md5((content.strip() + prec.strip() + succ.strip()).encode('utf-8')).hexdigest()
        if ctx_hash in seen_hashes:
            continue
            
        seen_hashes.add(ctx_hash)
        
        pid = parts[c_pid]
        pinfo = project_map.get(pid, {'name': 'Unknown', 'language': 'Unknown'})
        lbl = parts[c_satd].strip()
        is_satd = False if (lbl == r'\N' or not lbl) else True
        
        row_dict = {
            "comment_id": parts[c_cid],
            "project_id": pid,
            "project_name": pinfo['name'],
            "project_language": pinfo['language'],
            "comment_content": content,
            "cleaned_comment": parts[c_cleaned],
            "comment_line_no": parts[c_line],
            "comment_source_file": c_source,
            "comment_preceding_code": prec,
            "comment_succeeding_code": succ,
            "satd_affliction": lbl if is_satd else "",
            "satd_feature": parts[c_feat].strip() if parts[c_feat].strip() != r'\N' else ""
        }
        
        if is_satd and len(satd_records) < 10000:
            satd_records.append(row_dict)
        elif not is_satd and len(nonsatd_records) < 10000:
            nonsatd_records.append(row_dict)
            
        if len(satd_records) >= 10000 and len(nonsatd_records) >= 10000:
            break
            
        if total_scanned > 2000000: # safety limit
            break

    all_records = satd_records + nonsatd_records
    print(f"Extracted {len(all_records)} unique records (SATD: {len(satd_records)}, NON-SATD: {len(nonsatd_records)})")
    
    # Save main 20k
    headers = ["comment_id", "project_id", "project_name", "project_language", "comment_content", "cleaned_comment", "comment_line_no", "comment_source_file", "comment_preceding_code", "comment_succeeding_code", "satd_affliction", "satd_feature"]
    with open(OUT_CSV, 'w', encoding='utf-8', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=headers)
        writer.writeheader()
        writer.writerows(all_records)

    # Project-level splitting
    # Group by project
    project_counts = defaultdict(lambda: {'total': 0, 'satd': 0})
    project_records = defaultdict(list)
    for r in all_records:
        pid = r['project_id']
        project_records[pid].append(r)
        project_counts[pid]['total'] += 1
        if r['satd_affliction']:
            project_counts[pid]['satd'] += 1
            
    # Sort projects by total count descending
    sorted_projects = sorted(project_counts.items(), key=lambda x: x[1]['total'], reverse=True)
    
    train_records = []
    val_records = []
    test_records = []
    
    target_train = int(len(all_records) * 0.8)
    target_val = int(len(all_records) * 0.1)
    
    train_satd, train_total = 0, 0
    val_satd, val_total = 0, 0
    test_satd, test_total = 0, 0
    
    for pid, counts in sorted_projects:
        recs = project_records[pid]
        # Distribute based on capacities and try to keep SATD representation
        if train_total < target_train:
            train_records.extend(recs)
            train_total += counts['total']
            train_satd += counts['satd']
        elif val_total < target_val:
            val_records.extend(recs)
            val_total += counts['total']
            val_satd += counts['satd']
        else:
            test_records.extend(recs)
            test_total += counts['total']
            test_satd += counts['satd']

    # Write splits
    def write_split(path, records):
        with open(path, 'w', encoding='utf-8', newline='') as f:
            writer = csv.DictWriter(f, fieldnames=headers)
            writer.writeheader()
            writer.writerows(records)
            
    write_split(TRAIN_CSV, train_records)
    write_split(VAL_CSV, val_records)
    write_split(TEST_CSV, test_records)

    process = psutil.Process(os.getpid())
    peak_ram = process.memory_info().rss / (1024**2)
    elapsed = time.time() - start_time
    
    print(f"Splits created: Train={len(train_records)}, Val={len(val_records)}, Test={len(test_records)}")
    print(f"RAM: {peak_ram:.2f} MB, Time: {elapsed:.2f}s")

if __name__ == "__main__":
    run_extraction()
