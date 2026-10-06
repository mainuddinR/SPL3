-- Apply once as SPL3_APP to the existing Oracle schema before deploying the updated backend.
-- Preserves existing analysis runs and findings. Do not run schema.sql again.
ALTER TABLE analysis_runs ADD (
    publication_status VARCHAR2(255 CHAR) DEFAULT 'NOT_PUBLISHED' NOT NULL,
    github_review_id NUMBER(19,0),
    github_review_url VARCHAR2(255 CHAR),
    review_published_at TIMESTAMP(6),
    published_head_sha VARCHAR2(255 CHAR),
    publication_failure VARCHAR2(255 CHAR)
);
