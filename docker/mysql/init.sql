CREATE DATABASE IF NOT EXISTS music_ai CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE music_ai;

CREATE TABLE IF NOT EXISTS music_project (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(40) NOT NULL,
    current_version INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS project_version (
    id VARCHAR(36) PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL,
    version_number INT NOT NULL,
    score_json LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_project_version UNIQUE (project_id, version_number),
    CONSTRAINT fk_version_project FOREIGN KEY (project_id) REFERENCES music_project(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS generation_task (
    id VARCHAR(36) PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL,
    status VARCHAR(40) NOT NULL,
    prompt LONGTEXT NOT NULL,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_task_project FOREIGN KEY (project_id) REFERENCES music_project(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS conversation_message (
    id VARCHAR(36) PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_message_project FOREIGN KEY (project_id) REFERENCES music_project(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS exported_artifact (
    id VARCHAR(36) PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL,
    version_number INT NOT NULL,
    artifact_type VARCHAR(20) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_artifact_project FOREIGN KEY (project_id) REFERENCES music_project(id)
) ENGINE=InnoDB;
