import pandas as pd
import json
import os

def extract_projects():
    dataset_path = r"D:\8th semester\SPL3\technical_debt_dataset.csv"
    output_dir = r"D:\8th semester\SPL3\ml-service\data"
    output_file = os.path.join(output_dir, "m62_project_blocklist.json")
    
    os.makedirs(output_dir, exist_ok=True)
    
    print(f"Reading dataset: {dataset_path}")
    df = pd.read_csv(dataset_path)
    
    projects = df['projectname'].unique()
    print(f"Total unique projects: {len(projects)}")
    print(f"Projects: {list(projects)}")
    
    # Calculate stats per project
    stats = {}
    for proj in projects:
        proj_df = df[df['projectname'] == proj]
        total = len(proj_df)
        non_satd = len(proj_df[proj_df['classification'] == 'WITHOUT_CLASSIFICATION'])
        satd = total - non_satd
        stats[proj] = {
            "total_rows": total,
            "satd_rows": satd,
            "non_satd_rows": non_satd
        }
    
    print("\nStats per project:")
    for p, s in stats.items():
        print(f" - {p}: {s}")
        
    # Save the blocklist
    with open(output_file, 'w') as f:
        json.dump(list(projects), f, indent=4)
    print(f"\nSaved blocklist to {output_file}")

if __name__ == "__main__":
    extract_projects()
