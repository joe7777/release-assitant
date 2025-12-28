package com.example.llmhost.service;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

@Service
public class McpToolRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpToolRegistry.class);
    private static final List<String> MCP_CLIENT_CLASS_NAMES = List.of(
            "org.springframework.ai.mcp.client.McpClient",
            "org.springframework.ai.mcp.client.McpSyncClient",
            "org.springframework.ai.mcp.client.sync.McpSyncClient"
    );
    private static final List<String> MCP_TOOL_CALLBACK_CLASS_NAMES = List.of(
            "org.springframework.ai.mcp.client.tool.McpToolCallback",
            "org.springframework.ai.mcp.client.McpToolCallback",
            "org.springframework.ai.mcp.client.sync.McpToolCallback"
    );

    private final ApplicationContext applicationContext;
    private final String mcpServerUrl;
    private final CopyOnWriteArrayList<ToolCallback> toolCallbacks = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> toolNames = new CopyOnWriteArrayList<>();

    public McpToolRegistry(ApplicationContext applicationContext,
            @Value("${spring.ai.mcp.client.transport.streamable-http.url:}") String mcpServerUrl) {
        this.applicationContext = applicationContext;
        this.mcpServerUrl = mcpServerUrl;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadToolsOnStartup() {
        reloadTools();
    }

    public List<ToolCallback> getToolCallbacks() {
        return toolCallbacks;
    }

    public List<String> getToolNames() {
        return Collections.unmodifiableList(toolNames);
    }

    public synchronized List<ToolCallback> reloadTools() {
        Object mcpClient = resolveMcpClient();
        if (mcpClient == null) {
            LOGGER.warn("MCP client not configured, skipping tool discovery.");
            toolCallbacks.clear();
            toolNames.clear();
            return toolCallbacks;
        }

        Instant start = Instant.now();
        try {
            List<ToolDefinition> definitions = listTools(mcpClient);
            List<ToolCallback> callbacks = definitions.stream()
                    .map(definition -> createToolCallback(mcpClient, definition))
                    .filter(Objects::nonNull)
                    .toList();
            List<String> names = definitions.stream()
                    .map(ToolDefinition::name)
                    .filter(StringUtils::hasText)
                    .toList();

            toolCallbacks.clear();
            toolCallbacks.addAll(callbacks);
            toolNames.clear();
            toolNames.addAll(names);

            LOGGER.info("Loaded {} MCP tools from {}: {} ({} ms)", callbacks.size(), mcpServerUrl, names,
                    Duration.between(start, Instant.now()).toMillis());
            return toolCallbacks;
        }
        catch (Exception ex) {
            toolCallbacks.clear();
            toolNames.clear();
            LOGGER.warn("Failed to load MCP tools from {}: {}", mcpServerUrl, ex.getMessage());
            return toolCallbacks;
        }
    }

    private Object resolveMcpClient() {
        for (String className : MCP_CLIENT_CLASS_NAMES) {
            try {
                Class<?> clientClass = ClassUtils.forName(className, applicationContext.getClassLoader());
                Object client = applicationContext.getBeanProvider(clientClass).getIfAvailable();
                if (client != null) {
                    return client;
                }
            }
            catch (ClassNotFoundException ex) {
                // Ignore: not available for this Spring AI version.
            }
        }
        return null;
    }

    private List<ToolDefinition> listTools(Object mcpClient) {
        try {
            Method listTools = mcpClient.getClass().getMethod("listTools");
            Object response = listTools.invoke(mcpClient);
            if (response instanceof List<?> tools) {
                return tools.stream()
                        .filter(ToolDefinition.class::isInstance)
                        .map(ToolDefinition.class::cast)
                        .toList();
            }
        }
        catch (ReflectiveOperationException ex) {
            LOGGER.warn("Unable to list MCP tools: {}", ex.getMessage());
        }
        return List.of();
    }

    private ToolCallback createToolCallback(Object mcpClient, ToolDefinition definition) {
        for (String className : MCP_TOOL_CALLBACK_CLASS_NAMES) {
            try {
                Class<?> callbackClass = ClassUtils.forName(className, applicationContext.getClassLoader());
                if (!ToolCallback.class.isAssignableFrom(callbackClass)) {
                    continue;
                }
                Constructor<?> constructor = findCallbackConstructor(callbackClass, mcpClient.getClass());
                if (constructor != null) {
                    return (ToolCallback) constructor.newInstance(mcpClient, definition);
                }
            }
            catch (ClassNotFoundException ex) {
                // Ignore: not available for this Spring AI version.
            }
            catch (ReflectiveOperationException ex) {
                LOGGER.warn("Unable to create MCP tool callback using {}: {}", className, ex.getMessage());
            }
        }
        LOGGER.warn("No compatible MCP tool callback implementation found, skipping tool registration.");
        return null;
    }

    private Constructor<?> findCallbackConstructor(Class<?> callbackClass, Class<?> clientClass) {
        for (Constructor<?> constructor : callbackClass.getConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length != 2) {
                continue;
            }
            if (!parameterTypes[0].isAssignableFrom(clientClass)) {
                continue;
            }
            if (!parameterTypes[1].isAssignableFrom(ToolDefinition.class)) {
                continue;
            }
            return constructor;
        }
        return null;
    }
}
