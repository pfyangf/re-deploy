-- V001: add detail_logs column to deploy_history for per-deploy detailed logs
ALTER TABLE deploy_history ADD COLUMN detail_logs TEXT;
