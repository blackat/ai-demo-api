package com.example.demo.nl.strategy;

import com.example.demo.nl.config.LlmProperties;
import com.example.demo.nl.converter.OpenApiConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM strategy for Ollama — fully local, free, no API key needed.
 * <p>
 * Uses the OpenAI-compatible Ollama API (/api/chat with tools).
 * Recommended models with function calling: llama3.1, llama3.2, qwen2.5, mistral
 * <p>
 * Requires:
 * llm.provider=ollama
 * ollama running locally: https://ollama.com
 * ollama pull llama3.1
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaStrategy implements LlmStrategy {

    @Autowired
    private OpenApiConverter converter;
    @Autowired
    private LlmProperties props;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    private List<Map<String, Object>> tools;

    // Conversation history per thread (ask → respond)
    private final ThreadLocal<List<Map<String, Object>>> history = ThreadLocal.withInitial(ArrayList::new);

    @Override
    public String providerId() {
        return "ollama";
    }

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
        body.put("model", props.getOllamaModel());
        body.put("messages", messages);
        body.put("tools", tools);
        body.put("stream", false);

        Map<?, ?> response = post(props.getOllamaBaseUrl() + "/api/chat", body);
        Map<?, ?> message = (Map<?, ?>) response.get("message");

        List<?> toolCalls = (List<?>) message.get("tool_calls");
        if (toolCalls == null || toolCalls.isEmpty()) return null;

        Map<?, ?> fnCall = (Map<?, ?>) ((Map<?, ?>) toolCalls.get(0)).get("function");
        String name = (String) fnCall.get("name");
        Map<String, Object> args = (Map<String, Object>) fnCall.get("arguments");

        // Save history for the respond() step
        history.get().clear();
        history.get().addAll(messages);
        history.get().add(Map.of(
                "role", "assistant",
                "content", "",
                "tool_calls", toolCalls
        ));

        return new FunctionCallResult(name, args, toolCalls);
    }

    @Override
    public String respond(String userMessage, FunctionCallResult call, String apiResult) throws Exception {
        // llama3.1 and most local models do not reliably summarize tool results
        // in the second turn — they tend to hallucinate or ignore the actual data.
        // Instead we send one single prompt that includes the data directly,
        // which is far more reliable with local models.

        String prompt = String.format(
                "The user asked: \"%s\"\n\n" +
                        "You called the API function \"%s\" and got this result:\n%s\n\n" +
                        "Please answer the user\'s question in plain English using only the data above.",
                userMessage, call.functionName(), apiResult
        );

        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", prompt)
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getOllamaModel());
        body.put("messages", messages);
        body.put("stream", false);

        Map<?, ?> response = post(props.getOllamaBaseUrl() + "/api/chat", body);
        log.info("Ollama respond() raw response: {}", objectMapper.writeValueAsString(response));

        Map<?, ?> message = (Map<?, ?>) response.get("message");
        String content = message != null ? (String) message.get("content") : null;

        if (content == null || content.isBlank()) {
            log.warn("Ollama returned empty content — returning raw API result");
            return apiResult;
        }

        return content;
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
