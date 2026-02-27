package com.example.demo.nl;

import com.example.demo.nl.config.LlmProperties;
import com.example.demo.nl.converter.OpenApiConverter;
import com.example.demo.nl.converter.OperationInfo;
import com.example.demo.nl.strategy.FunctionCallResult;
import com.example.demo.nl.strategy.LlmStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Provider-neutral orchestrator.
 *
 * What it owns (same for all providers):
 *  - Picking the active strategy based on llm.provider
 *  - Executing the actual REST call after the LLM decides what to call
 *  - URL building (path variable substitution + query params)
 *
 * What it delegates to the strategy:
 *  - Loading tools in the right format
 *  - Sending the message to the LLM
 *  - Sending the API result back for a natural language response
 */
@Service
public class NlOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(NlOrchestrator.class);

    @Autowired private LlmProperties      props;
    @Autowired private OpenApiConverter   converter;
    @Autowired private RestTemplate       restTemplate;
    @Autowired private ObjectMapper       objectMapper;
    @Autowired private List<LlmStrategy>  strategies; // Spring injects all implementations

    private LlmStrategy activeStrategy;
    private volatile boolean ready = false;

    /**
     * Runs AFTER the embedded server is fully started and listening on the port.
     * This avoids the chicken-and-egg problem of @PostConstruct trying to call
     * /v3/api-docs before the HTTP server is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() throws Exception {
        // Pick the strategy that matches llm.provider
        activeStrategy = strategies.stream()
                .filter(s -> s.providerId().equals(props.getProvider()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No strategy found for provider: " + props.getProvider()
                        + ". Valid values: gemini-vertex, gemini-free, ollama"));

        log.info("Active LLM provider: {}", activeStrategy.providerId());
        activeStrategy.init(props.getSpecUrl());
        ready = true;
        log.info("Orchestrator ready");
    }

    // ---------------------------------------------------------------
    // Main entry point
    // ---------------------------------------------------------------

    public String process(String userMessage) throws Exception {
        if (!ready) throw new IllegalStateException("Orchestrator is still initializing, please retry.");
        // Step 1 — Ask the LLM what to do
        FunctionCallResult call = activeStrategy.ask(userMessage);

        if (call == null) {
            // LLM answered directly without calling a function (e.g. ambiguous input)
            return "The model could not determine which API to call. Please rephrase your request.";
        }

        log.info("LLM decided to call: {} with args: {}", call.functionName(), call.arguments());

        // Step 2 — Look up method + path from the registry
        OperationInfo op = converter.getOperation(call.functionName())
                .orElseThrow(() -> new IllegalStateException(
                        "Unknown function returned by LLM: " + call.functionName()));

        // Step 3 — Execute the actual REST call
        String apiResult = executeRestCall(op, call.arguments());
        log.info("API result: {}", apiResult);

        // Step 4 — Send result back to LLM for a natural language response
        return activeStrategy.respond(userMessage, call, apiResult);
    }

    // ---------------------------------------------------------------
    // REST execution — same for all providers
    // ---------------------------------------------------------------

    private String executeRestCall(OperationInfo op, Map<String, Object> args) throws Exception {
        String url = buildUrl(op.path(), args);
        log.info("Executing: {} {}", op.method(), url);

        return switch (op.method()) {
            case "GET"    -> get(url);
            case "POST"   -> post(url, args);
            case "PUT"    -> put(url, args);
            case "PATCH"  -> patch(url, args);
            case "DELETE" -> delete(url);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + op.method());
        };
    }

    /**
     * Builds the full URL:
     *  - Substitutes {pathVariables} from args
     *  - Appends remaining args as query parameters (for GET)
     */
    private String buildUrl(String pathTemplate, Map<String, Object> args) {
        String url = props.getApiBaseUrl() + pathTemplate;
        Map<String, Object> remaining = new HashMap<>(args);

        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            if (url.contains(placeholder)) {
                url = url.replace(placeholder, String.valueOf(entry.getValue()));
                remaining.remove(entry.getKey());
            }
        }

        if (!remaining.isEmpty()) {
            StringBuilder qs = new StringBuilder("?");
            remaining.forEach((k, v) -> qs.append(k).append("=").append(v).append("&"));
            url += qs.substring(0, qs.length() - 1);
        }

        return url;
    }

    // ---------------------------------------------------------------
    // HTTP helpers
    // ---------------------------------------------------------------

    private String get(String url) {
        return restTemplate.getForObject(url, String.class);
    }

    private String post(String url, Map<String, Object> body) throws Exception {
        ResponseEntity<String> r = restTemplate.postForEntity(url, jsonEntity(body), String.class);
        return r.getBody();
    }

    private String put(String url, Map<String, Object> body) throws Exception {
        restTemplate.put(url, jsonEntity(body));
        return "{\"result\":\"updated\"}";
    }

    private String patch(String url, Map<String, Object> body) throws Exception {
        ResponseEntity<String> r = restTemplate.exchange(
                url, HttpMethod.PATCH, jsonEntity(body), String.class);
        return r.getBody();
    }

    private String delete(String url) {
        restTemplate.delete(url);
        return "{\"result\":\"deleted\"}";
    }

    private HttpEntity<String> jsonEntity(Map<String, Object> body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
    }
}
