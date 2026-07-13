create table if not exists music_project (
    id varchar(36) primary key,
    title varchar(200) not null,
    status varchar(40) not null,
    current_version integer not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists project_version (
    id varchar(36) primary key,
    project_id varchar(36) not null,
    version_number integer not null,
    score_json longtext not null,
    created_at timestamp not null,
    constraint fk_version_project foreign key (project_id) references music_project(id),
    constraint uk_project_version unique (project_id, version_number)
);

create table if not exists generation_task (
    id varchar(36) primary key,
    project_id varchar(36) not null,
    status varchar(40) not null,
    prompt longtext not null,
    error_message varchar(1000),
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_task_project foreign key (project_id) references music_project(id)
);

create table if not exists conversation_message (
    id varchar(36) primary key,
    project_id varchar(36) not null,
    role varchar(20) not null,
    content longtext not null,
    created_at timestamp not null,
    constraint fk_message_project foreign key (project_id) references music_project(id)
);

create table if not exists exported_artifact (
    id varchar(36) primary key,
    project_id varchar(36) not null,
    version_number integer not null,
    artifact_type varchar(20) not null,
    storage_path varchar(500) not null,
    created_at timestamp not null,
    constraint fk_artifact_project foreign key (project_id) references music_project(id)
);
