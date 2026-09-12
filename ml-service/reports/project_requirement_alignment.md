# Project Requirement Alignment Audit

## 1. ORIGINAL REQUIREMENTS
Based on the `SPL-3_Project_Proposal.txt`, the core requirements are:
- **Core Goal**: AI-powered automated code review system integrating with GitHub Pull Requests to detect Self-Admitted Technical Debt (SATD).
- **Data Source**: Detect SATD directly from **code comments AND surrounding code context**.
- **Model**: Fine-tuned **CodeBERT** model.
- **Classification Categories**: Design Debt, Defect Debt, Test Debt, Requirement Debt, Documentation Debt.
- **Additional Features**: Severity Scoring Module, AI-Based Fix Suggestion (LLM), Automated PR Review Annotations, Web Dashboard.
- **Tools explicitly listed**: Google Colab, Hugging Face Transformers, Spring Boot, Angular.

## 2. CURRENT IMPLEMENTATION
- The backend (Spring Boot) and frontend (Angular) are partially implemented.
- The ML-Service (FastAPI) exists but the model is not trained.
- A massive 15.6M row PENTACET dataset was considered but halted to protect system resources.
- The system currently relies on the `technical_debt_dataset.csv` (62K rows).

## 3. 62K DATASET FIT
The 62K dataset perfectly matches the **Classification Categories** required by the proposal (`DESIGN`, `IMPLEMENTATION`, `DEFECT`, `TEST`, `DOCUMENTATION`, `WITHOUT_CLASSIFICATION`).
However, it only contains `projectname`, `classification`, and `commenttext`. It **entirely lacks the surrounding source code context**.

## 4. GAPS
- **A. Can the 62K dataset be used directly for the proposed CodeBERT model?**
  **NO.** The proposal mandates using "comments and surrounding code context." CodeBERT is a bimodal model designed specifically to take both natural language and programming language as inputs. Feeding it only comments reduces it to a standard NLP model (like RoBERTa), defeating the architectural purpose of using CodeBERT.
- **B. Is there a practical way to obtain the code context WITHOUT the 15.6M PENTACET dataset?**
  **YES.** The 62K dataset only contains 10 unique projects. Instead of processing 15 million rows of raw PostgreSQL dumps, a lightweight script could clone those 10 specific GitHub repositories and use simple AST parsing or Regex to locate the comments and extract the surrounding 10 lines of code. Alternatively, we can drop the code-context requirement entirely.
- **C. Can CodeBERT reasonably be used with the available data?**
  If we use empty strings for the code context, CodeBERT will still function, but it will rely 100% on the comment text. You can honestly claim it performs automated code review based on comments, but claiming it "analyzes surrounding code context" would be factually incorrect unless we fetch the code.

## 5. MINIMUM FIXES (To satisfy the Proposal)
- **Data Duplication:** The dataset contains 23,238 exact duplicates. **Safest handling:** Dynamically drop duplicates in Python using `pandas.DataFrame.drop_duplicates()` immediately after reading the CSV into memory. Do not alter the physical file.
- **Class Imbalance:** The dataset is 93.5% NON-SATD. **Safest handling:** Dynamically undersample the `WITHOUT_CLASSIFICATION` class during the Python dataset preparation to achieve a 1:2 or 1:3 ratio, OR use weighted `CrossEntropyLoss` during PyTorch training.
- **Hardware Bottleneck (No GPU):** CodeBERT cannot realistically be fine-tuned on a CPU. **Safest handling:** Since the proposal explicitly lists **Google Colab** under "Development Tools," the training script should be pushed to a Colab Notebook, trained on a free T4 GPU, and the final `.bin` checkpoint downloaded back to the local `ml-service` for inference.

## 6. OPTIONAL ENHANCEMENTS
- Extracting the missing "surrounding code context" by cloning the 10 repositories.
- Implementing the LLM-based "Fix Suggestion System" (could be mocked or integrated via OpenAI API later).

## 7. RECOMMENDED IMPLEMENTATION PLAN
1. **Data Prep Script:** Write a Python script that loads the 62K dataset, drops duplicates dynamically, performs a project-level stratified split (Train/Val/Test), and undersamples the majority class.
2. **Export for Colab:** Save this cleaned, balanced, split dataset as a tiny, highly-optimized CSV.
3. **Colab Training:** Upload the CSV to Google Colab. Train CodeBERT using Hugging Face `Trainer` (feeding empty strings for the code context if we skip extraction).
4. **Deploy:** Download the trained weights and plug them into the existing FastAPI `codebert_classifier.py`.

## 8. RISKS FOR FINAL DEMONSTRATION
If you present CodeBERT to examiners but admit it only reads comments and ignores the code, they may question why a simpler NLP model wasn't used. You must either successfully extract the code context for the 62K dataset, or justify the limitation as a "future scope" enhancement due to dataset constraints.

## Final Verdict

**NEEDS DATA/IMPLEMENTATION FIX BEFORE CODEBERT**
*(The 62K dataset must be dynamically deduplicated, undersampled, and ideally merged with source code context before it is pushed to Google Colab for training).*
