# FUTURE EVALUATION SCRIPT (DO NOT RUN NOW)
from transformers import RobertaForSequenceClassification, Trainer
from dataset import SATDDataset
from tokenizer_utils import get_tokenizer
from train import compute_metrics
from config import TEST_CSV

def main():
    model_path = "./outputs/best_codebert_satd"
    tokenizer = get_tokenizer()
    test_dataset = SATDDataset(TEST_CSV, tokenizer)
    
    model = RobertaForSequenceClassification.from_pretrained(model_path)
    trainer = Trainer(model=model, compute_metrics=compute_metrics)
    
    results = trainer.evaluate(test_dataset)
    print("Final Test Results:", results)

if __name__ == "__main__":
    # main()
    print("Evaluation script ready.")
