package com.example.mcpserver.config;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ToolLoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(ToolLoggingAspect.class);

    @Around("@annotation(mcpTool)")
    public Object logToolExecution(ProceedingJoinPoint pjp, Tool mcpTool) throws Throwable {
        Instant start = Instant.now();
        String runId = UUID.randomUUID().toString();
        try {
            Object result = pjp.proceed();
            Duration duration = Duration.between(start, Instant.now());
            logger.info("mcp-tool completed",
                    org.slf4j.helpers.MessageFormatter.arrayFormat("tool={}, runId={}, durationMs={}",
                            new Object[] { mcpTool.name(), runId, duration.toMillis() }).getMessage());
            return result;
        }
        catch (Throwable ex) {
            Duration duration = Duration.between(start, Instant.now());
            logger.error("mcp-tool failed", ex);
            logger.info("mcp-tool summary: tool={}, runId={}, durationMs={} status=error", mcpTool.name(), runId,
                    duration.toMillis());
            throw ex;
        }
    }
}
