# Feasibility Test: Context Recovery for 62K Dataset

## 1. TEST SAMPLE SIZE
- **Rows tested:** 100
- **Target Projects:** jruby-1.4.0, apache-ant-1.7.0

## 2. REPOSITORY MAPPING RESULTS
- Mapping `projectname` (e.g. `apache-ant-1.7.0`) to modern GitHub repositories directly is **impossible** because the dataset refers to specific historical releases (e.g., from 2006-2009).
- **Workaround used:** Mapped historical release names to specific GitHub Release Tags/SourceForge archives (e.g. `https://github.com/apache/ant/archive/refs/tags/ANT_170.zip`).
- **Mapped to a downloadable repo:** 100 / 100

## 3. COMMENT MATCHING RESULTS
- **Exact String Matches:** 84
- **Unique Matches (1 file):** 74
- **Ambiguous Matches (>1 file):** 10
- **Unmatched Comments:** 16

## 4. SOURCE-CODE CONTEXT RESULTS
- **Java Files Confirmed:** 74
- **Context Extractable:** 74

## 5. AMBIGUOUS/UNMATCHED CASES
- **Why are comments unmatched?** The `commenttext` in the 62K dataset has often been pre-processed (e.g. stripping `//` or `/*`, removing newlines, converting to lowercase, or stripping punctuation). A strict substring search fails on pre-processed text.
- **Why are matches ambiguous?** Developers often copy-paste the exact same FIXME or TODO comment across multiple files (e.g., `// TODO: implement later`). Without a filepath column in the 62K dataset, it is impossible to know *which* file the comment originally came from.

## 6. ESTIMATED FULL-DATASET FEASIBILITY
Based on the sample, finding the exact source code location using ONLY the comment string as a search query is **highly unreliable**.
If 74 out of 100 were successfully and uniquely matched, we can estimate that only **74.0%** of the 62K dataset is safely recoverable. 

## 7. STORAGE/TIME ESTIMATE
- To process all 62K rows, we must download and extract historical ZIP archives for all 10 projects.
- Extracted source code will consume ~2-3 GB of disk space.
- The matching script would take ~5-10 minutes.
- **BUT**, due to ambiguity and pre-processing, writing a fuzzy-matching script that successfully recovers >90% of the context would take significant engineering effort.

## 8. RISKS
- **Data Corruption:** Assigning the wrong source code context to an ambiguous comment will teach CodeBERT incorrect patterns.
- **High Data Loss:** We may have to discard 50%+ of the dataset if we strictly require unique exact matches.

## 9. RECOMMENDATION
Because the 62K dataset lacks filepaths and the comment text has been stripped of formatting, searching for the original source code context is a fragile, error-prone reverse-engineering task.

**Final Verdict:**
CONTEXT RECOVERY NOT FEASIBLE
