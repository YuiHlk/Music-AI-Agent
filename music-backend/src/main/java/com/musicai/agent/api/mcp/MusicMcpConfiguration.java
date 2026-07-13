package com.musicai.agent.api.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MusicMcpProperties.class)
@ConditionalOnProperty(prefix = "music-ai.mcp", name = "enabled", havingValue = "true")
public class MusicMcpConfiguration {

    static final String MCP_ENDPOINT = "/mcp";

    @Bean
    McpJsonMapper mcpJsonMapper(ObjectMapper objectMapper) {
        return new JacksonMcpJsonMapper(objectMapper);
    }

    @Bean
    HttpServletStreamableServerTransportProvider mcpTransportProvider(McpJsonMapper jsonMapper,
                                                                       MusicMcpProperties properties) {
        properties.requireValidConfiguration();
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint(MCP_ENDPOINT)
                .build();
    }

    @Bean
    ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            HttpServletStreamableServerTransportProvider transportProvider) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
                new ServletRegistrationBean<>(transportProvider, MCP_ENDPOINT);
        registration.setName("musicMcpServlet");
        registration.setAsyncSupported(true);
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean
    FilterRegistrationBean<McpBearerTokenFilter> mcpBearerTokenFilter(MusicMcpProperties properties,
                                                                       ObjectMapper objectMapper) {
        properties.requireValidConfiguration();
        FilterRegistrationBean<McpBearerTokenFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new McpBearerTokenFilter(properties.token(), objectMapper));
        registration.setName("musicMcpBearerTokenFilter");
        registration.addUrlPatterns(MCP_ENDPOINT);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean(destroyMethod = "closeGracefully")
    McpSyncServer musicMcpServer(HttpServletStreamableServerTransportProvider transportProvider,
                                 McpJsonMapper jsonMapper,
                                 MusicMcpToolCatalog toolCatalog) {
        return McpServer.sync(transportProvider)
                .jsonMapper(jsonMapper)
                .serverInfo("music-ai-agent", "0.1.0")
                .instructions("Create and evolve validated guitar scores through application-level tools. "
                        + "Generation and rewrite operations are asynchronous; poll get_generation_task.")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .tools(toolCatalog.specifications())
                .build();
    }
}
