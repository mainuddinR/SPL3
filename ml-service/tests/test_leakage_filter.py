import pytest
import os
import json
from app.data_pipeline.leakage_filter import ProjectLeakageFilter

@pytest.fixture
def filter_instance(tmp_path):
    blocklist = [
        "apache-ant-1.7.0",
        "hibernate-distribution-3.3.2.GA",
        "jEdit-4.2",
        "sql12"
    ]
    blocklist_path = tmp_path / "m62_project_blocklist.json"
    with open(blocklist_path, 'w') as f:
        json.dump(blocklist, f)
    
    return ProjectLeakageFilter(str(blocklist_path))

def test_exact_match(filter_instance):
    status, _ = filter_instance.check_record("apache-ant-1.7.0")
    assert status == "EXCLUDE"

def test_case_insensitive_match(filter_instance):
    status, _ = filter_instance.check_record("Apache-ANT-1.7.0")
    assert status == "EXCLUDE"

def test_normalized_repo_match(filter_instance):
    # PENTACET might have it as apache/ant or something similar
    status, _ = filter_instance.check_record("https://github.com/apache/ant")
    assert status == "EXCLUDE"
    
    status, _ = filter_instance.check_record("hibernate/hibernate-orm")
    assert status == "FLAGGED"

def test_unrelated_project(filter_instance):
    status, _ = filter_instance.check_record("spring-projects/spring-boot")
    assert status == "KEEP"

def test_missing_project(filter_instance):
    status, _ = filter_instance.check_record("")
    assert status == "FLAGGED"
    
    status, _ = filter_instance.check_record(None)
    assert status == "FLAGGED"

def test_empty_blocklist(tmp_path):
    blocklist_path = tmp_path / "empty_blocklist.json"
    with open(blocklist_path, 'w') as f:
        json.dump([], f)
    
    empty_filter = ProjectLeakageFilter(str(blocklist_path))
    status, _ = empty_filter.check_record("apache-ant-1.7.0")
    assert status == "KEEP"
