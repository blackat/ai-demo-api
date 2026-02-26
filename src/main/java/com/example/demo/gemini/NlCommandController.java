package com.example.demo.gemini;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/nl")
@Tag(name = "Natural Language", description = "Invoke REST API using natural language via Gemini")
public class NlCommandController {

    @Autowired
    private GeminiOrchestrator orchestrator;

    @PostMapping("/command")
    @Operation(
        summary = "Execute a natural language command",
        description = "Translates a natural language instruction into a REST API call using Gemini"
    )
    public ResponseEntity<Map<String, String>> command(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message field is required"));
        }

        try {
            String reply = orchestrator.process(message);
            return ResponseEntity.ok(Map.of("reply", reply));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
