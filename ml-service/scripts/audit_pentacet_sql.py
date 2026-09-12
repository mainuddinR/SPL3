import os
import json
import sys
import codecs
from collections import defaultdict

# Add ml-service app path for LeakageFilter
sys.path.append(r"D:\8th semester\SPL3\ml-service")
from app.data_pipeline.leakage_filter import ProjectLeakageFilter

SQL_FILE = r"D:\8th semester\SPL3\data\pentacet\pentacet_clean_and_load_dump.sql"
JSON_OUT = r"D:\8th semester\SPL3\data\pentacet\pentacet_sql_audit.json"
MD_OUT = r"D:\8th semester\SPL3\data\pentacet\pentacet_sql_audit.md"
BLOCKLIST_FILE = r"D:\8th semester\SPL3\ml-service\data\m62_project_blocklist.json"

def audit_sql_dump():
    print(f"Starting audit of {SQL_FILE}")
    
    leakage_filter = ProjectLeakageFilter(BLOCKLIST_FILE)
    
    # We will build dictionaries and stats in-memory for the audit
    project_map = {}
    
    # Stats
    stats = {
        "total_comments": 0,
        "total_java_records": 0,
        "total_projects": 0,
        "satd_count": 0,
        "non_satd_count": 0,
        "missing_comment_count": 0,
        "missing_preceding_code": 0,
        "missing_succeeding_code": 0,
        "missing_project_count": 0,
        "m62_leakage_count": 0,
        "ambiguous_leakage_count": 0,
        "records_remaining_after_leakage": 0,
        "duplicate_comment_count": 0
    }
    
    feature_distribution = defaultdict(int)
    affliction_distribution = defaultdict(int)
    context_lengths = []
    
    # For duplicate checking (store hashes or strings in a set)
    seen_comments = set()
    
    # We read the file twice:
    # Pass 1: Extract project_main
    print("Pass 1: Extracting project_main...")
    with codecs.open(SQL_FILE, 'r', encoding='utf-8', errors='ignore') as f:
        in_project_copy = False
        for line in f:
            if "COPY public.project_main (" in line:
                in_project_copy = True
                continue
            if in_project_copy:
                if line.startswith(r"\."):
                    in_project_copy = False
                    break
                parts = line.strip('\n').split('\t')
                if len(parts) >= 11:
                    proj_id = parts[0]
                    proj_name = parts[1]
                    proj_lang = parts[2]
                    proj_url = parts[6]
                    project_map[proj_id] = {
                        "name": proj_name,
                        "language": proj_lang,
                        "url": proj_url
                    }

    stats["total_projects"] = len(project_map)
    print(f"Found {stats['total_projects']} projects in project_main.")

    # Pass 2: Extract comment_attr
    print("Pass 2: Streaming comment_attr...")
    with codecs.open(SQL_FILE, 'r', encoding='utf-8', errors='ignore') as f:
        in_comment_copy = False
        for line in f:
            if "COPY public.comment_attr (" in line:
                in_comment_copy = True
                # Parse column order from the COPY statement
                # COPY public.comment_attr (col1, col2, ...) FROM stdin;
                col_str = line[line.find("(")+1 : line.find(")")]
                cols = [c.strip() for c in col_str.split(",")]
                
                cid_idx = cols.index("comment_id") if "comment_id" in cols else -1
                ccont_idx = cols.index("comment_content") if "comment_content" in cols else -1
                cpre_idx = cols.index("comment_preceding_code") if "comment_preceding_code" in cols else -1
                csucc_idx = cols.index("comment_succeeding_code") if "comment_succeeding_code" in cols else -1
                pid_idx = cols.index("project_id") if "project_id" in cols else -1
                aff_idx = cols.index("satd_affliction") if "satd_affliction" in cols else -1
                feat_idx = cols.index("satd_feature") if "satd_feature" in cols else -1
                
                print(f"Column indexes -> ID:{cid_idx}, Cont:{ccont_idx}, Pre:{cpre_idx}, Succ:{csucc_idx}, Proj:{pid_idx}, Aff:{aff_idx}, Feat:{feat_idx}")
                continue
                
            if in_comment_copy:
                if line.startswith(r"\."):
                    in_comment_copy = False
                    break
                
                parts = line.strip('\n').split('\t')
                if len(parts) < max(cid_idx, ccont_idx, cpre_idx, csucc_idx, pid_idx, aff_idx, feat_idx):
                    continue
                
                stats["total_comments"] += 1
                
                ccont = parts[ccont_idx] if ccont_idx != -1 else r"\N"
                cpre = parts[cpre_idx] if cpre_idx != -1 else r"\N"
                csucc = parts[csucc_idx] if csucc_idx != -1 else r"\N"
                pid = parts[pid_idx] if pid_idx != -1 else r"\N"
                aff = parts[aff_idx] if aff_idx != -1 else r"\N"
                feat = parts[feat_idx] if feat_idx != -1 else r"\N"
                
                # Project info
                proj_info = project_map.get(pid, {})
                proj_name = proj_info.get("name", "")
                proj_url = proj_info.get("url", "")
                proj_lang = proj_info.get("language", "")
                
                if proj_lang.lower() == "java":
                    stats["total_java_records"] += 1
                
                if pid == r"\N" or not proj_info:
                    stats["missing_project_count"] += 1
                
                # Labels
                if aff != r"\N" and aff.strip():
                    stats["satd_count"] += 1
                    affliction_distribution[aff] += 1
                else:
                    stats["non_satd_count"] += 1
                    
                if feat != r"\N" and feat.strip():
                    feature_distribution[feat] += 1
                
                # Context
                if ccont == r"\N" or not ccont.strip():
                    stats["missing_comment_count"] += 1
                
                if cpre == r"\N" or not cpre.strip():
                    stats["missing_preceding_code"] += 1
                if csucc == r"\N" or not csucc.strip():
                    stats["missing_succeeding_code"] += 1
                
                ctx_len = 0
                if cpre != r"\N": ctx_len += len(cpre)
                if csucc != r"\N": ctx_len += len(csucc)
                if ctx_len > 0:
                    context_lengths.append(ctx_len)
                    
                # Duplicates
                if ccont != r"\N":
                    if ccont in seen_comments:
                        stats["duplicate_comment_count"] += 1
                    else:
                        seen_comments.add(ccont)
                
                # Leakage
                is_leaked = False
                if proj_url:
                    l_stat, _ = leakage_filter.check_record(proj_url)
                    if l_stat == "EXCLUDE":
                        stats["m62_leakage_count"] += 1
                        is_leaked = True
                    elif l_stat == "FLAGGED":
                        stats["ambiguous_leakage_count"] += 1
                        is_leaked = True
                
                if not is_leaked:
                    stats["records_remaining_after_leakage"] += 1

                if stats["total_comments"] % 500000 == 0:
                    print(f"Processed {stats['total_comments']} comments...")

    print("Pass 2 complete.")
    
    # Calculate context length stats
    avg_ctx = sum(context_lengths) / len(context_lengths) if context_lengths else 0
    max_ctx = max(context_lengths) if context_lengths else 0
    min_ctx = min(context_lengths) if context_lengths else 0
    stats["context_length"] = {
        "average": avg_ctx,
        "max": max_ctx,
        "min": min_ctx
    }
    
    # Save JSON
    with open(JSON_OUT, 'w') as f:
        json.dump({
            "stats": stats,
            "affliction_distribution": dict(affliction_distribution),
            "feature_distribution": dict(sorted(feature_distribution.items(), key=lambda x: -x[1])[:50])
        }, f, indent=4)
        
    # Save MD
    with open(MD_OUT, 'w') as f:
        f.write("# PENTACET SQL Audit Report\n\n")
        for k, v in stats.items():
            if k == "context_length": continue
            f.write(f"- **{k}**: {v}\n")
        
        f.write("\n## Context Lengths\n")
        f.write(f"- Average: {avg_ctx:.2f} chars\n")
        f.write(f"- Max: {max_ctx} chars\n")
        f.write(f"- Min: {min_ctx} chars\n")
        
        f.write("\n## Affliction Distribution\n")
        for k, v in affliction_distribution.items():
            f.write(f"- `{k}`: {v}\n")
            
        f.write("\n## Top 50 SATD Features\n")
        for k, v in sorted(feature_distribution.items(), key=lambda x: -x[1])[:50]:
            f.write(f"- `{k}`: {v}\n")

    print(f"Audit saved to {JSON_OUT} and {MD_OUT}")

if __name__ == "__main__":
    audit_sql_dump()
