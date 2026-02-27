package com.example.demo.nl.strategy;

import com.example.demo.nl.config.LlmProperties;
import com.example.demo.nl.converter.OpenApiConverter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * LLM strategy for Gemini Free via Google AI Studio REST API (no Google Cloud project needed).
 *
 * Free tier: 15 requests/min, 1500 requests/day.
 * Get your API key at: https://aistudio.google.com/app/apikey
 *
 * Requires:
 *   llm.provider=gemini-free
 *   llm.gemini-api-key=YOUR_API_KEY
 *
 * Uses the Gemini REST API directly (no SDK) — OpenAI-compatible tool format.
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "gemini-free")
public class GeminiFreeStrategy implements LlmStrategy {

    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    @Autowired private OpenApiConverter converter;
    @Autowired private LlmProperties    props;
    @Autowired private RestTemplate     restTemplate;
    @Autowired private ObjectMapper     objectMapper;

    // Gemini Free uses the same tool declaration format as Vertex AI (Google's own format)
    // but sent as JSON over REST rather than via the SDK.
    // We reuse the OpenAI-style Map format since it serializes cleanly to JSON.
    private List<Map<String, Object>> tools;

    // Conversation history for multi-turn (ask → respond)
    private final ThreadLocal<List<Map<String, Object>>> history = ThreadLocal.withInitial(ArrayList::new);

    @Override
    public String providerId() { return "gemini-free"; }

    @Override
    public void init(String specUrl) throws Exception {
        // Gemini REST API accepts tools in the same structure as OpenAI
        tools = converter.loadAsOpenAiTools(specUrl);
    }

    @Override
    public FunctionCallResult ask(String userMessage) throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "parts", List.of(Map.of("text", userMessage))));

        Map<String, Object> body = Map.of(
            "contents", messages,
            "tools",    List.of(Map.of("function_declarations",
                            tools.stream().map(t -> (Map<?, ?>) t.get("function")).toList()))
        );

        JsonNode response = call(body);
        JsonNode candidate = response.path("candidates").path(0).path("content");
        JsonNode parts     = candidate.path("parts");

        // Check for function call
        for (JsonNode part : parts) {
            if (!part.path("functionCall").isMissingNode()) {
                JsonNode fn   = part.path("functionCall");
                String   name = fn.path("name").asText();
                Map<String, Object> args = objectMapper.convertValue(
                        fn.path("args"), objectMapper.getTypeFactory()
                          .constructMapType(Map.class, String.class, Object.class));

                // Save history for the respond() step
                history.get().clear();
                history.get().addAll(messages);
                history.get().add(Map.of("role", "model", "parts", List.of(part)));

                return new FunctionCallResult(name, args, part);
            }
        }

        return null; // model answered directly
    }

    @Override
    public String respond(String userMessage, FunctionCallResult call, String apiResult) throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>(history.get());

        // Append tool result
        messages.add(Map.of("role", "user", "parts", List.of(Map.of(
            "functionResponse", Map.of(
                "name",     call.functionName(),
                "response", Map.of("result", apiResult)
            )
        ))));

        Map<String, Object> body = Map.of("contents", messages);
        JsonNode response = call(body);

        return response.path("candidates").path(0)
                       .path("content").path("parts").path(0)
                       .path("text").asText("");
    }

    private JsonNode call(Map<String, Object> body) throws Exception {
        String url = String.format(BASE_URL, props.getGeminiModel(), props.getGeminiApiKey());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String json = objectMapper.writeValueAsString(body);
        ResponseEntity<String> response = restTemplate.postForEntity(
                url, new HttpEntity<>(json, headers), String.class);
        return objectMapper.readTree(response.getBody());
    }
}
