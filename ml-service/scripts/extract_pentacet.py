import os
import sys
import subprocess
import csv
import json

# Add app path for LeakageFilter
sys.path.append(r"D:\8th semester\SPL3\ml-service")
from app.data_pipeline.leakage_filter import ProjectLeakageFilter

PG_RESTORE = r"D:\8th semester\SPL3\tools\pgsql\bin\pg_restore.exe"
DUMP_FILE = r"D:\8th semester\SPL3\data\pentacet\pentacet_clean_and_load_dump.sql"
BLOCKLIST_FILE = r"D:\8th semester\SPL3\ml-service\data\m62_project_blocklist.json"
OUTPUT_CSV = r"D:\8th semester\SPL3\data\pentacet\technical_debt_train.csv"

def stream_and_extract():
    print("Starting PENTACET extraction pipeline...")
    
    if not os.path.exists(DUMP_FILE):
        print(f"Error: {DUMP_FILE} not found!")
        sys.exit(1)
        
    leakage_filter = ProjectLeakageFilter(BLOCKLIST_FILE)
    project_map = {}
    
    # 1. Extract project_main
    print("Step 1: Extracting project_main...")
    cmd_proj = [PG_RESTORE, "-a", "-t", "project_main", "-f", "-", DUMP_FILE]
    proc_proj = subprocess.Popen(cmd_proj, stdout=subprocess.PIPE, text=True, encoding='utf-8', errors='ignore')
    
    # Skip preamble until we see COPY
    for line in proc_proj.stdout:
        if line.startswith("COPY public.project_main "):
            break
            
    for line in proc_proj.stdout:
        if line.startswith(r"\."):
            break
        parts = line.strip('\n').split('\t')
        if len(parts) >= 11:
            pid = parts[0]
            pname = parts[1]
            plang = parts[2]
            purl = parts[6]
            project_map[pid] = {
                "name": pname,
                "url": purl,
                "language": plang
            }
            
    proc_proj.wait()
    print(f"Loaded {len(project_map)} projects into memory.")
    
    # 2. Extract comment_attr and build CSV
    print("Step 2: Streaming comment_attr and building dataset...")
    
    cmd_cmnt = [PG_RESTORE, "-a", "-t", "comment_attr", "-f", "-", DUMP_FILE]
    proc_cmnt = subprocess.Popen(cmd_cmnt, stdout=subprocess.PIPE, text=True, encoding='utf-8', errors='ignore')
    
    col_indexes = {}
    
    # Wait for COPY line to get schema
    for line in proc_cmnt.stdout:
        if line.startswith("COPY public.comment_attr ("):
            col_str = line[line.find("(")+1 : line.find(")")]
            cols = [c.strip() for c in col_str.split(",")]
            for i, c in enumerate(cols):
                col_indexes[c] = i
            break
            
    if not col_indexes:
        print("Failed to find COPY public.comment_attr header!")
        sys.exit(1)
        
    # Open CSV writer
    os.makedirs(os.path.dirname(OUTPUT_CSV), exist_ok=True)
    with open(OUTPUT_CSV, 'w', encoding='utf-8', newline='') as f_csv:
        writer = csv.writer(f_csv)
        writer.writerow([
            "project_name", "project_url", "language", "filename", "line_number", 
            "comment", "surrounding_code", "satd_label", "satd_feature"
        ])
        
        written_count = 0
        skipped_leakage = 0
        skipped_non_java = 0
        skipped_missing = 0
        
        for line in proc_cmnt.stdout:
            if line.startswith(r"\."):
                break
                
            parts = line.strip('\n').split('\t')
            if len(parts) < len(col_indexes):
                continue
                
            # Extract fields safely
            def get_val(col_name):
                idx = col_indexes.get(col_name, -1)
                val = parts[idx] if idx != -1 else r"\N"
                return val if val != r"\N" else ""
                
            pid = get_val("project_id")
            comment = get_val("comment_content")
            pre_code = get_val("comment_preceding_code")
            succ_code = get_val("comment_succeeding_code")
            line_num = get_val("comment_line_no")
            filename = get_val("comment_source_file")
            satd_aff = get_val("satd_affliction")
            satd_feat = get_val("satd_feature")
            
            proj = project_map.get(pid, {})
            pname = proj.get("name", "")
            purl = proj.get("url", "")
            plang = proj.get("language", "")
            
            if plang.lower() != "java":
                skipped_non_java += 1
                continue
                
            if not comment or not purl:
                skipped_missing += 1
                continue
                
            # Leakage check
            l_stat, _ = leakage_filter.check_record(purl)
            if l_stat in ["EXCLUDE", "FLAGGED"]:
                skipped_leakage += 1
                continue
                
            surrounding_code = pre_code + "\n" + succ_code
            
            # Write row
            writer.writerow([
                pname, purl, plang, filename, line_num,
                comment, surrounding_code, satd_aff, satd_feat
            ])
            written_count += 1
            
            if written_count % 50000 == 0:
                print(f"Extracted {written_count} training records...")
                
    proc_cmnt.wait()
    print("Extraction complete!")
    print(f"Total training records saved: {written_count}")
    print(f"Skipped due to M62 leakage: {skipped_leakage}")
    print(f"Skipped non-Java: {skipped_non_java}")
    print(f"Skipped missing data: {skipped_missing}")
    print(f"Output saved to {OUTPUT_CSV}")

if __name__ == "__main__":
    stream_and_extract()
