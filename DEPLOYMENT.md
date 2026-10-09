# Local application deployment

Oracle XE 21c stays installed on Windows. Docker Compose runs only Angular, Spring Boot, and the real CodeBERT FastAPI service; Oracle data is never stored in a container.

Prerequisites:

- Docker Desktop with Linux containers running.
- Windows services `OracleServiceXE` and `OracleOraDB21Home2TNSListener` running; XEPDB1 available with the seven-table `SPL3_APP` schema already deployed.
- The ignored `.local/runtime-secrets.env` populated with Oracle, GitHub OAuth, and JWT values. Never commit this file.
- The local checkpoint directory `ml-service/models/codebert_satd/best_model` containing `model.safetensors` and tokenizer files. Compose mounts it read-only into the ML container; the image does not contain model weights.

Stop any native Angular, Spring Boot, or FastAPI processes using ports 4200, 8080, or 8001. From the project root, run `docker compose up --build`, then open <http://localhost:4200>. The backend publishes port 8080, FastAPI publishes port 8001, and the GitHub OAuth callback remains `http://localhost:8080/login/oauth2/code/github`. Angular's browser requests continue to use `http://localhost:8080`; the backend reaches FastAPI through `http://ml-service:8001`.

For presentations on this same Windows laptop, use a Wi-Fi-independent local connection: Oracle `listener.ora` binds TCP port 1521 to `127.0.0.1`, its `tnsnames.ora` entries `XE` and `LISTENER_XE` use `127.0.0.1`, and Compose sets `ORACLE_HOST: host.docker.internal`. Docker Desktop can forward `host.docker.internal` to a Windows localhost listener (verified with a temporary loopback TCP service). Do **not** put the current Wi-Fi/LAN address in Compose or expose Oracle on `0.0.0.0` on a university network.

On 2026-10-09 the first listener restart left `XEPDB1` unregistered because Oracle `LOCAL_LISTENER` still pointed at the `LISTENER_XE` alias. The running Oracle instance was repaired with `ALTER SYSTEM SET LOCAL_LISTENER = '(ADDRESS=(PROTOCOL=TCP)(HOST=127.0.0.1)(PORT=1521))' SCOPE=BOTH;` followed by `ALTER SYSTEM REGISTER;` (SQL*Plus OS authentication as SYSDBA). `lsnrctl status` then reported `xepdb1` READY. Docker reached the listener via `host.docker.internal:1521`, and the recreated Spring Boot backend obtained an Oracle JDBC connection; Angular and ML health checks passed. No schema/data reset was needed. The local repair script now includes a manual registration fallback when delayed.

After preparing the files, an Administrator PowerShell window may run `.local/finish-wifi-fix.ps1` to apply/revalidate the setup. It restarts the Oracle listener only, checks for the `XEPDB1` service, verifies that the ML container can reach Oracle TCP 1521, and recreates only the Spring backend with the updated environment. It does not reset schema/data or change the CodeBERT model. Opening `http://localhost:4200` on the presentation laptop remains independent of the Wi-Fi IP, but GitHub OAuth, repository synchronization and LLM guidance still require suitable internet connectivity. Reconnect GitHub after backend restart if needed. Backups of the original Oracle and Compose configs are in the ignored `.local` directory.

Stop the application containers with `docker compose down`. Oracle remains running and its rows persist. The ignored `.local/start-stack.ps1` and `.local/stop-stack.ps1` provide the same start/stop commands with an Oracle-service check before startup.
