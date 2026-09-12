import sys
from transformers import RobertaTokenizer

tokenizer = RobertaTokenizer.from_pretrained("microsoft/codebert-base")
text = "int a = 1;\n" * 300

print("Testing __call__ with truncation=True, max_length=1000000...", flush=True)
sys.stdout.flush()
ids = tokenizer(text, add_special_tokens=False, truncation=True, max_length=1000000)['input_ids']
print(f"call length: {len(ids)}", flush=True)
