package com.musicai.agent.infrastructure.persistence;

import com.musicai.agent.application.port.ProjectStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.sql.init.mode=always")
@Testcontainers(disabledWithoutDocker = true)
class MySqlProjectStoreTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("music_ai")
            .withUsername("music_ai")
            .withPassword("music_ai_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    ProjectStore store;

    @Test
    void initializesSchemaAndPersistsProjectLifecycleOnMySql8() {
        ProjectStore.StoredProject project = store.createProject("MySQL persistence");
        ProjectStore.StoredTask task = store.createTask(project.id(), "Generate a riff");

        int version = store.saveVersion(project.id(), "{\"title\":\"test\"}");
        store.updateTask(task.id(), "COMPLETED", null);

        assertThat(version).isEqualTo(1);
        assertThat(store.findProject(project.id())).get()
                .extracting(ProjectStore.StoredProject::currentVersion).isEqualTo(1);
        assertThat(store.findVersion(project.id(), 1)).isPresent();
        assertThat(store.findTask(task.id())).get()
                .extracting(ProjectStore.StoredTask::status).isEqualTo("COMPLETED");
        assertThat(store.findProjects(10)).extracting(ProjectStore.StoredProject::id).contains(project.id());
    }
}
