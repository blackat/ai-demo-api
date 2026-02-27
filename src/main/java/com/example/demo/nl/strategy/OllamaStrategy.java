package com.example.demo.nl.strategy;

import com.example.demo.nl.config.LlmProperties;
import com.example.demo.nl.converter.OpenApiConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * LLM strategy for Ollama — fully local, free, no API key needed.
 *
 * Uses the OpenAI-compatible Ollama API (/api/chat with tools).
 * Recommended models with function calling: llama3.1, llama3.2, qwen2.5, mistral
 *
 * Requires:
 *   llm.provider=ollama
 *   ollama running locally: https://ollama.com
 *   ollama pull llama3.1
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaStrategy implements LlmStrategy {

    @Autowired private OpenApiConverter converter;
    @Autowired private LlmProperties    props;
    @Autowired private RestTemplate     restTemplate;
    @Autowired private ObjectMapper     objectMapper;

    private List<Map<String, Object>> tools;

    // Conversation history per thread (ask → respond)
    private final ThreadLocal<List<Map<String, Object>>> history = ThreadLocal.withInitial(ArrayList::new);

    @Override
    public String providerId() { return "ollama"; }

    @Override
    public void init(String specUrl) throws Exception {
        // Ollama uses OpenAI-compatible tool format — exact same structure
        tools = converter.loadAsOpenAiTools(specUrl);
    }

    @Override
    public FunctionCallResult ask(String userMessage) throws Exception {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", userMessage)
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model",    props.getOllamaModel());
        body.put("messages", messages);
        body.put("tools",    tools);
        body.put("stream",   false);

        Map<?, ?> response = post(props.getOllamaBaseUrl() + "/api/chat", body);
        Map<?, ?> message  = (Map<?, ?>) response.get("message");

        List<?> toolCalls = (List<?>) message.get("tool_calls");
        if (toolCalls == null || toolCalls.isEmpty()) return null;

        Map<?, ?> fnCall  = (Map<?, ?>) ((Map<?, ?>) toolCalls.get(0)).get("function");
        String    name    = (String) fnCall.get("name");
        Map<String, Object> args = (Map<String, Object>) fnCall.get("arguments");

        // Save history for the respond() step
        history.get().clear();
        history.get().addAll(messages);
        history.get().add(Map.of(
            "role",       "assistant",
            "content",    "",
            "tool_calls", toolCalls
        ));

        return new FunctionCallResult(name, args, toolCalls);
    }

    @Override
    public String respond(String userMessage, FunctionCallResult call, String apiResult) throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>(history.get());

        // Append tool result
        messages.add(Map.of(
            "role",    "tool",
            "content", apiResult
        ));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model",    props.getOllamaModel());
        body.put("messages", messages);
        body.put("stream",   false);

        Map<?, ?> response = post(props.getOllamaBaseUrl() + "/api/chat", body);
        Map<?, ?> message  = (Map<?, ?>) response.get("message");
        return (String) message.get("content");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String url, Object body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String json = objectMapper.writeValueAsString(body);
        ResponseEntity<String> response = restTemplate.postForEntity(
                url, new HttpEntity<>(json, headers), String.class);
        return objectMapper.readValue(response.getBody(), Map.class);
    }
}
