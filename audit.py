import pandas as pd
import json
import sys

def audit_dataset(file_path):
    print(f"Reading dataset from {file_path}...")
    try:
        df = pd.read_csv(file_path)
    except Exception as e:
        print(f"Failed to read CSV: {e}")
        return

    # Basic stats
    print(f"Number of rows: {len(df)}")
    print(f"Number of columns: {len(df.columns)}")
    print(f"Columns: {list(df.columns)}")
    
    print("\nData Types:")
    for col, dtype in df.dtypes.items():
        print(f" - {col}: {dtype}")
        
    print("\nFirst 5 rows:")
    print(df.head(5).to_string())
    
    print("\nLast 5 rows:")
    print(df.tail(5).to_string())
    
    print("\nMissing values per column:")
    missing = df.isnull().sum()
    for col, val in missing.items():
        print(f" - {col}: {val}")
        
    print("\nDuplicate rows:")
    print(df.duplicated().sum())
    
    print("\nValue distributions (Categorical/Label columns):")
    for col in df.columns:
        if df[col].dtype == 'object' and df[col].nunique() < 50:
            print(f"\nColumn: {col} ({df[col].nunique()} unique values)")
            counts = df[col].value_counts(dropna=False)
            percs = df[col].value_counts(dropna=False, normalize=True) * 100
            for val in counts.index:
                print(f" - {val}: {counts[val]} ({percs[val]:.2f}%)")
                
    print("\nEmpty or whitespace strings:")
    for col in df.columns:
        if df[col].dtype == 'object':
            empty_count = df[col].astype(str).str.strip().eq('').sum()
            if empty_count > 0:
                print(f" - {col}: {empty_count}")
                
    print("\nText lengths:")
    for col in df.columns:
        if df[col].dtype == 'object' and df[col].nunique() >= 50:
            lens = df[col].dropna().astype(str).str.len()
            print(f" - {col}: min={lens.min()}, max={lens.max()}, avg={lens.mean():.2f}")
            
if __name__ == "__main__":
    audit_dataset(r"D:\8th semester\SPL3\technical_debt_dataset.csv")
