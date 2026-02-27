package com.example.demo.nl;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/nl")
@Tag(name = "Natural Language", description = "Invoke REST API using natural language")
public class NlController {

    @Autowired
    private NlOrchestrator orchestrator;

    @PostMapping("/command")
    @Operation(
        summary = "Execute a natural language command",
        description = "Translates natural language into a REST API call using the configured LLM provider (gemini-vertex, gemini-free, or ollama)"
    )
    public ResponseEntity<Map<String, String>> command(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message field is required"));
        }
        try {
            return ResponseEntity.ok(Map.of("reply", orchestrator.process(message)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
