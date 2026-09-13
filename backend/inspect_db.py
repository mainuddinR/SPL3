import sqlite3
import pandas as pd
import sys

db_path = r'd:\8th semester\SPL3\data\pentacet\processed\preprocessing.db'
conn = sqlite3.connect(db_path)

print("Tables:")
print(pd.read_sql("SELECT name FROM sqlite_master WHERE type='table';", conn))

print("\nSample Pull Request:")
try:
    print(pd.read_sql("SELECT * FROM pull_requests LIMIT 1;", conn))
except Exception as e:
    print(e)

try:
    print(pd.read_sql("SELECT * FROM prs LIMIT 1;", conn))
except Exception as e:
    print(e)
