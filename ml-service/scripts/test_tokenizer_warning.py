import sys
from transformers import RobertaTokenizer

tokenizer = RobertaTokenizer.from_pretrained("microsoft/codebert-base")

text = "int a = 1;\n" * 300

print("Testing encode...", flush=True)
sys.stdout.flush()
ids = tokenizer.encode(text, add_special_tokens=False)
print(f"encode length: {len(ids)}", flush=True)

print("Testing tokenize...", flush=True)
sys.stdout.flush()
tokens = tokenizer.tokenize(text)
print(f"tokenize length: {len(tokens)}", flush=True)

print("Testing convert...", flush=True)
sys.stdout.flush()
ids2 = tokenizer.convert_tokens_to_ids(tokens)
print(f"convert length: {len(ids2)}", flush=True)
