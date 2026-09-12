import pandas as pd
from transformers import RobertaTokenizer
from config import MODEL_NAME, MAX_LENGTH
from input_builder import build_structured_input, get_binary_label

def get_tokenizer():
    return RobertaTokenizer.from_pretrained(MODEL_NAME)

def tokenize_record(row, tokenizer):
    parts = build_structured_input(
        row['comment_content'], 
        row['comment_preceding_code'], 
        row['comment_succeeding_code']
    )
    
    # Tokenize each part without truncation first to get their sizes
    # We use tokenizer() with a massive max_length to suppress the internal model_max_length warning.
    # The true truncation is mathematically calculated and executed manually below.
    tok_c = tokenizer(parts['comment_section'], add_special_tokens=False, truncation=True, max_length=1000000)['input_ids']
    tok_p = tokenizer(parts['preceding_section'], add_special_tokens=False, truncation=True, max_length=1000000)['input_ids']
    tok_s = tokenizer(parts['succeeding_section'], add_special_tokens=False, truncation=True, max_length=1000000)['input_ids']
    
    # Special tokens budget ( [CLS] + [SEP] + [SEP] + [SEP] = 4 tokens)
    special_tokens_len = 4
    budget = MAX_LENGTH - special_tokens_len
    
    len_c = len(tok_c)
    len_p = len(tok_p)
    len_s = len(tok_s)
    
    # 1. Prioritize comment
    if len_c >= budget:
        # Extreme case: comment alone exceeds budget
        tok_c = tok_c[:budget]
        tok_p = []
        tok_s = []
    else:
        rem_budget = budget - len_c
        
        # 2. Allocate remaining budget to preceding and succeeding
        # Prefer keeping lines closest to comment
        # Preceding: keep the end (bottom)
        # Succeeding: keep the start (top)
        
        if len_p + len_s <= rem_budget:
            # Both fit perfectly
            pass
        else:
            # Need to truncate code
            # Let's try 50/50 split of remaining budget
            half = rem_budget // 2
            if len_p <= half:
                # Preceding fits, give rest to succeeding
                alloc_p = len_p
                alloc_s = rem_budget - alloc_p
            elif len_s <= half:
                # Succeeding fits, give rest to preceding
                alloc_s = len_s
                alloc_p = rem_budget - alloc_s
            else:
                # Both are too big, give 50/50
                alloc_p = half
                alloc_s = rem_budget - half
                
            tok_p = tok_p[-alloc_p:] if alloc_p > 0 else []
            tok_s = tok_s[:alloc_s] if alloc_s > 0 else []
            
    # Reconstruct final sequence
    # [CLS] COMMENT [SEP] PRECEDING [SEP] SUCCEEDING [SEP]
    final_ids = [tokenizer.cls_token_id] + tok_c + [tokenizer.sep_token_id] + tok_p + [tokenizer.sep_token_id] + tok_s + [tokenizer.sep_token_id]
    
    attention_mask = [1] * len(final_ids)
    
    # Pad to max length
    pad_len = MAX_LENGTH - len(final_ids)
    final_ids.extend([tokenizer.pad_token_id] * pad_len)
    attention_mask.extend([0] * pad_len)
    
    label = get_binary_label(row.get('satd_affliction', ''))
    
    return {
        'input_ids': final_ids,
        'attention_mask': attention_mask,
        'label': label,
        'diagnostics': {
            'orig_c_len': len_c,
            'orig_p_len': len_p,
            'orig_s_len': len_s,
            'final_c_len': len(tok_c),
            'final_p_len': len(tok_p),
            'final_s_len': len(tok_s),
            'truncated': (len_p + len_s) > (len(tok_p) + len(tok_s))
        }
    }
