-- =====================================================================
-- BugReplay — Execution Recording and Bug Investigation Tool
-- Database Schema for MySQL 8.x / 9.x
-- =====================================================================

-- 1. Create the database if it doesn't already exist
CREATE DATABASE IF NOT EXISTS bugreplay
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE bugreplay;

-- 2. Create the run_history table for recording metadata
-- Note: Full event logs and checkpoint states remain stored in JSON trace files.
-- This table stores indexable metadata for fast search, filtering, and history retrieval.
CREATE TABLE IF NOT EXISTS run_history (
    run_id VARCHAR(64) NOT NULL PRIMARY KEY,
    program_name VARCHAR(255) NOT NULL,
    source_path VARCHAR(500) DEFAULT '',
    recorded_at VARCHAR(64) NOT NULL,
    execution_status VARCHAR(32) NOT NULL,
    exit_code INT DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    event_count INT NOT NULL DEFAULT 0,
    checkpoint_count INT NOT NULL DEFAULT 0,
    trace_file_path VARCHAR(500) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_program_name (program_name),
    INDEX idx_recorded_at (recorded_at),
    INDEX idx_status (execution_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
