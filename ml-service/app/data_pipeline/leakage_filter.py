import json
import re
import os
from typing import Dict, List, Tuple

class ProjectLeakageFilter:
    def __init__(self, blocklist_path: str):
        self.blocklist_path = blocklist_path
        self.blocklist = self._load_and_normalize_blocklist()
        
        # Stats tracking
        self.total_records = 0
        self.excluded_records = 0
        self.retained_records = 0
        self.missing_project_records = 0
        self.ambiguous_records = 0
        self.excluded_projects = set()

    def _load_and_normalize_blocklist(self) -> List[str]:
        if not os.path.exists(self.blocklist_path):
            return []
        with open(self.blocklist_path, 'r') as f:
            projects = json.load(f)
        return [self.normalize_project_name(p) for p in projects]

    @staticmethod
    def normalize_project_name(name: str) -> str:
        if not name:
            return ""
        # Lowercase
        name = name.lower()
        # Remove version numbers commonly found in M62 (e.g., -1.7.0, -2.10, -3.3.2.GA)
        name = re.sub(r'[-_]\d+(\.\d+)*(\.[a-z]+)*$', '', name)
        # Remove common extensions or src tags (e.g., -src)
        name = re.sub(r'[-_]src$', '', name)
        # Remove github URLs if present
        name = re.sub(r'https?://(www\.)?github\.com/', '', name)
        # Replace common separators with spaces or just strip them (we'll just remove them for aggressive matching without fuzzy matching)
        name = re.sub(r'[-_/\\]', '', name)
        return name

    def check_record(self, record_project: str) -> Tuple[str, str]:
        """
        Returns (status, reason)
        Status: 'EXCLUDE', 'KEEP', 'FLAGGED'
        """
        self.total_records += 1
        
        if not record_project or not record_project.strip():
            self.missing_project_records += 1
            return "FLAGGED", "Missing project information"

        normalized = self.normalize_project_name(record_project)
        
        # Check exact matches against normalized blocklist
        for blocked_proj in self.blocklist:
            if blocked_proj == normalized or (len(blocked_proj) > 3 and blocked_proj in normalized):
                self.excluded_records += 1
                self.excluded_projects.add(record_project)
                return "EXCLUDE", f"Matched M62 blocked project: {blocked_proj}"
        
        # Check for ambiguous mappings (e.g. 'hibernate' vs 'hibernatedistribution')
        # We can do a simple prefix check for the core project name (first 5+ chars)
        record_core = re.sub(r'[^a-z]', '', record_project.lower()[:10])
        for blocked_proj in self.blocklist:
            blocked_core = blocked_proj[:10]
            if len(record_core) >= 5 and len(blocked_core) >= 5:
                if record_core.startswith(blocked_core) or blocked_core.startswith(record_core):
                    self.ambiguous_records += 1
                    return "FLAGGED", f"Ambiguous match with {blocked_proj}"
        
        self.retained_records += 1
        return "KEEP", "No match found"

    def generate_report(self) -> str:
        perc = (self.excluded_records / self.total_records * 100) if self.total_records > 0 else 0
        report = (
            f"--- Leakage Filter Report ---\n"
            f"Total records examined: {self.total_records}\n"
            f"Records excluded: {self.excluded_records} ({perc:.2f}%)\n"
            f"Records retained: {self.retained_records}\n"
            f"Records missing project info: {self.missing_project_records} (Flagged)\n"
            f"Ambiguous records: {self.ambiguous_records} (Flagged)\n"
            f"Excluded projects count: {len(self.excluded_projects)}\n"
            f"Excluded projects list: {list(self.excluded_projects)}\n"
            f"-----------------------------"
        )
        return report
