CREATE DATABASE IF NOT EXISTS music_ai CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE music_ai;

CREATE TABLE IF NOT EXISTS music_project (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    current_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS project_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    version_number INT NOT NULL,
    score_json LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_project_version UNIQUE (project_id, version_number),
    CONSTRAINT fk_version_project FOREIGN KEY (project_id) REFERENCES music_project(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS generation_task (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    message VARCHAR(1000),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_task_project FOREIGN KEY (project_id) REFERENCES music_project(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS conversation_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_message_project FOREIGN KEY (project_id) REFERENCES music_project(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS exported_artifact (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    version_number INT NOT NULL,
    artifact_type VARCHAR(32) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_artifact_project FOREIGN KEY (project_id) REFERENCES music_project(id)
) ENGINE=InnoDB;
