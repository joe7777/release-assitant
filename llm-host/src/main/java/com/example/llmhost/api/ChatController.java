package com.example.llmhost.api;

import com.example.llmhost.service.ToolCallingChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class ChatController {

    private final ToolCallingChatService chatService;

    public ChatController(ToolCallingChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping({"/chat", "/runs"})
    public ChatRunResponse run(@Valid @RequestBody ChatRequest request) {
        return chatService.run(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleValidationError(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}
