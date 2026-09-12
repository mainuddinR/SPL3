# FUTURE TRAINING SCRIPT (DO NOT RUN NOW)
import torch
from transformers import RobertaForSequenceClassification, Trainer, TrainingArguments
from dataset import SATDDataset
from tokenizer_utils import get_tokenizer
from config import *
import numpy as np
from sklearn.metrics import accuracy_score, precision_recall_fscore_support

def compute_metrics(pred):
    labels = pred.label_ids
    logits = pred.predictions[0] if isinstance(pred.predictions, tuple) else pred.predictions
    preds = logits.argmax(-1)
    precision, recall, f1, _ = precision_recall_fscore_support(labels, preds, average='binary')
    acc = accuracy_score(labels, preds)
    return {
        'accuracy': acc,
        'f1': f1,
        'precision': precision,
        'recall': recall
    }

def main():
    print("Preparing CodeBERT Training...")
    tokenizer = get_tokenizer()
    
    # Load Datasets (NEVER load test.csv here)
    train_dataset = SATDDataset(TRAIN_CSV, tokenizer)
    val_dataset = SATDDataset(VAL_CSV, tokenizer)
    
    model = RobertaForSequenceClassification.from_pretrained(MODEL_NAME, num_labels=2)
    
    training_args = TrainingArguments(
        output_dir="./outputs",
        eval_strategy="epoch",
        save_strategy="epoch",
        learning_rate=LEARNING_RATE,
        per_device_train_batch_size=TRAIN_BATCH_SIZE,
        per_device_eval_batch_size=TRAIN_BATCH_SIZE,
        gradient_accumulation_steps=GRADIENT_ACCUMULATION_STEPS,
        num_train_epochs=NUM_EPOCHS,
        weight_decay=0.01,
        load_best_model_at_end=True,
        metric_for_best_model="f1",
        fp16=torch.cuda.is_available(), # Use AMP on GPU
        seed=RANDOM_SEED
    )
    
    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_dataset,
        eval_dataset=val_dataset,
        compute_metrics=compute_metrics
    )
    
    print("Starting Training...")
    trainer.train()
    
    # Save best model
    trainer.save_model("./outputs/best_codebert_satd")
    tokenizer.save_pretrained("./outputs/best_codebert_satd")

if __name__ == "__main__":
    # main()
    print("This script is ready for Colab execution. Execution blocked locally.")
