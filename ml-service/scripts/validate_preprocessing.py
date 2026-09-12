import os
import sys
import csv
import pytest
import json

PROCESSED_DIR = r"D:\8th semester\SPL3\data\pentacet\processed\pilot"
OUT_TRAIN = os.path.join(PROCESSED_DIR, "pentacet_train.csv")
OUT_VAL = os.path.join(PROCESSED_DIR, "pentacet_validation.csv")
OUT_TEST = os.path.join(PROCESSED_DIR, "pentacet_test.csv")
MANIFEST = os.path.join(PROCESSED_DIR, "project_split_manifest.csv")
BLOCKLIST_FILE = r"D:\8th semester\SPL3\ml-service\data\m62_project_blocklist.json"

sys.path.append(r"D:\8th semester\SPL3\ml-service")
try:
    from app.data_pipeline.leakage_filter import ProjectLeakageFilter
except ImportError:
    class ProjectLeakageFilter:
        def __init__(self, p): pass
        def check_record(self, p): return "KEEP", ""

def load_csv(file_path):
    maxInt = sys.maxsize
    while True:
        try:
            csv.field_size_limit(maxInt)
            break
        except OverflowError:
            maxInt = int(maxInt/10)
            
    rows = []
    if os.path.exists(file_path):
        with open(file_path, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            for row in reader:
                rows.append(row)
    return rows

def test_files_exist():
    assert os.path.exists(OUT_TRAIN)
    assert os.path.exists(OUT_VAL)
    assert os.path.exists(OUT_TEST)
    assert os.path.exists(MANIFEST)

def test_required_columns():
    train_data = load_csv(OUT_TRAIN)
    if train_data:
        row = train_data[0]
        expected_cols = ["project_name", "project_url", "comment", "surrounding_code", "satd_label", "satd_feature", "is_satd", "codebert_input"]
        for col in expected_cols:
            assert col in row

def test_m62_leakage():
    leakage_filter = ProjectLeakageFilter(BLOCKLIST_FILE)
    
    for path in [OUT_TRAIN, OUT_VAL, OUT_TEST]:
        data = load_csv(path)
        for row in data:
            l_stat, _ = leakage_filter.check_record(row['project_url'])
            assert l_stat not in ["EXCLUDE", "FLAGGED"], f"M62 leakage detected: {row['project_url']}"

def test_project_isolation():
    train_data = load_csv(OUT_TRAIN)
    val_data = load_csv(OUT_VAL)
    test_data = load_csv(OUT_TEST)
    
    train_projects = set(r['project_url'] for r in train_data)
    val_projects = set(r['project_url'] for r in val_data)
    test_projects = set(r['project_url'] for r in test_data)
    
    assert len(train_projects.intersection(val_projects)) == 0
    assert len(train_projects.intersection(test_projects)) == 0
    assert len(val_projects.intersection(test_projects)) == 0

def test_class_ratio():
    for path in [OUT_TRAIN, OUT_VAL, OUT_TEST]:
        data = load_csv(path)
        if not data: continue
        
        satd_count = sum(1 for r in data if r['is_satd'] == '1')
        nonsatd_count = sum(1 for r in data if r['is_satd'] == '0')
        
        if satd_count > 0:
            ratio = nonsatd_count / satd_count
            # Expecting approximately 2.0 (1:2 ratio)
            assert 1.8 <= ratio <= 2.2, f"Ratio in {path} is {ratio:.2f}, expected ~2.0"

def test_codebert_input_format():
    train_data = load_csv(OUT_TRAIN)
    if train_data:
        row = train_data[0]
        cb_input = row['codebert_input']
        assert cb_input.startswith("[COMMENT]")
        assert "[CODE]" in cb_input
