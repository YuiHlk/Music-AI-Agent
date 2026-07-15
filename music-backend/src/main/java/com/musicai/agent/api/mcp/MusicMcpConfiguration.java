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

/**
 * 在显式启用时组装 MCP HTTP transport、认证过滤器、同步服务器和音乐工具目录。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MusicMcpProperties.class)
@ConditionalOnProperty(prefix = "music-ai.mcp", name = "enabled", havingValue = "true")
public class MusicMcpConfiguration {

    /** MCP Streamable HTTP servlet 路径。 */
    static final String MCP_ENDPOINT = "/mcp";

    /**
     * @param objectMapper 应用 JSON mapper
     * @return MCP SDK 的 Jackson 适配器
     */
    @Bean
    McpJsonMapper mcpJsonMapper(ObjectMapper objectMapper) {
        return new JacksonMcpJsonMapper(objectMapper);
    }

    /**
     * @param jsonMapper MCP JSON mapper
     * @param properties 已验证的 MCP 配置
     * @return 绑定固定端点的 Streamable HTTP transport
     */
    @Bean
    HttpServletStreamableServerTransportProvider mcpTransportProvider(McpJsonMapper jsonMapper,
                                                                       MusicMcpProperties properties) {
        properties.requireValidConfiguration();
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint(MCP_ENDPOINT)
                .build();
    }

    /**
     * 注册支持异步请求的 MCP transport servlet。
     * @param transportProvider transport 实现
     * @return servlet 注册信息
     */
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

    /**
     * 将 Bearer 认证置于 MCP servlet 之前。
     * @param properties MCP token 配置
     * @param objectMapper 错误响应序列化器
     * @return 最高优先级过滤器注册信息
     */
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

    /**
     * 创建同步 MCP 服务器并注册工具；生成和改写仍返回需轮询的异步任务。
     * @param transportProvider HTTP transport
     * @param jsonMapper MCP JSON mapper
     * @param toolCatalog 音乐工具目录
     * @return 支持优雅关闭的 MCP server
     */
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
