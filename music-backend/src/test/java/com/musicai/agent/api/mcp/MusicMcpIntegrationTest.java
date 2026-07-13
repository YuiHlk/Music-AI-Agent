package com.musicai.agent.api.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:music-mcp;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "music-ai.export-directory=${java.io.tmpdir}/music-ai-mcp-integration",
        "music-ai.mcp.enabled=true",
        "music-ai.mcp.token=test-mcp-token-1234567890"
})
class MusicMcpIntegrationTest {

    private static final String TOKEN = "test-mcp-token-1234567890";
    private static final String ACCEPT = "application/json, text/event-stream";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void rejectsRequestsWithoutBearerToken() throws Exception {
        HttpResponse<String> response = httpClient.send(baseRequest(initializeRequest()).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue(HttpHeaders.WWW_AUTHENTICATE)).contains("Bearer");
        assertThat(objectMapper.readTree(response.body()).path("code").asText())
                .isEqualTo("MCP_AUTHENTICATION_REQUIRED");
    }

    @Test
    void exposesACompleteProjectGenerationWorkflow() throws Exception {
        HttpResponse<String> initialize = send(initializeRequest(), null);
        assertThat(initialize.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(initialize.body()).path("result").path("serverInfo").path("name").asText())
                .isEqualTo("music-ai-agent");
        String sessionId = initialize.headers().firstValue("mcp-session-id").orElseThrow();

        HttpResponse<String> initialized = send("""
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """, sessionId);
        assertThat(initialized.statusCode()).isEqualTo(202);

        JsonNode listedTools = result(send(request(2, "tools/list", "{}"), sessionId));
        Set<String> toolNames = StreamSupport.stream(listedTools.path("tools").spliterator(), false)
                .map(tool -> tool.path("name").asText())
                .collect(Collectors.toSet());
        assertThat(toolNames).containsExactlyInAnyOrder(
                "create_music_project", "generate_guitar_riff", "get_generation_task", "get_score_summary",
                "validate_score", "list_artifacts", "rewrite_measures", "rollback_project");

        JsonNode missingProject = callTool(sessionId, 30, "get_score_summary",
                "{\"projectId\":\"missing-project\"}");
        assertThat(missingProject.path("isError").asBoolean()).isTrue();
        assertThat(missingProject.path("structuredContent").path("code").asText()).isEqualTo("NOT_FOUND");

        JsonNode createResult = callTool(sessionId, 3, "create_music_project", """
                {"title":"MCP Integration Riff"}
                """);
        assertThat(createResult.path("isError").asBoolean()).isFalse();
        String projectId = createResult.path("structuredContent").path("id").asText();
        assertThat(projectId).isNotBlank();

        JsonNode generateResult = callTool(sessionId, 4, "generate_guitar_riff", """
                {"projectId":"%s","prompt":"生成一段8小节、120 BPM、E小调、标准调弦的摇滚吉他Riff"}
                """.formatted(projectId));
        String taskId = generateResult.path("structuredContent").path("id").asText();
        assertThat(taskId).isNotBlank();

        JsonNode task = waitForCompletedTask(sessionId, projectId, taskId);
        assertThat(task.path("structuredContent").path("status").asText()).isEqualTo("COMPLETED");

        JsonNode score = callTool(sessionId, 20, "get_score_summary",
                "{\"projectId\":\"" + projectId + "\"}");
        assertThat(score.path("structuredContent").path("tempo").asInt()).isEqualTo(120);
        assertThat(score.path("structuredContent").path("measureCount").asInt()).isEqualTo(8);

        JsonNode validation = callTool(sessionId, 21, "validate_score",
                "{\"projectId\":\"" + projectId + "\"}");
        assertThat(validation.path("structuredContent").path("valid").asBoolean()).isTrue();

        JsonNode artifacts = callTool(sessionId, 22, "list_artifacts",
                "{\"projectId\":\"" + projectId + "\"}");
        assertThat(artifacts.path("structuredContent").path("artifacts")).hasSize(2);
        assertThat(artifacts.toString()).doesNotContain("storagePath", "\\\\", "/tmp/");

        HttpResponse<String> close = httpClient.send(HttpRequest.newBuilder(endpoint())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .header("mcp-session-id", sessionId)
                        .DELETE().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(close.statusCode()).isEqualTo(200);
    }

    private JsonNode waitForCompletedTask(String sessionId, String projectId, String taskId) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            JsonNode result = callTool(sessionId, 10 + attempt, "get_generation_task",
                    "{\"taskId\":\"" + taskId + "\"}");
            String status = result.path("structuredContent").path("status").asText();
            if (status.equals("COMPLETED")) {
                return result;
            }
            if (status.equals("FAILED")) {
                throw new AssertionError("MCP generation failed for project " + projectId + ": " + result);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("MCP generation did not complete in time for project " + projectId);
    }

    private JsonNode callTool(String sessionId, int id, String toolName, String argumentsJson) throws Exception {
        String params = "{\"name\":\"" + toolName + "\",\"arguments\":" + argumentsJson + "}";
        return result(send(request(id, "tools/call", params), sessionId));
    }

    private JsonNode result(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        String data = response.body().lines()
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring("data:".length()).trim())
                .findFirst()
                .orElse(response.body());
        JsonNode rpcResponse = objectMapper.readTree(data);
        assertThat(rpcResponse.path("error").isMissingNode()).isTrue();
        return rpcResponse.path("result");
    }

    private HttpResponse<String> send(String body, String sessionId) throws Exception {
        HttpRequest.Builder request = baseRequest(body)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
        if (sessionId != null) {
            request.header("mcp-session-id", sessionId);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder baseRequest(String body) {
        return HttpRequest.newBuilder(endpoint())
                .timeout(Duration.ofSeconds(10))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.ACCEPT, ACCEPT)
                .POST(HttpRequest.BodyPublishers.ofString(body));
    }

    private URI endpoint() {
        return URI.create("http://localhost:" + port + "/mcp");
    }

    private static String initializeRequest() {
        return """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"integration-test","version":"1.0"}}}
                """;
    }

    private static String request(int id, String method, String params) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method
                + "\",\"params\":" + params + "}";
    }
}
