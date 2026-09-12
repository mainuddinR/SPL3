import os
import sys
import subprocess
import csv
import time
import shutil
import psutil

DUMP_FILE = r"D:\8th semester\SPL3\data\pentacet\pentacet_clean_and_load_dump.sql"
PG_RESTORE = r"D:\8th semester\SPL3\tools\pgsql\bin\pg_restore.exe"

PILOT_DIR = r"D:\8th semester\SPL3\data\pentacet\pilot"
os.makedirs(PILOT_DIR, exist_ok=True)
OUT_CSV = os.path.join(PILOT_DIR, "pentacet_pilot_10k.csv")

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

def run_pilot():
    start_time = time.time()
    check_safety()
    
    # 1. Load Projects
    print("Loading project_main...")
    project_map = {}
    for _, parts in stream_table("project_main"):
        if len(parts) >= 11:
            pid = parts[0]
            pname = parts[1]
            plang = parts[2]
            project_map[pid] = {'name': pname, 'language': plang}
            
    print(f"Loaded {len(project_map)} projects.")

    # 2. Get columns
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

    # 3. Stream comment_attr
    satd_records = []
    nonsatd_records = []
    
    total_scanned = 0
    max_len = max([c_cid, c_pid, c_content, c_cleaned, c_line, c_file, c_prec, c_succ, c_satd, c_feat])
    
    print("Streaming comment_attr...")
    for row_idx, parts in stream_table("comment_attr"):
        total_scanned += 1
        if len(parts) <= max_len: continue
        
        pid = parts[c_pid]
        pinfo = project_map.get(pid, {'name': 'Unknown', 'language': 'Unknown'})
        
        lbl = parts[c_satd].strip()
        is_satd = False if (lbl == r'\N' or not lbl) else True
        
        row_dict = {
            "comment_id": parts[c_cid],
            "project_id": pid,
            "project_name": pinfo['name'],
            "project_language": pinfo['language'],
            "comment_content": parts[c_content],
            "cleaned_comment": parts[c_cleaned],
            "comment_line_no": parts[c_line],
            "comment_source_file": parts[c_file],
            "comment_preceding_code": parts[c_prec],
            "comment_succeeding_code": parts[c_succ],
            "satd_affliction": lbl if is_satd else "",
            "satd_feature": parts[c_feat].strip() if parts[c_feat].strip() != r'\N' else ""
        }
        
        if is_satd and len(satd_records) < 5000:
            satd_records.append(row_dict)
        elif not is_satd and len(nonsatd_records) < 5000:
            nonsatd_records.append(row_dict)
            
        if len(satd_records) == 5000 and len(nonsatd_records) == 5000:
            break
            
        if total_scanned > 500000: # safety limit so we don't stream 15M just to find 5000 SATD
            break
            
    all_records = satd_records + nonsatd_records
    print(f"Extracted {len(all_records)} records (SATD: {len(satd_records)}, NON-SATD: {len(nonsatd_records)})")
    
    # Write to CSV
    headers = ["comment_id", "project_id", "project_name", "project_language", "comment_content", "cleaned_comment", "comment_line_no", "comment_source_file", "comment_preceding_code", "comment_succeeding_code", "satd_affliction", "satd_feature"]
    with open(OUT_CSV, 'w', encoding='utf-8', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=headers)
        writer.writeheader()
        writer.writerows(all_records)
        
    process = psutil.Process(os.getpid())
    peak_ram = process.memory_info().rss / (1024**2)
    elapsed = time.time() - start_time
    out_size = os.path.getsize(OUT_CSV) / (1024**2)
    
    print(f"RAM: {peak_ram:.2f} MB, Time: {elapsed:.2f}s, OutSize: {out_size:.2f} MB")

if __name__ == "__main__":
    run_pilot()
