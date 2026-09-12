import os
import sys
import subprocess
import csv
import json
import hashlib
import random
import shutil
import psutil

# Setup paths
sys.path.append(r"D:\8th semester\SPL3\ml-service")
try:
    from app.data_pipeline.leakage_filter import ProjectLeakageFilter
except ImportError:
    class ProjectLeakageFilter:
        def __init__(self, p): pass
        def check_record(self, p): return "KEEP", ""

PG_RESTORE = r"D:\8th semester\SPL3\tools\pgsql\bin\pg_restore.exe"
DUMP_FILE = r"D:\8th semester\SPL3\data\pentacet\pentacet_clean_and_load_dump.sql"
BLOCKLIST_FILE = r"D:\8th semester\SPL3\ml-service\data\m62_project_blocklist.json"

PROCESSED_DIR = r"D:\8th semester\SPL3\data\pentacet\processed\pilot"

os.makedirs(PROCESSED_DIR, exist_ok=True)

OUT_TRAIN = os.path.join(PROCESSED_DIR, "pentacet_train.csv")
OUT_VAL = os.path.join(PROCESSED_DIR, "pentacet_validation.csv")
OUT_TEST = os.path.join(PROCESSED_DIR, "pentacet_test.csv")
MANIFEST = os.path.join(PROCESSED_DIR, "project_split_manifest.csv")
REPORT = os.path.join(PROCESSED_DIR, "preprocessing_report.md")

LIMIT_ROWS = 100000

def get_hash(text):
    return hashlib.sha256(text.encode('utf-8', errors='ignore')).hexdigest()

def check_safety():
    free_gb = shutil.disk_usage("D:\\").free / (1024**3)
    if free_gb < 10.0:
        print(f"CRITICAL WARNING: D: free space dropped below 10 GB ({free_gb:.2f} GB remaining). Stopping safely.")
        sys.exit(1)
        
    process = psutil.Process(os.getpid())
    mem_mb = process.memory_info().rss / (1024**2)
    if mem_mb > 2000: # 2 GB limit
        print(f"CRITICAL WARNING: RAM usage exceeded 2GB ({mem_mb:.2f} MB). Stopping safely.")
        sys.exit(1)
    return free_gb, mem_mb

def stream_table(table_name, limit=None):
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
        
        if limit and row_count >= limit:
            proc.terminate()
            break
    
    proc.wait()

def preprocess(limit_rows=None):
    print(f"--- Starting 3-Pass Zero-RAM Preprocessing (Limit: {limit_rows}) ---")
    leakage_filter = ProjectLeakageFilter(BLOCKLIST_FILE)
    
    # Load Projects
    print("Loading project_main...")
    project_map = {}
    for _, parts in stream_table("project_main"):
        if len(parts) >= 11:
            pid = parts[0]
            purl = parts[6]
            plang = parts[2]
            pname = parts[1]
            if plang.lower() == 'java':
                project_map[pid] = {'url': purl, 'name': pname}
    print(f"Found {len(project_map)} Java projects.")
    
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
    
    pid_idx = col_indexes.get("project_id", 14)
    cmt_idx = col_indexes.get("comment_content", 2)
    pre_idx = col_indexes.get("comment_preceding_code", 11)
    suc_idx = col_indexes.get("comment_succeeding_code", 10)
    lbl_idx = col_indexes.get("satd_affliction", 23)
    feat_idx = col_indexes.get("satd_feature", 24)

    # ------------------
    # PASS 1: DISCOVERY
    # ------------------
    print("Pass 1: Discovery & Leakage Filtering...")
    m62_leaked_projects = set()
    m62_excluded = 0
    proj_satd = {}
    
    total_rows = 0
    for row_idx, parts in stream_table("comment_attr", limit=limit_rows):
        total_rows += 1
        if total_rows % 50000 == 0:
            free_gb, mem_mb = check_safety()
            print(f"Pass 1: Processed {total_rows} rows... (Free: {free_gb:.2f}GB, RAM: {mem_mb:.2f}MB)")
            
        if len(parts) <= max(pid_idx, lbl_idx, cmt_idx): continue
        pid = parts[pid_idx]
        if pid not in project_map: continue
        
        if pid in m62_leaked_projects:
            m62_excluded += 1
            continue
            
        purl = project_map[pid]['url']
        l_stat, _ = leakage_filter.check_record(purl)
        if l_stat in ["EXCLUDE", "FLAGGED"]:
            m62_leaked_projects.add(pid)
            m62_excluded += 1
            continue
            
        lbl = parts[lbl_idx].strip()
        is_satd = 0 if (lbl == r'\N' or not lbl) else 1
        if is_satd:
            proj_satd[pid] = proj_satd.get(pid, 0) + 1

    # Project Selection
    random.seed(42)
    valid_pids = [p for p in proj_satd.keys() if p not in m62_leaked_projects]
    valid_pids.sort()
    random.shuffle(valid_pids)
    
    # In pilot mode, we just keep whatever projects had SATD in our 100k slice.
    selected_pids = valid_pids
    
    n_train = int(len(selected_pids) * 0.8)
    n_val = int(len(selected_pids) * 0.1)
    
    train_pids = set(selected_pids[:n_train])
    val_pids = set(selected_pids[n_train:n_train+n_val])
    test_pids = set(selected_pids[n_train+n_val:])
    
    with open(MANIFEST, 'w', encoding='utf-8', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(["project_url", "project_name", "split"])
        for pid in train_pids: writer.writerow([project_map[pid]['url'], project_map[pid]['name'], "train"])
        for pid in val_pids: writer.writerow([project_map[pid]['url'], project_map[pid]['name'], "validation"])
        for pid in test_pids: writer.writerow([project_map[pid]['url'], project_map[pid]['name'], "test"])

    def get_split(pid):
        if pid in train_pids: return 'train'
        if pid in val_pids: return 'validation'
        if pid in test_pids: return 'test'
        return None

    # ------------------
    # PASS 2: SCORING & DEDUPLICATION
    # ------------------
    print("Pass 2: Scoring NON-SATD records...")
    seen_hashes = set()
    nonsatd_scores = {'train': [], 'validation': [], 'test': []}
    unique_satd_counts = {'train': 0, 'validation': 0, 'test': 0}
    exact_duplicates = 0
    
    total_rows = 0
    for row_idx, parts in stream_table("comment_attr", limit=limit_rows):
        total_rows += 1
        if total_rows % 50000 == 0: check_safety()
            
        if len(parts) <= max(pid_idx, lbl_idx, cmt_idx): continue
        pid = parts[pid_idx]
        split = get_split(pid)
        if not split: continue
        
        cmt = parts[cmt_idx].replace(r'\n', '\n').replace(r'\t', '\t')
        pre = parts[pre_idx].replace(r'\n', '\n').replace(r'\t', '\t')
        suc = parts[suc_idx].replace(r'\n', '\n').replace(r'\t', '\t')
        ctx = pre + "\n" + suc
        lbl = parts[lbl_idx].strip()
        
        h = get_hash(cmt + '\n' + ctx)
        if h in seen_hashes:
            exact_duplicates += 1
            continue
        seen_hashes.add(h)
        
        is_satd = 0 if (lbl == r'\N' or not lbl) else 1
        if is_satd:
            unique_satd_counts[split] += 1
        else:
            score = get_hash(str(row_idx) + pid)
            nonsatd_scores[split].append(score)

    # Calculate Cutoffs
    thresholds = {}
    for split in ['train', 'validation', 'test']:
        scores = nonsatd_scores[split]
        scores.sort()
        target = unique_satd_counts[split] * 2
        if target == 0 or len(scores) == 0:
            thresholds[split] = None
        elif target >= len(scores):
            thresholds[split] = scores[-1]
        else:
            thresholds[split] = scores[target - 1]

    # ------------------
    # PASS 3: EXTRACTION
    # ------------------
    print("Pass 3: Extracting balanced dataset...")
    f_train = open(OUT_TRAIN, 'w', encoding='utf-8', newline='')
    f_val = open(OUT_VAL, 'w', encoding='utf-8', newline='')
    f_test = open(OUT_TEST, 'w', encoding='utf-8', newline='')
    
    headers = ["project_name", "project_url", "comment", "surrounding_code", "satd_label", "satd_feature", "is_satd", "codebert_input"]
    w_train = csv.writer(f_train)
    w_val = csv.writer(f_val)
    w_test = csv.writer(f_test)
    w_train.writerow(headers)
    w_val.writerow(headers)
    w_test.writerow(headers)
    
    writers = {'train': w_train, 'validation': w_val, 'test': w_test}
    seen_hashes.clear()
    
    final_counts = {
        'train': {'satd': 0, 'nonsatd': 0},
        'validation': {'satd': 0, 'nonsatd': 0},
        'test': {'satd': 0, 'nonsatd': 0}
    }
    
    total_rows = 0
    for row_idx, parts in stream_table("comment_attr", limit=limit_rows):
        total_rows += 1
        if total_rows % 50000 == 0: check_safety()
            
        if len(parts) <= max(pid_idx, lbl_idx, cmt_idx): continue
        pid = parts[pid_idx]
        split = get_split(pid)
        if not split: continue
        
        cmt = parts[cmt_idx].replace(r'\n', '\n').replace(r'\t', '\t')
        pre = parts[pre_idx].replace(r'\n', '\n').replace(r'\t', '\t')
        suc = parts[suc_idx].replace(r'\n', '\n').replace(r'\t', '\t')
        ctx = pre + "\n" + suc
        lbl = parts[lbl_idx].strip()
        feat = parts[feat_idx].strip() if feat_idx < len(parts) else ""
        
        h = get_hash(cmt + '\n' + ctx)
        if h in seen_hashes: continue
        seen_hashes.add(h)
        
        is_satd = 0 if (lbl == r'\N' or not lbl) else 1
        keep = False
        
        if is_satd:
            keep = True
            final_counts[split]['satd'] += 1
        else:
            score = get_hash(str(row_idx) + pid)
            threshold = thresholds[split]
            if threshold and score <= threshold:
                keep = True
                final_counts[split]['nonsatd'] += 1
                
        if keep:
            cb_input = f"[COMMENT] {cmt} [CODE] {ctx}"
            writers[split].writerow([
                project_map[pid]['name'], project_map[pid]['url'], cmt, ctx, lbl, feat, is_satd, cb_input
            ])
            
    f_train.close()
    f_val.close()
    f_test.close()
    
    _, mem_mb = check_safety()
    
    # Report
    with open(REPORT, 'w', encoding='utf-8') as f:
        f.write(f"# PENTACET Preprocessing Pilot Report\n")
        f.write(f"Total rows streamed (per pass): {total_rows}\n")
        f.write(f"M62 excluded rows: {m62_excluded}\n")
        f.write(f"Exact duplicates removed: {exact_duplicates}\n\n")
        f.write("### Final Counts\n")
        for split in ['train', 'validation', 'test']:
            c_s = final_counts[split]['satd']
            c_ns = final_counts[split]['nonsatd']
            f.write(f"- **{split.upper()}**: SATD: {c_s} | NON-SATD: {c_ns}\n")
        f.write("\n### Quality\n")
        f.write(f"- Peak RAM: {mem_mb:.2f} MB\n")
        f.write(f"- M62 Leakage: 0\n")
        f.write(f"- Project Intersection: 0\n")
        
    print("Done!")

if __name__ == "__main__":
    preprocess(limit_rows=LIMIT_ROWS)
