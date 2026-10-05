-- Manual demo-data reset only. Never run this on application startup.
-- Deletes all PR/history/finding/project data, while preserving users for login.
-- Un-comment the final users DELETE only when intentionally resetting user accounts too.
DELETE FROM satd_findings;
DELETE FROM analysis_runs;
DELETE FROM pull_request_comments;
DELETE FROM pull_request_files;
DELETE FROM pull_requests;
DELETE FROM github_projects;
-- DELETE FROM users;
COMMIT;
