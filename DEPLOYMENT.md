# Local application deployment

Oracle XE 21c stays installed on Windows. Docker Compose runs only Angular, Spring Boot, and the real CodeBERT FastAPI service; Oracle data is never stored in a container.

Prerequisites:

- Docker Desktop with Linux containers running.
- Windows services `OracleServiceXE` and `OracleOraDB21Home2TNSListener` running; XEPDB1 available with the seven-table `SPL3_APP` schema already deployed.
- The ignored `.local/runtime-secrets.env` populated with Oracle, GitHub OAuth, and JWT values. Never commit this file.
- The local checkpoint directory `ml-service/models/codebert_satd/best_model` containing `model.safetensors` and tokenizer files. Compose mounts it read-only into the ML container; the image does not contain model weights.

Stop any native Angular, Spring Boot, or FastAPI processes using ports 4200, 8080, or 8001. From the project root, run `docker compose up --build`, then open <http://localhost:4200>. The backend publishes port 8080, FastAPI publishes port 8001, and the GitHub OAuth callback remains `http://localhost:8080/login/oauth2/code/github`. Angular's browser requests continue to use `http://localhost:8080`; the backend reaches FastAPI through `http://ml-service:8001`.

Compose initially uses `host.docker.internal` for the native Oracle listener on port 1521. Verify that address from Docker before relying on the stack. If the listener is reachable only through the Windows LAN address, change only `ORACLE_HOST` in `compose.yml` to the verified address. Do not change the database schema or OAuth flow for this routing issue.

Stop the application containers with `docker compose down`. Oracle remains running and its rows persist. The ignored `.local/start-stack.ps1` and `.local/stop-stack.ps1` provide the same start/stop commands with an Oracle-service check before startup.
