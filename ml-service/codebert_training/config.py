import os

# Dataset Locations (READ-ONLY)
DATASET_DIR = r"D:\8th semester\SPL3\data\pentacet\controlled_30k"
TRAIN_CSV = os.path.join(DATASET_DIR, "train.csv")
VAL_CSV = os.path.join(DATASET_DIR, "validation.csv")
TEST_CSV = os.path.join(DATASET_DIR, "test.csv")

# Training Configuration
MODEL_NAME = "microsoft/codebert-base"
MAX_LENGTH = 512
TRAIN_BATCH_SIZE = 8
GRADIENT_ACCUMULATION_STEPS = 4
LEARNING_RATE = 2e-5
NUM_EPOCHS = 3
RANDOM_SEED = 42

# Column Names (Verified in Audit)
COL_COMMENT = "comment_content"
COL_PRECEDING = "comment_preceding_code"
COL_SUCCEEDING = "comment_succeeding_code"
COL_SATD = "satd_affliction"
COL_PROJECT = "project_name"

# Section Markers for Input Builder
MARKER_COMMENT = "COMMENT: "
MARKER_PRECEDING = "PRECEDING_CODE: "
MARKER_SUCCEEDING = "SUCCEEDING_CODE: "
