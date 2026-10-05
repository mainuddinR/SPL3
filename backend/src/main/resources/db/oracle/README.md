# Oracle cutover (Oracle Database 19c or newer)

This directory defines a **fresh, empty** schema for the current JPA entities. It does not import the previous in-memory H2 data. Use a dedicated Oracle application user with permission to create tables and indexes in its own schema. Do not run `schema.sql` against a schema containing application data; it is not an incremental migration.

1. Start Oracle and its listener, then create or select a dedicated application user/schema. Grant `CREATE SESSION`, `CREATE TABLE`, `CREATE SEQUENCE` (required by the identity columns), and sufficient tablespace quota to that user. It can index its own tables. Keep administrator and application passwords outside this repository.
2. Connect as that user with SQLcl or SQL*Plus and run `@schema.sql` from this directory. The seven tables, foreign keys, checks, identity columns, and indexes are created once.
3. Set `SPRING_PROFILES_ACTIVE=oracle`, `ORACLE_HOST`, `ORACLE_PORT`, `ORACLE_SERVICE`, `ORACLE_USER`, `ORACLE_PASSWORD`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `JWT_SECRET` (at least 32 random bytes for the JWT HMAC key), and `ML_SERVICE_URL`. Set optional `LLM_*`, `OPENAI_API_KEY`, and `OPENAI_MODEL` only when using suggestions. Start Spring Boot under JDK 21. The Oracle profile uses `ddl-auto: validate`; it will fail startup if the schema is missing or incompatible.
4. Sign in, save a repository, sync a PR and its details, and analyze it. If a saved SATD finding exists and LLM access is configured, generate a suggestion from Analysis History. Stop and restart Spring Boot with the same Oracle environment, then verify that the user, project, PR, run, finding, and any suggestion remain.

Read-only verification queries (run as the application user):

```sql
SELECT id, github_id, username, created_at FROM users ORDER BY id;
SELECT id, user_id, github_repo_id, full_name FROM github_projects ORDER BY id;
SELECT id, project_id, github_pr_id, pr_number, state FROM pull_requests ORDER BY id;
SELECT id, pull_request_id, status, completed_at, analyzed_candidate_count FROM analysis_runs ORDER BY id;
SELECT id, analysis_run_id, filename, line_number, debt_category, severity_score,
       CASE WHEN ai_suggestion IS NULL THEN 'NO' ELSE 'YES' END AS has_suggestion
FROM satd_findings ORDER BY id;
```

`reset-data.sql` is an optional **manual** demo-data reset. It deletes findings, runs, comments, files, PRs, and projects in foreign-key order, preserving users by default. Review it before running it. The application never invokes it or purges data automatically.

The H2 fallback is selected explicitly with `SPRING_PROFILES_ACTIVE=h2`; it uses an in-memory database and local H2 console. H2 tests use their own isolated configuration. No active profile is committed into `application.yml`.
Starting the normal application without exactly one of `h2` or `oracle` is rejected, avoiding Spring Boot's implicit embedded-H2 fallback.
