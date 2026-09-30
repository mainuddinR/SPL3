# PROJECT_CONTEXT.md

## 1. Project Identity

**Project:** Automated Code Review / Technical Debt Analyzer (SPL-3)

University SPL-3 project. The implementation must remain aligned with the **original Project Proposal and SRS**. Do not change the Proposal/SRS to fit the implementation.

Core goal: analyze GitHub Pull Requests and detect **Self-Admitted Technical Debt (SATD)** using source-code comments together with surrounding source-code context.

```text
Angular Frontend
      |
      v
Spring Boot Backend :8080
      |
      +--> GitHub OAuth / GitHub API
      +--> PR synchronization / diff processing
      +--> SatdExtractionService
                |
                v
          CodeBertClientService
                |
                v
FastAPI ML Service :8001
      |
      v
Fine-tuned CodeBERT
      |
      v
SATD / NON-SATD + probabilities/confidence
```

## 2. Mandatory Development Rules

1. Inspect existing code before editing.
2. Preserve the existing architecture unless a requirement genuinely requires change.
3. Avoid unrelated refactoring; project is near submission.
4. Do **not** retrain CodeBERT unless explicitly requested.
5. Do **not** replace the fine-tuned model with base `microsoft/codebert-base`.
6. Do **not** use mock/fake AI predictions instead of the real model.
7. Preserve GitHub OAuth + JWT authentication.
8. Preserve Spring Boot -> FastAPI integration.
9. Do not disable Spring Security or make protected endpoints public just for testing.
10. Do not add dummy production data/CommandLineRunner seeds merely for testing.
11. Do not change DB schema unless the requested feature genuinely requires it.
12. Verify SRS requirements against the actual Proposal/SRS when available.
13. Do not rewrite working backend/ML functionality without a concrete reason.
14. Use **JDK 21**, not JDK 24, for backend work.
15. Never commit JWTs, OAuth secrets, passwords, or other credentials.

## 3. Dataset History and Final Decision

Original `technical_debt_dataset.csv` audit:

- 62,275 rows
- 23,238 exact duplicates
- ~93.5% NON-SATD / ~6.5% SATD
- only `projectname`, `classification`, `commenttext`
- no source-code context

Historical context recovery was tested on 100 rows using old release/tag archives. Result: 74 unique matches, 10 ambiguous, 16 unmatched. This approach was rejected as unreliable.

The project moved to **PENTACET**, which contains code context. Processing the entire ~15.6M-row dataset was intentionally abandoned because it was unnecessary and risky for the deadline.

### Final Controlled 30K Dataset

```text
D:\8th semester\SPL3\data\pentacet\controlled_30k\
```

Properties:

- 30,000 unique records
- 15,000 SATD / 15,000 NON-SATD
- Java-only
- non-empty comments
- duplicate `comment + context` removed
- ~98.4% have both preceding and succeeding context
- 2,037 projects
- project-disjoint splitting; no project overlap across train/validation/test

One generated split was Train 23,892 / Validation 3,090 / Test 3,018. The actual final Colab evaluation file contained **2,988 rows**, and all 2,988 were evaluated; use the actual final evaluation results below for reporting.

Binary label construction:

```text
non-empty satd_affliction -> SATD (1)
empty satd_affliction     -> NON-SATD (0)
```

## 4. CodeBERT Preprocessing and Training

Input consists of:

- comment
- preceding source code
- succeeding source code

Maximum sequence length: **512 tokens**. Structured truncation preserves the comment and nearby code instead of blindly truncating the combined text.

Training package:

```text
D:\8th semester\SPL3\ml-service\codebert_training\
```

Important files:

```text
requirements.txt
config.py
input_builder.py
tokenizer_utils.py
dataset.py
train.py
evaluate.py
README.md
smoke_test.py
```

Smoke test: 20 records (10 SATD / 10 NON-SATD), valid IDs/masks, no final sequence >512, null safety passed.

A later inference warning came from unbounded token sizing before manual truncation. `tokenizer_utils.py` was fixed while preserving the existing 512-token allocation logic. Verified long final input token count: 512.

Training hardware: Google Colab Tesla T4.

Base pretrained model used for fine-tuning: `microsoft/codebert-base`.

Key training configuration:

```text
learning rate: 2e-5
train batch size: 8
eval batch size: 8
gradient accumulation: 4
effective batch size: ~32
epochs: 5
weight decay: 0.01
max sequence length: 512
early stopping patience: 2
selection metric: validation F1
```

## 5. Best Checkpoint and Final Model

Checkpoint trainer state showed:

```text
checkpoint-751   -> ~0.955323
checkpoint-1502  -> ~0.971503
checkpoint-2253  -> ~0.971999
checkpoint-3004  -> still best checkpoint-2253
checkpoint-3755  -> ~0.974102
```

Confirmed best checkpoint: **checkpoint-3755**.

Production inference model:

```text
D:\8th semester\SPL3\ml-service\models\codebert_satd\best_model
```

Verified model files include:

```text
config.json
model.safetensors
tokenizer.json
tokenizer_config.json
training_args.bin
```

`model.safetensors` is ~475.5 MB.

**Always use this fine-tuned local model for inference. Do not use base CodeBERT.**

## 6. Final Model Evaluation

Actual final test evaluation:

```text
rows evaluated = 2,988
missing predictions = 0
Accuracy  = 0.9521
Precision = 0.9760
Recall    = 0.9611
F1        = 0.9685
ROC-AUC   = 0.9854
TP = 2198
TN = 647
FP = 54
FN = 89
```

Final **test F1 = 0.9685**. Do not confuse this with the best validation metric (~0.9741).

## 7. ML Inference Service

Python environment:

```text
D:\8th semester\SPL3\ml-service\venv\Scripts\python.exe
```

Classifier:

```text
ml-service/app/services/codebert_classifier.py
```

It loads the actual fine-tuned local model/tokenizer, uses `.eval()` and `torch.no_grad()`, and uses GPU when available otherwise CPU.

Input:

```text
comment
preceding_code
succeeding_code
```

Output:

```text
label
satd_probability
non_satd_probability
confidence
```

FastAPI endpoint:

```text
POST http://localhost:8001/api/v1/satd-detect
Swagger: http://localhost:8001/docs
```

Verified SATD example returned approximately:

```json
{
  "label": "SATD",
  "satd_probability": 0.9999402761459351,
  "non_satd_probability": 0.00005973592124064453,
  "confidence": 0.9999402761459351
}
```

NON-SATD inference has also been verified successfully.

## 8. Backend Environment

Spring Boot backend runs on port **8080**.

Required Java: **JDK 21**.

Known installation:

```text
C:\Program Files\Java\jdk-21.0.11
```

JDK 24 previously caused a Lombok/compiler incompatibility (`TypeTag :: UNKNOWN`). This was an environment issue, not an ML issue.

Backend tests under JDK 21 have passed:

```text
9 tests
9 passed
0 failures/errors
```

## 9. Backend SATD Architecture

```text
PullRequestController
       |
       v
PullRequestAnalysisService
       |
       v
SatdExtractionService
       |
       v
CodeBertClientService
       |
       v
FastAPI /api/v1/satd-detect
```

Relevant DTOs:

```text
SatdCandidateDTO
CodeBertRequestDTO
CodeBertResponseDTO
SatdAnalysisResponseDTO
```

`SatdCandidateDTO` now separates:

```text
commentText
precedingCode
succeedingCode
filename
language
lineNumber
```

`SatdExtractionService` parses source comments from unified PR diffs and preserves filename, source line number, preceding context, succeeding context, and comment text. The comment itself is excluded from preceding/succeeding context.

Protected analysis endpoint:

```text
POST /api/prs/{prId}/analyze
```

## 10. Spring Boot -> FastAPI HTTP Compatibility Fix

Initial integration produced Uvicorn warnings such as `Unsupported upgrade request` and `Invalid HTTP request received`.

The Spring `RestClient` was configured with:

```java
RestClient.builder()
    .requestFactory(new SimpleClientHttpRequestFactory())
    .build();
```

This uses the compatible HTTP/1.1 request path with Uvicorn. Do not casually remove this configuration.

ML service URL in backend configuration is `http://localhost:8001`.

## 11. Authentication Architecture and H2 Issue

Security flow:

```text
Angular
  -> /oauth2/authorization/github
  -> GitHub OAuth
  -> CustomOAuth2SuccessHandler
  -> application JWT
  -> Angular
  -> Authorization: Bearer <JWT> on protected API calls
```

Important classes/files:

```text
SecurityConfig
JwtAuthTokenFilter
JwtUtils
CustomOAuth2SuccessHandler
auth-interceptor.ts
```

Development DB is in-memory H2:

```text
jdbc:h2:mem:devdb
```

Therefore Spring Boot restart wipes stored DB data.

A real issue occurred because `JwtAuthTokenFilter` validated the JWT but also required `userRepository.findByGithubId(...)`. After an H2 restart, the user row disappeared, authentication was not placed in `SecurityContext`, and Spring Security redirected the request to GitHub OAuth login, returning GitHub login HTML.

Current fix: `JwtAuthTokenFilter` authenticates directly from a cryptographically valid application JWT without depending on the wiped H2 user row. The reported changed file was:

```text
security/JwtAuthTokenFilter.java
```

Tests after this fix: BUILD SUCCESS, 9 tests, 0 failures.

Security note: sufficient for the current development/demo architecture, but review before treating as production-grade security.

## 12. GitHub Repository / PR Flow

Existing operations include:

```text
POST /api/github/repos/select
POST /api/prs/project/{projectId}/sync
POST /api/prs/{prId}/sync-details
```

`GithubService` includes repository selection, PR sync, and PR detail sync functionality.

Real repository used for testing: **apache/ant**.

Important distinction: a frontend `Comments (0)` value may mean GitHub discussion/review comments. SATD extraction analyzes **source-code comments inside changed code/diffs** (`// TODO`, `// FIXME`, `/* ... */`, JavaDoc, etc.).

## 13. Verified Real End-to-End Test

Internal DB PR ID **19** was successfully analyzed with:

```text
POST http://localhost:8080/api/prs/19/analyze
Authorization: Bearer <valid application JWT>
```

It returned multiple predictions from the real fine-tuned model.

Example:

```text
filename:
src/main/org/apache/tools/ant/taskdefs/email/EmailTask.java

lineNumber: 113
commentText: /** more strict TLS server certificate check */
label: NON-SATD
satdProbability: 0.00011576359247556
nonSatdProbability: 0.9998842477798462
confidence: 0.9998842477798462
```

Additional comments from `EmailTask.java` and `Mailer.java` were also classified successfully.

This verifies the real pipeline:

```text
Real GitHub PR
 -> Spring Boot stored PR/diff
 -> SatdExtractionService
 -> comment + preceding code + succeeding code
 -> CodeBertClientService
 -> FastAPI :8001
 -> fine-tuned CodeBERT
 -> prediction probabilities
 -> Spring Boot API response
```

PR #19 mostly contained JavaDoc/documentation comments, so high-confidence NON-SATD predictions are reasonable. A future demo should ideally include a real PR/diff with an explicit debt-style TODO/FIXME/workaround comment to demonstrate the positive SATD path. Do not fabricate a positive result as if it came from a real PR.

## 14. Frontend Current State

Frontend technology:

```text
Angular 20 standalone SPA
TypeScript
CSS/SCSS + Tailwind
```

Known routes:

```text
/login
/dashboard
/dashboard/repos
/dashboard/projects/:projectId/pulls
/dashboard/pulls/:prId
```

Important services:

```text
github.service.ts
pull-request.service.ts
```

GitHub login/repository/PR browsing exists. The first SATD frontend integration calls the existing analysis endpoint and displays binary predictions with source context. Angular production build passed. On 2026-09-24, the user confirmed successful manual browser E2E verification against Spring Boot, FastAPI :8001 and the local fine-tuned CodeBERT model on a real Apache Ant PR.

## 15. SRS-Related Gaps Previously Identified

Previous audit identified these likely implemented areas:

```text
GitHub PR integration
GitHub login
repository / PR browsing
```

Likely missing/incomplete areas included:

```text
SATD detection display
context-aware analysis display
classification/category display
severity scoring
AI/fix suggestions
risky-code highlighting
SATD count summary
severity charts
SATD statistics/history
```

Potential SATD categories previously identified:

```text
Design Debt
Defect Debt
Test Debt
Requirement Debt
Documentation Debt
```

Potential SRS functionality previously identified:

```text
severity levels 1-5
AI/LLM fix suggestions
repository SATD statistics
PR analysis history
severity visualization
```

**Do not treat this historical list as current or authoritative without checking the actual Proposal/SRS.** SATD detection and context display are now implemented and user-verified in the browser. The original Proposal was re-read on 2026-09-24; no separate original SRS was found in the repository. The prior `frontend_srs_audit.md` derives its requirements from the Proposal.

## 16. Database Constraint

Existing `SATDFinding` persistence did not contain all newer probability/context fields. During Phase 1 the deliberate decision was **not to modify the DB schema**. The analysis endpoint currently returns DTO results without requiring persistence of every prediction.

If later SRS/history functionality genuinely requires persistence, inspect existing entities/repositories/schema and the SRS first.

## 17. Testing Constraints

Thunder Client became unusable/outdated. Do not depend on it.

Protected endpoints can be tested with PowerShell, for example:

```powershell
$token = "YOUR_CURRENT_JWT"

Invoke-RestMethod `
  -Uri "http://localhost:8080/api/prs/19/analyze" `
  -Method POST `
  -Headers @{
      Authorization = "Bearer $token"
  }
```

Never place a real JWT in repository documentation.

## 18. Current Verified System Status

### ML

```text
Fine-tuned CodeBERT: WORKING
Local model loading: WORKING
FastAPI inference: WORKING
SATD prediction: WORKING
NON-SATD prediction: WORKING
512-token preprocessing: WORKING
```

### Backend

```text
Spring Boot: WORKING
JDK 21 build/tests: WORKING
GitHub OAuth: IMPLEMENTED
JWT auth: WORKING after current filter fix
GitHub repo sync: IMPLEMENTED
PR sync: IMPLEMENTED
PR detail/diff sync: IMPLEMENTED
SATD extraction: WORKING
Spring Boot -> FastAPI: WORKING
/api/prs/{prId}/analyze: WORKING
Real PR #19 E2E test: PASSED
```

### Frontend

```text
GitHub login/repository/PR viewer: IMPLEMENTED
SATD analysis UX: IMPLEMENTED / BUILD PASSED / REAL FRONTEND E2E MANUALLY VERIFIED BY USER (2026-09-24)
```

## 19. Immediate Next Development Goal

The first integration described below is implemented and manually verified in the browser by the user. Planning for the remaining original Proposal requirements is now in progress; no additional capability is recorded as implemented.

```text
User opens PR Details
        |
        v
Clicks "Analyze PR"
        |
        v
Angular POST /api/prs/{prId}/analyze
        |
        v
Existing JWT interceptor adds Bearer token
        |
        v
Spring Boot performs real analysis
        |
        v
Angular receives analysis results
        |
        v
Display findings
```

Initially display only fields already returned by the backend, such as:

```text
filename
lineNumber
commentText
precedingCode
succeedingCode
label
satdProbability
nonSatdProbability
confidence
```

Do not invent backend fields merely to make the UI appear complete.

After the basic analysis display works, compare the remaining implementation against the exact SRS and complete missing requirements incrementally.

## 20. Codex Working Protocol

Before making changes, Codex should:

1. Read this file completely.
2. Read the original Proposal and SRS if present.
3. Inspect repository structure and relevant implementation.
4. Treat source code + Proposal/SRS as source of truth if they conflict with this handoff.
5. Confirm the requested feature is not already implemented.
6. Identify the smallest set of files requiring changes.
7. Preserve the verified ML/backend pipeline.
8. Run relevant tests/build after changes.
9. Report files changed, why, tests/build results, and remaining limitations.
10. Update this context's status only after new behavior is actually verified.

## 21. First Prompt to Give Codex

```text
Before making any code changes, read PROJECT_CONTEXT.md completely.

Then inspect the repository and read the original Project Proposal and SRS if they
are available in the project.

Treat the existing source code and the original Proposal/SRS as the source of truth
if anything in PROJECT_CONTEXT.md conflicts with them. PROJECT_CONTEXT.md is a
handoff document describing the latest known implementation state.

Do not modify code yet.

Report:
1. Your understanding of the current architecture.
2. Which parts are already working and should not be rewritten.
3. The current frontend/backend/ML integration state.
4. Remaining requirements you can verify directly from the SRS.
5. The safest next implementation step.

Important constraints:
- no CodeBERT retraining
- use the existing fine-tuned local model
- no mock AI responses
- preserve GitHub OAuth/JWT
- preserve Spring Boot -> FastAPI integration
- do not disable security
- avoid unrelated refactoring
- do not change DB schema unless genuinely required
- use JDK 21 for backend work
- inspect before editing
```

## 22. Current Handoff Point

**Handoff state:** Angular + backend + ML real end-to-end SATD analysis is manually verified working on real Apache Ant PR data, as reported by the user on 2026-09-24.

**Last successful verification:** The user reported that Angular Analyze PR called `POST /api/prs/{internalPrId}/analyze` and displayed real CodeBERT results with label, filename, line number, SATD probability, confidence, extracted comment and preceding/succeeding code. The browser test's internal PR ID was not specified. The earlier PowerShell verification used internal PR ID 19.

**Next focus:** Dependency-aware planning against the original Proposal. Implementation of remaining requirements has not begun in this planning task.

## 23. First Frontend SATD Integration (2026-09-23)

- Added `SatdAnalysisResponse` to `frontend/src/app/models/pull-request.model.ts`, matching the nine camelCase fields in `SatdAnalysisResponseDTO`.
- Added `PullRequestService.analyzePullRequest(prId)`: POST `/api/prs/{prId}/analyze` with no request payload, using the existing registered JWT interceptor.
- Added Analyze PR beside Sync Details and an SATD Analysis section on PR Details. Existing Overview, Files, and Comments tabs remain.
- Displays returned label, filename, line number, extracted comment, SATD probability and confidence; expandable preceding/succeeding code preserves whitespace.
- Implements not-run, loading, findings, empty-array and safe error states. Guards duplicate requests and concurrent sync/analysis. Clears analysis when syncing or changing PRs and unsubscribes from analysis on navigation/destruction.
- Results are held only in page memory. No persistence, severity, debt categories, LLM suggestions, charts, history or GitHub posting was added.
- No backend, ML, authentication configuration or database changes were made.
- Verification: `npm.cmd run build` in `frontend` passed (production Angular build with strict TypeScript/template checks). The first sandbox attempt failed with `spawn EPERM`; the approved build outside the sandbox succeeded. Final source diff was reviewed for scope and whitespace.
- Frontend browser/E2E testing was not performed during the initial implementation task. This limitation was superseded by the user's manual verification reported on 2026-09-24 (see section 24).

## 24. User-Verified Frontend E2E and Inspection Corrections (2026-09-24)

- The user manually verified: Angular PR Details -> Analyze PR -> Spring Boot `/api/prs/{internalPrId}/analyze` -> SatdExtractionService -> FastAPI :8001 -> local fine-tuned CodeBERT -> Spring Boot response -> Angular SATD Analysis UI, using a real Apache Ant PR. This is user-reported verification, not a new agent-run test.
- The real CodeBERT classifier is binary only. The Proposal's Project Description explicitly attributes five-category classification to CodeBERT, but the current model does not provide that capability. The legacy `DebtCategory` enum and mock classifier do not establish real category support.
- The Proposal specifies severity 1-5 and complexity, method length and risky keywords, but supplies no scoring formula or thresholds. Current extraction supplies up to five context lines on each side within a diff hunk, not a complete method or measured complexity.
- No real category service, severity scoring service, LLM provider integration, webhook receiver or GitHub review publisher was found in the inspected application code.
- `SATDFinding` already has category (required), severity, security flag, comment, suggestion, line and timestamp fields, plus required analysis-run/file links. It lacks binary prediction probabilities/confidence and code-context snapshots. `AnalysisRun` has PR, status and start/end timestamps. The analysis service does not save either entity.
- `GithubService.fetchAndStorePullRequestDetails` deletes and recreates file rows during sync; persisted findings would reference those rows through a required foreign key. PR entities/DTOs do not currently retain the PR head commit SHA.
- Dashboard repository counts are user-scoped, while PR count uses the global repository count operation. Critical issues (0), technical debt score (100), repository health (100), Healthy status and Not analyzed timestamps are fixed values, not analysis-derived statistics.
- The Proposal explicitly lists GitHub Apps and automatic PR analysis in Scope section 1, inline GitHub annotations in section 5, and GitHub Webhooks under technologies. Existing GitHub requests are read operations without an attached user access token; manual sync/analyze does not implement automatic review publishing.
- Development storage remains in-memory H2; Oracle is listed in the Proposal and its connection configuration is commented out. No database or code changes were made during this planning task.

## 25. Local SATD Location Highlighting (2026-09-24)

- Implemented local Angular `View in diff` navigation for real analysis findings whose label is exactly `SATD`. It selects the matching filename in the Files tab, scrolls to the returned new-file line and focuses the rendered row.
- Added a small unified-diff parser in `frontend/src/app/features/pull-requests/pull-request-details/unified-diff.ts`. Hunk headers reset old/new counters; context advances both, additions advance new only, deletions advance old only, and metadata has no source line. Matching uses exact filename and new-file `lineNumber`.
- The Files diff marks only matched SATD rows with a visible `SATD location` text label, red background and outline while retaining existing addition/deletion text colors. No affected line range is inferred.
- If a patch, file or requested line is unavailable, the SATD analysis card remains and shows `Diff location unavailable`. NON-SATD results get no SATD navigation or markers. The existing analysis reset on sync/PR change clears markers because markers derive only from current analysis results.
- Added focused `unified-diff.spec.ts` tests for line-counter behavior, multiple hunks, metadata, exact location matching, unavailable mapping, and NON-SATD behavior. Five tests passed in Chrome via Angular test runner.
- Angular production build passed and `git diff --check` found no whitespace errors. No browser E2E verification of this highlighting feature was performed in this task; the earlier user-verified analysis E2E remains valid.
- No backend, ML, database/schema, API contract, security/JWT, GitHub synchronization or GitHub posting changes were made.

## 26. SATD Analysis Persistence Foundation (2026-09-24)

- Each authorized `POST /api/prs/{prId}/analyze` now creates a new RUNNING `AnalysisRun`; successful analysis commits SATD finding snapshots and marks the run COMPLETED with `analyzedCandidateCount`. An inference/persistence failure marks the run FAILED where possible and preserves the existing API failure behavior.
- Analysis ownership is checked through `PullRequest -> GithubProject -> User.githubId` against the existing authenticated principal. Missing and non-owned PR IDs receive the same not-found response before any run is created.
- `SATDFinding` stores only results labeled exactly SATD, including filename, line, comment, preceding/succeeding code, and both probabilities plus confidence. Debt category, severity, security flag and AI suggestion remain null. NON-SATD predictions still appear in the unchanged POST DTO array but are not saved as findings.
- The required finding-to-`PullRequestFile` association was removed. Historical findings no longer depend on file rows replaced by PR detail sync. `debtCategory` was made nullable. `AnalysisRun` gained nullable `analyzedCandidateCount` (null while RUNNING/FAILED, 0 for completed empty analysis).
- A dedicated persistence service uses separate short `REQUIRES_NEW` transactions to create RUNNING, atomically save findings and COMPLETED, or mark FAILED. FastAPI calls occur outside those transactions.
- No migration framework exists. Development H2 is in-memory: restart Spring Boot to create a clean schema from the corrected entity mappings before running the modified application. `ddl-auto: update` must not be relied on to drop the old FK or NOT NULL category constraint in an existing durable database. Oracle cutover and durable migration are not part of this phase.
- Added backend integration tests with a mocked CodeBERT client. Focused tests passed (5/5). Full backend suite passed (14/14) on JDK 21. `mvn.cmd -q -DskipTests package` passed. The POST JSON array shape was checked by MockMvc. No real browser/database E2E persistence test was performed.
- No frontend, FastAPI, model, GitHub sync, OAuth/JWT issuance, HTTP client compatibility, Oracle configuration, history endpoints, or dashboard behavior changed.

## 27. Local H2 Console Security Configuration (2026-09-24)

- The H2 console configuration in `application.yml` is for local development. It now defaults to enabled at `/h2-console`; set `H2_CONSOLE_ENABLED=false` outside local development. The previous default of false with no environment override left the servlet unregistered and caused a `No static resource h2-console` response.
- When enabled, Spring Security permits `/h2-console/**` and sends `X-Frame-Options: SAMEORIGIN`, allowing the console's same-origin frames. The existing global CSRF disablement was already present and was not changed. Existing `/api/auth/**`, authenticated API, OAuth2 login and JWT filter configuration were retained.
- A focused MockMvc security test verified the same-origin header and that an unauthenticated normal API request was not successful. The complete backend test suite passed on JDK 21: 15 tests, 0 failures or errors.
- After the local-default correction, Spring Boot 3.3.4 on JDK 21 logged `H2ConsoleAutoConfiguration - H2 console available at '/h2-console'`. A live GET on a temporary port (18080, because 8080 was occupied) returned HTTP 200 with H2 login HTML and `X-Frame-Options: SAMEORIGIN`. Browser frame rendering on the user's 8080 process was not tested; that process needs a restart to load the corrected configuration.

## 28. PR Analysis History Read API and UI (2026-09-24)

- The user manually verified persistence for internal PR 19 in the running H2 database: one COMPLETED `ANALYSIS_RUNS` row with `analyzedCandidateCount = 7`, populated timestamps and `pullRequestId = 19`, and zero `SATD_FINDINGS` rows because the analyzed candidates were NON-SATD. This is user-reported verification, not a new agent-run browser test.
- Added owner-protected `GET /api/prs/{prId}/analysis-runs` (newest `startedAt` first, ID tie-breaker) and `GET /api/prs/{prId}/analysis-runs/{runId}`. Both use the existing authenticated GitHub ID to check PR ownership; detail also requires the run to belong to the requested PR. Missing/non-owned IDs receive not-found responses.
- List summaries report run ID, status, timestamps, nullable analyzed-candidate count and the count of persisted SATD findings for that run. Detail returns that summary plus saved SATD-only filename, line, comment, preceding/succeeding code and prediction probabilities/confidence. It does not invoke CodeBERT or read current PR file content.
- Angular PR Details now has an Analysis History tab with list/detail loading, empty and safe error states, distinct candidate/finding counts, and truthful completed-empty, zero-candidate, RUNNING and FAILED messages. A successful live analysis refreshes history independently of the live result; historical findings have no current-diff navigation.
- Focused backend persistence/history tests passed (8 tests), full backend tests passed (18 tests), and JDK 21 package check passed. Focused Angular PR Details and unified-diff tests passed (10 tests) in Chrome; Angular production build passed. Browser E2E of the new History UI was not performed.
- Existing POST `/analyze` DTO array contract, persistence schema, model/ML service, OAuth/JWT and GitHub integration were not changed in this phase.

## 29. Deterministic SATD Debt-Category Assessment (2026-09-24)

- CodeBERT remains a binary SATD/NON-SATD detector. A separate Spring Boot `DebtCategoryAssessmentService` now runs only after a real SATD prediction. This provides a downstream five-category product capability but deviates from the original Proposal's description of CodeBERT itself classifying debt categories.
- Versioned `category-rules-v1` rules require explicit comment evidence for DESIGN, DEFECT, TEST, REQUIREMENT or DOCUMENTATION. Generic TODO/FIXME/HACK, filename or JavaDoc syntax alone do not determine a category. No match or conflicting matches yields UNCLASSIFIED; path/nearby code can corroborate but cannot independently classify.
- A single assessment feeds the live response and immutable SATD finding snapshot. New assessed UNCLASSIFIED findings persist `debtCategory = null` with a reason and rule version. Legacy findings with null rule version appear as NOT_ASSESSED in history. NON-SATD results are not assessed or persisted as findings. If assessment throws, the binary result remains SATD and the category is left not assessed.
- Added nullable `category_reason` and `category_rule_version` columns to `SATDFinding`. Live and saved-history DTOs include category, reason and rule version; Angular shows categories only for SATD, including Unclassified and legacy Not Assessed. Severity and all other Proposal enhancements remain unimplemented.
- Focused category and persistence tests passed (18 tests total across those two classes). Full backend suite passed (28 tests) on JDK 21 and the backend package check passed. Focused Angular PR Details/diff tests passed (12 tests) in Chrome and the Angular production build passed. Browser E2E of category assessment was not performed.
- Development H2 is in-memory. A backend restart creates a fresh schema with the two nullable provenance columns and clears prior in-memory data. A durable database would require an explicit migration; none was added in this phase. FastAPI, trained model, OAuth/JWT, GitHub sync and severity behavior were unchanged.

## 30. Severity Infrastructure Phase A: PR Revision and Verified Source (2026-09-30)

- GitHub PR head/base SHAs are now mapped during PR list sync and stored as mutable `PullRequest.headSha/baseSha`. The revision associated with the synchronized file rows is stored separately as `syncedFilesHeadSha/syncedFilesBaseSha`; each `PullRequestFile.sha` remains the Git blob SHA, not a commit SHA.
- PR detail sync requests PR metadata before and after fetching all file pages (`per_page=100`, at most 30 pages). A short page ends pagination. Reaching 3,000 files without proof of completion, an API failure, or a changed head/base rejects the file snapshot before replacement. A short transactional replacement updates file rows and their associated revision; old rows survive a failed fetch/consistency check. Network calls are outside that replacement transaction.
- Analyze PR captures the stored file rows and their associated revision under a short database lock, then snapshots that revision on the new `AnalysisRun` as nullable `analyzedHeadSha/analyzedBaseSha`. Later PR head updates do not change old run values. Legacy file rows without revision metadata produce null run revision fields.
- A separate, currently optional `GithubSourceRetrievalService` can retrieve a Java file from the GitHub Contents API at an exact head SHA, cap the response/source size, decode Base64 UTF-8, and require the returned blob SHA to equal the stored file SHA. It reports verified, unavailable, mismatch, unsupported, API failure, or candidate-location mismatch states. Its per-analysis session caches by repository ID, head SHA, path and blob SHA. It is not invoked by normal Analyze PR yet, so source retrieval failure cannot affect binary SATD, category, persistence, or the current response.
- Current GitHub requests remain unauthenticated. Public-source retrieval can be exercised, but private repository access is not established. No OAuth/JWT changes or token storage were added. No live GitHub E2E retrieval/sync verification was performed; tests use mocked HTTP responses.
- Added four nullable revision columns on `PullRequest` and two nullable revision columns on `AnalysisRun`. No method metrics, severity, security scoring, Java parser, frontend, FastAPI or CodeBERT changes were made. Development in-memory H2 requires a backend restart to create a fresh schema, which clears its prior rows; durable databases would need an explicit migration not supplied here.
- Focused revision/source/persistence tests passed (28 tests across three classes). The full JDK 21 backend suite passed (44 tests), and the package check passed.

## 31. Severity Infrastructure Phase B: Java Method Metrics (2026-09-30)

- Added `javaparser-core` 3.26.4 and an independently callable `JavaMethodMetricsService` that accepts only `VerifiedSourceResult.Status.VERIFIED` complete source plus a candidate line. It does not fetch GitHub content and is not invoked by the normal Analyze PR path.
- A candidate is owned only when its line lies strictly between the containing method/constructor body brace lines. The innermost applicable method or constructor is selected. Comments before a declaration, class/field comments, and locations inside a lambda or class scope with no inner method return METHOD_NOT_FOUND. This conservative line-only rule also leaves comments on opening/closing brace lines unowned.
- `MethodMetricsResult` reports AVAILABLE with name, method/constructor kind, declaration line range, method length, complexity, and `method-metrics-v1`; unavailable states are METHOD_NOT_FOUND, PARSE_FAILED, SOURCE_POSITION_UNAVAILABLE, and UNSUPPORTED_SOURCE, without fabricated numeric defaults.
- Method length counts distinct physical body lines with Java code tokens, excluding blank/comment-only lines, brace-only lines and independent nested lambda/class/anonymous-class scopes. Inline code-plus-comment lines count. Complexity starts at 1 and adds one per `if`, traditional/enhanced `for`, `while`, `do-while`, `catch`, ternary, `&&`, `||`, and each non-default switch label. Default adds zero; classic, arrow and switch-expression cases were tested. Independent nested scopes do not add decisions to the outer method.
- Focused method-metrics tests passed (10 tests). The full JDK 21 backend suite passed (54 tests, no failures/errors/skips), including the Phase A revision/source/persistence tests; JDK 21 package check passed. These are local parser/backend tests, not live GitHub or severity E2E verification.
- No database schema, DTO, frontend, FastAPI, CodeBERT, category rule, OAuth/JWT, or production analysis-flow changes were made in Phase B. Severity 1–5 remains unimplemented. The parser is configured for Java 21; unsupported or malformed source can return an unavailable state, and line-only locations on brace lines are deliberately not assigned.
## 32. Severity Infrastructure Phase C: Deterministic SATD Severity (2026-09-30)

- SATD findings now attempt optional severity assessment after real binary CodeBERT inference and independent category assessment. NON-SATD results do not request source or receive category/severity assessment. One analysis-scoped `GithubSourceRetrievalService.Session` is reused across SATD candidates; exact analyzed head SHA, stored file blob SHA and candidate location are checked by that service before Java AST method metrics run.
- `risk-evidence-v1` matches explicit missing, bypassed, disabled, weak or inadequate authentication, authorization/access, input-validation/injection, cryptography, credential/secret or security-control wording in the SATD comment. Bare topic words are not evidence. This is wording evidence, not vulnerability or exploitability detection.
- `severity-rules-v1` is a deterministic project heuristic, not a Proposal-defined formula. It uses `method-metrics-v1` method length and complexity plus explicit risk wording. Score 1: length <=20 and complexity <=5 without risk; score 2: length >20 or complexity >5 with no higher rule; score 3: length >50 or complexity >10 or explicit risk with no higher rule; score 4: length >50 and complexity >10, or one elevated metric plus explicit risk; score 5: length >100 and complexity >20 plus explicit risk. Highest matching rule wins. Neither CodeBERT confidence nor debt category enters scoring.
- If revision/source/location/metrics are unavailable or an optional step throws, SATD and category remain valid and the finding is persisted with null severity score, a safe reason, and `severity-rules-v1` provenance. Live results distinguish ASSESSED, NOT_ASSESSED and NON-SATD NOT_APPLICABLE. Saved history derives LEGACY when rule version is null, NOT_ASSESSED when version is present but score is null, and ASSESSED otherwise; history reads saved fields only.
- Added nullable severity reason/version, method length/complexity/version and risk evidence/version columns to `SATDFinding`; the existing nullable severity score stores the assessment. Live POST DTO and saved history DTO expose the same snapshot evidence. Angular PR Details shows severity for live SATD and saved SATD, including distinct unavailable/legacy wording, without showing severity on NON-SATD cards.
- Focused risk/scoring/persistence tests passed (20 tests across three classes), and focused Angular PR Details/diff tests passed (13 tests in Chrome). Full backend suite passed on JDK 21 (61 tests, no failures/errors/skips); JDK 21 package and Angular production build passed. Phase A/B tests are included in the full suite. Browser/live GitHub severity E2E remains pending manual verification.
- Development H2 is in-memory: restart the backend to create the nullable columns, which clears existing in-memory rows. A durable database requires a migration not added here. Source retrieval remains unauthenticated, so private-repository severity support is not established. FastAPI, CodeBERT, category rules, OAuth/JWT and GitHub sync semantics were not changed.

## 33. User-Scoped SATD Dashboard (2026-09-30)

- The existing protected `GET /api/dashboard/summary` now returns user-scoped repository/PR/analysis counts, category and severity distributions, and repository analysis summaries. Its JWT validation and authentication flow were retained. No database schema change was required.
- Current dashboard findings come only from the latest COMPLETED `AnalysisRun` per owned PR, ordered by `completedAt` with run ID as a tie-breaker. Later FAILED or RUNNING runs do not replace completed data; older completed runs do not double-count. A completed run with zero SATD findings still counts as an analyzed PR, including one with zero analyzable candidates.
- Repository count and PR count are restricted to the authenticated GitHub user's selected projects. Current SATD count and distributions read saved finding snapshots only. Category null with a saved category-rule version is UNCLASSIFIED; null with no version is legacy NOT_ASSESSED. A null severity score is NOT_ASSESSED, never Severity 1. Latest analysis time is the latest completed timestamp among current completed runs.
- Angular Dashboard now shows Repositories, Pull Requests, Analyzed PRs, Current SATD Findings, latest completed analysis time, category/severity distributions, and per-repository PR/analysis/finding counts. Fixed Critical Issues, Tech Debt Score, Healthy status, and Health Score were removed. UI text states that counts describe latest completed PR-diff analyses, not full repository source-code inventory. Empty, loading, and safe error states are present.
- Focused backend dashboard integration tests passed (3 tests); full JDK 21 backend suite passed (64 tests, no failures/errors/skips), and the package check passed. Focused Angular dashboard tests passed (4 tests in Chrome), and the Angular production build passed. `git diff --check` passed after restoring the generated application log. Dashboard reads do not invoke CodeBERT, FastAPI, category or severity assessment, or GitHub source retrieval. Browser E2E of this dashboard remains pending.
