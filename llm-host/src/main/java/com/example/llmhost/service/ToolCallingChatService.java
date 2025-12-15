package com.example.llmhost.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.llmhost.api.ChatRequest;
import com.example.llmhost.api.ChatRunResponse;
import com.example.llmhost.api.ToolCallTrace;
import com.example.llmhost.config.AppProperties;
import com.example.llmhost.config.AppProperties.ToolingProperties;
import com.example.llmhost.config.SystemPromptProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

@Service
public class ToolCallingChatService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolCallingChatService.class);
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*?\\}", Pattern.MULTILINE);

    private final ChatClient chatClient;
    private final SystemPromptProvider systemPromptProvider;
    private final AppProperties properties;
    private final List<ToolCallback> functionCallbacks;

    public ToolCallingChatService(ChatClient chatClient, SystemPromptProvider systemPromptProvider, AppProperties properties,
            List<ToolCallback> functionCallbacks) {
        this.chatClient = chatClient;
        this.systemPromptProvider = systemPromptProvider;
        this.properties = properties;
        this.functionCallbacks = new ArrayList<>(functionCallbacks);
    }

    public ChatRunResponse run(ChatRequest request) {
        validatePrompt(request);
        List<ToolCallTrace> traces = new ArrayList<>();
        List<ToolCallback> callbacks = shouldUseTools(request)
                ? wrapCallbacks(traces)
                : Collections.emptyList();

        var response = chatClient.prompt()
                .system(systemPromptProvider.buildSystemPrompt())
                .user(request.prompt())
                .tools(callbacks)
                .call();

        String content = response.content();
        String json = extractFirstJson(content);

        return new ChatRunResponse(content, json, traces, shouldUseTools(request));
    }

    private boolean shouldUseTools(ChatRequest request) {
        return !properties.getTooling().isDryRun() && !request.dryRun();
    }

    private void validatePrompt(ChatRequest request) {
        int maxLength = properties.getTooling().getMaxPromptLength();
        if (StringUtils.hasLength(request.prompt()) && request.prompt().length() > maxLength) {
            throw new IllegalArgumentException("Prompt too long. Limit is " + maxLength + " characters");
        }
    }

    private List<ToolCallback> wrapCallbacks(List<ToolCallTrace> traces) {
        ToolingProperties tooling = properties.getTooling();
        AtomicInteger counter = new AtomicInteger();
        return functionCallbacks.stream()
                .map(delegate -> LoggingFunctionCallback.wrap(delegate, counter, tooling.getMaxToolCalls(), traces))
                .map(ToolCallback.class::cast)
                .toList();
    }

    private String extractFirstJson(String content) {
        Matcher matcher = JSON_BLOCK.matcher(content);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private static class LoggingFunctionCallback implements InvocationHandler {

        private static final List<String> CALL_METHODS = List.of("call", "execute");

        private final ToolCallback delegate;
        private final AtomicInteger counter;
        private final int maxCalls;
        private final List<ToolCallTrace> traces;
        private final Supplier<String> toolNameSupplier;

        private LoggingFunctionCallback(ToolCallback delegate, AtomicInteger counter, int maxCalls,
                List<ToolCallTrace> traces) {
            this.delegate = delegate;
            this.counter = counter;
            this.maxCalls = maxCalls;
            this.traces = traces;
            this.toolNameSupplier = memoize(this::resolveToolName);
        }

        static ToolCallback wrap(ToolCallback delegate, AtomicInteger counter, int maxCalls, List<ToolCallTrace> traces) {
            var handler = new LoggingFunctionCallback(delegate, counter, maxCalls, traces);
            return (ToolCallback) Proxy.newProxyInstance(delegate.getClass().getClassLoader(),
                    new Class<?>[] { ToolCallback.class }, handler);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (CALL_METHODS.contains(method.getName())) {
                int current = counter.incrementAndGet();
                if (current > maxCalls) {
                    throw new IllegalStateException("Maximum tool calls exceeded (" + maxCalls + ")");
                }
                Instant start = Instant.now();
                try {
                    LOGGER.info("Tool call #{} - {}", current, toolNameSupplier.get());
                    Object response = method.invoke(delegate, args);
                    traces.add(new ToolCallTrace(toolNameSupplier.get(), args != null && args.length > 0
                            ? String.valueOf(args[0]) : "", Duration.between(start, Instant.now()).toMillis(), true, null));
                    return response;
                }
                catch (Exception ex) {
                    traces.add(new ToolCallTrace(toolNameSupplier.get(), args != null && args.length > 0
                            ? String.valueOf(args[0]) : "", Duration.between(start, Instant.now()).toMillis(), false,
                            ex.getMessage()));
                    throw ex;
                }
            }
            return method.invoke(delegate, args);
        }

        private String resolveToolName() {
            return Stream.<Supplier<String>>of(
                    () -> invokeIfPresent(delegate, "getToolDefinition", def -> invokeIfPresent(def, "getName", Objects::toString)),
                    () -> invokeIfPresent(delegate, "getName", Objects::toString))
                    .map(Supplier::get)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(delegate.getClass().getSimpleName());
        }

        private static <T> T invokeIfPresent(Object target, String methodName, java.util.function.Function<Object, T> mapper) {
            try {
                Method method = target.getClass().getMethod(methodName);
                Object result = method.invoke(target);
                return result == null ? null : mapper.apply(result);
            }
            catch (Exception ex) {
                return null;
            }
        }

        private static <T> Supplier<T> memoize(Supplier<T> supplier) {
            return new Supplier<>() {
                private boolean initialized;
                private T value;

                @Override
                public T get() {
                    if (!initialized) {
                        value = supplier.get();
                        initialized = true;
                    }
                    return value;
                }
            };
        }
    }
}
