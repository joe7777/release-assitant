package com.example.llmhost.api;

import com.example.llmhost.service.ToolCallingChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Chat", description = "Manage chat runs with tool calling support")
@RequestMapping
public class ChatController {

    private final ToolCallingChatService chatService;

    public ChatController(ToolCallingChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping({"/chat", "/runs"})
    @Operation(summary = "Execute a chat run", description = "Routes the chat request through the configured LLM and MCP tools")
    public ChatRunResponse run(@Valid @RequestBody ChatRequest request) {
        return chatService.run(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleValidationError(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}
