import pandas as pd
import torch
from torch.utils.data import Dataset
from tokenizer_utils import tokenize_record

class SATDDataset(Dataset):
    def __init__(self, csv_file, tokenizer):
        self.df = pd.read_csv(csv_file, dtype=str).fillna('')
        # Validation checks
        required_cols = ['comment_content', 'comment_preceding_code', 'comment_succeeding_code', 'satd_affliction']
        for col in required_cols:
            if col not in self.df.columns:
                raise ValueError(f"CRITICAL ERROR: Missing required column '{col}' in {csv_file}")
                
        self.tokenizer = tokenizer

    def __len__(self):
        return len(self.df)

    def __getitem__(self, idx):
        row = self.df.iloc[idx]
        tokens = tokenize_record(row, self.tokenizer)
        return {
            'input_ids': torch.tensor(tokens['input_ids'], dtype=torch.long),
            'attention_mask': torch.tensor(tokens['attention_mask'], dtype=torch.long),
            'labels': torch.tensor(tokens['label'], dtype=torch.long)
        }
