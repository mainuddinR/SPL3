# Automated Code Review & Technical Debt Analyzer

A web application for reviewing GitHub pull requests and identifying self-admitted technical debt (SATD) in source-code comments. It combines a fine-tuned CodeBERT classifier with code metrics, design-pattern analysis, and GitHub review workflows.

## What the application does

- Sign in with GitHub and select repositories and pull requests.
- Sync changed files and review comments for a pull request.
- Detect SATD in comments using surrounding source-code context.
- Show confidence, category, severity, risk evidence, and analysis history.
- Inspect Java repositories for GoF and architectural design patterns, then check PR changes against a saved pattern profile.
- Generate optional AI-assisted suggestions and publish review feedback to GitHub.
- Display repository, PR, finding, and notification summaries.

## Technology

| Part | Technology |
| --- | --- |
| Frontend | Angular, TypeScript, Nginx |
| Backend | Spring Boot, Java 21, Spring Security, JPA |
| ML service | Python, FastAPI, fine-tuned CodeBERT |
| Database | Oracle Database XE (XEPDB1) |
| Integration | GitHub OAuth and REST APIs |
| Local deployment | Docker Compose |

## Repository structure

```text
backend/      Spring Boot API, security, analysis, persistence and tests
frontend/     Angular application and UI tests
ml-service/   FastAPI inference, CodeBERT training code and ML tests
tools/        Presentation and utility scripts
compose.yml   Local Docker services
```

The Oracle schema and migrations are under `backend/src/main/resources/db/oracle/`. The training pipeline is under `ml-service/codebert_training/`, and the final 30K PENTACET dataset description is in `ml-service/reports/pentacet_final_30k_dataset_report.md`.

## Local setup

The current Compose setup uses Oracle XE installed on the Windows host. Oracle is **not** started as a Docker container.

1. Install Docker Desktop and ensure Oracle XE/XEPDB1 is running.
2. Create the application schema in Oracle using `backend/src/main/resources/db/oracle/schema.sql` (fresh databases only).
3. Set local credentials in the ignored `.local/runtime-secrets.env` file, including `ORACLE_PASSWORD`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, and `JWT_SECRET`. The GitHub OAuth application must allow the callback URL used by the backend.
4. Place the trained checkpoint and tokenizer files in `ml-service/models/codebert_satd/best_model/`. Model weights are not tracked in Git.
5. Start the application from the repository root:

```powershell
docker compose up -d --build
```

Open **http://localhost:4200**. Docker exposes the Spring Boot API on localhost port 8080 and the ML service on localhost port 8001.

An optional `compose.public.yml` override is provided for the original presentation setup. Its hostname and GitHub callback must be configured for the environment before using it publicly.

## Tests

```powershell
cd backend
./mvnw.cmd test

cd ../frontend
npm ci
npm test

cd ../ml-service
python -m pytest tests
```

Backend tests include isolated H2-based tests; the running application uses Oracle. The ML service uses the trained CodeBERT checkpoint in Docker, rather than the mock classifier used by some local unit tests.

## Model and data

The deployed classifier uses a fine-tuned CodeBERT checkpoint and includes the comment together with preceding and succeeding code as its input. The 30K PENTACET subset used for training is described in the training package. Raw datasets, generated checkpoints, local credentials and machine-specific notes are intentionally not committed.

## Notes

- GitHub login, repository synchronization and review publication require an internet connection and appropriate GitHub permissions.
- AI-generated suggestions are optional and require a configured provider.
- Never commit access tokens, Oracle credentials, or trained model weights.
