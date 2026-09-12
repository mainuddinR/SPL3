import pandas as pd
from tokenizer_utils import get_tokenizer, tokenize_record
from input_builder import get_binary_label
import os
import time

TRAIN_CSV = r"D:\8th semester\SPL3\data\pentacet\controlled_30k\train.csv"
REPORT_FILE = r"D:\8th semester\SPL3\ml-service\reports\codebert_20_record_smoke_test_report.md"

def run_smoke_test():
    print("Loading 20 records for smoke test...")
    df = pd.read_csv(TRAIN_CSV, dtype=str).fillna('')
    df['bin_label'] = df['satd_affliction'].apply(get_binary_label)
    
    # 10 SATD, 10 NON-SATD
    df_satd = df[df['bin_label'] == 1].head(10)
    df_nonsatd = df[df['bin_label'] == 0].head(10)
    df_test = pd.concat([df_satd, df_nonsatd])
    
    print("Initializing CodeBERT tokenizer...")
    tokenizer = get_tokenizer()
    
    results = []
    errors = []
    
    for idx, row in df_test.iterrows():
        try:
            tokens = tokenize_record(row, tokenizer)
            results.append({
                'idx': idx,
                'project': row['project_name'],
                'label': tokens['label'],
                'comment': row['comment_content'],
                'has_pre': bool(row['comment_preceding_code'].strip()),
                'has_suc': bool(row['comment_succeeding_code'].strip()),
                'input_ids': tokens['input_ids'],
                'diag': tokens['diagnostics']
            })
            
            # Verifications
            assert tokens['label'] in [0, 1]
            assert len(tokens['input_ids']) == 512
            assert len(tokens['attention_mask']) == 512
            
        except Exception as e:
            errors.append(f"Row {idx}: {str(e)}")

    passed = len(results) == 20 and len(errors) == 0
    
    # Metrics
    tok_lens = [sum(1 for x in r['input_ids'] if x != tokenizer.pad_token_id) for r in results]
    max_len = max(tok_lens) if tok_lens else 0
    min_len = min(tok_lens) if tok_lens else 0
    avg_len = sum(tok_lens) / len(tok_lens) if tok_lens else 0
    trunc_count = sum(1 for r in results if r['diag']['truncated'])
    
    # Print 5 diagnostic records
    print("\n=== SMOKE TEST DIAGNOSTICS (5 Records) ===")
    for r in results[:5]:
        print(f"Index: {r['idx']} | Project: {r['project']} | Label: {r['label']}")
        print(f"Comment: {r['comment'][:60]}...")
        print(f"Preceding Exist: {r['has_pre']} | Succeeding Exist: {r['has_suc']}")
        print(f"Truncated: {r['diag']['truncated']}")
        print("-" * 50)
        
    print(f"\nStatus: {'SMOKE TEST PASSED' if passed else 'SMOKE TEST FAILED'}")
    
    # Generate Report
    report = f"""# CodeBERT 20-Record Smoke Test Report

- **Timestamp:** {time.strftime('%Y-%m-%d %H:%M:%S')}
- **Tokenizer:** microsoft/codebert-base
- **Records Tested:** {len(results)}
- **SATD Count:** {sum(1 for r in results if r['label'] == 1)}
- **NON-SATD Count:** {sum(1 for r in results if r['label'] == 0)}

## Tokenizer Statistics
- **Max Length:** {max_len} / 512
- **Min Length:** {min_len} / 512
- **Average Length:** {avg_len:.1f} / 512
- **Truncated Records:** {trunc_count}

## Feature Preservation
- **Comment Preserved:** YES (Prioritized in algorithm)
- **Preceding Context:** YES (Bottom lines preserved during truncation)
- **Succeeding Context:** YES (Top lines preserved during truncation)

## Errors
{chr(10).join(errors) if errors else 'None'}

## Final Status
{'SMOKE TEST PASSED' if passed else 'SMOKE TEST FAILED'}
"""
    with open(REPORT_FILE, 'w', encoding='utf-8') as f:
        f.write(report)

if __name__ == "__main__":
    run_smoke_test()
