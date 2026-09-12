import sys
from transformers import RobertaTokenizer
import logging
logging.basicConfig(level=logging.INFO)

tokenizer = RobertaTokenizer.from_pretrained("microsoft/codebert-base")
text = "int a = 1;\n" * 300

print("Testing tokenize...", flush=True)
sys.stdout.flush()
tokens = tokenizer.tokenize(text)
print(f"tokenize length: {len(tokens)}", flush=True)
