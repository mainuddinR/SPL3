import pandas as pd
from config import MARKER_COMMENT, MARKER_PRECEDING, MARKER_SUCCEEDING

def clean_text(text):
    if pd.isna(text) or text is None:
        return ""
    return str(text).strip()

def build_structured_input(comment, preceding, succeeding):
    c = clean_text(comment)
    p = clean_text(preceding)
    s = clean_text(succeeding)
    
    # We maintain distinct sections that the tokenizer algorithm will use
    return {
        "comment_section": f"{MARKER_COMMENT}{c}",
        "preceding_section": f"{MARKER_PRECEDING}{p}",
        "succeeding_section": f"{MARKER_SUCCEEDING}{s}"
    }

def get_binary_label(satd_affliction):
    lbl = clean_text(satd_affliction)
    if lbl == '':
        return 0 # NON-SATD
    return 1 # SATD
