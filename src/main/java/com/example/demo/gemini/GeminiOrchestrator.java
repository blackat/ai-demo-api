package com.example.demo.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.*;
import com.google.cloud.vertexai.generativeai.ContentMaker;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates the full NL → Gemini → REST API → natural language response flow.
 * <p>
 * Flow:
 * 1. Load all endpoints from OpenAPI spec as Gemini FunctionDeclarations (once at startup)
 * 2. User sends natural language message
 * 3. Ask Gemini what to do — it returns a function call (endpoint + params)
 * 4. Execute the actual REST call against your API
 * 5. Return result to Gemini to produce a human-readable response
 */
@Service
public class GeminiOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GeminiOrchestrator.class);

    @Autowired
    private OpenApiToGeminiConverter converter;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // After
    @Autowired
    private GeminiProperties geminiProps;


    private List<FunctionDeclaration> functions;
    private Tool geminiTools;

    // ---------------------------------------------------------------
    // Startup — load all functions from OpenAPI spec
    // ---------------------------------------------------------------


    @PostConstruct
    public void init() throws Exception {
        log.info("Loading OpenAPI spec from: {}", geminiProps.getSpecUrl());
        functions = converter.load(geminiProps.getSpecUrl());
        geminiTools = Tool.newBuilder().addAllFunctionDeclarations(functions).build();
        log.info("Gemini orchestrator ready with {} functions", functions.size());
    }

    // ---------------------------------------------------------------
    // Main entry point
    // ---------------------------------------------------------------

    public String process(String userMessage) throws Exception {
        try (VertexAI vertexAI = new VertexAI(geminiProps.getProjectId(), geminiProps.getLocation())) {

            GenerativeModel model = new GenerativeModel(geminiProps.getModel(), vertexAI)
                    .withTools(List.of(geminiTools));

            // Step 1: Ask Gemini what the user wants to do
            GenerateContentResponse response = model.generateContent(userMessage);
            Content responseContent = response.getCandidates(0).getContent();

            // Step 2: Check if Gemini decided to call a function
            Optional<Part> functionCallPart = responseContent.getPartsList().stream()
                    .filter(Part::hasFunctionCall)
                    .findFirst();

            if (functionCallPart.isEmpty()) {
                // Gemini answered directly (e.g. ambiguous input, clarification needed)
                return extractText(responseContent);
            }

            FunctionCall fnCall = functionCallPart.get().getFunctionCall();
            log.info("Gemini wants to call: {} with args: {}", fnCall.getName(), fnCall.getArgs());

            // Step 3: Execute the actual REST call
            String apiResult = executeRestCall(fnCall);
            log.info("API result: {}", apiResult);

            // Step 4: Send result back to Gemini for a natural language response
            GenerateContentResponse finalResponse = model.generateContent(List.of(
                    ContentMaker.fromMultiModalData(userMessage),
                    responseContent,
                    ContentMaker.fromMultiModalData(
                            FunctionResponse.newBuilder()
                                    .setName(fnCall.getName())
                                    .setResponse(Struct.newBuilder()
                                            .putFields("result", Value.newBuilder()
                                                    .setStringValue(apiResult)
                                                    .build())
                                            .build())
                                    .build()
                    )
            ));

            return extractText(finalResponse.getCandidates(0).getContent());
        }
    }

    // ---------------------------------------------------------------
    // Execute the REST call that Gemini decided to make
    // ---------------------------------------------------------------

    private String executeRestCall(FunctionCall fnCall) throws Exception {
        String operationId = fnCall.getName();

        // Look up method + path from our registry
        OpenApiToGeminiConverter.OperationInfo op = converter.getOperation(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown operation: " + operationId));

        // Convert Protobuf Struct → plain Java Map
        Map<String, Object> args = structToMap(fnCall.getArgs());

        // Build the URL, substituting path variables
        String url = buildUrl(op.path(), args);

        log.info("Executing: {} {}", op.method(), url);

        return switch (op.method()) {
            case "GET" -> get(url);
            case "POST" -> post(url, args);
            case "PUT" -> put(url, args);
            case "PATCH" -> patch(url, args);
            case "DELETE" -> delete(url);
            default -> throw new IllegalArgumentException("Unsupported method: " + op.method());
        };
    }

    // ---------------------------------------------------------------
    // URL building — substitutes {pathVariables} and appends query params
    // ---------------------------------------------------------------

    private String buildUrl(String pathTemplate, Map<String, Object> args) {
        String url = geminiProps.getApiBaseUrl() + pathTemplate;

        // Substitute path variables: /api/orders/{id} + {id: 101} → /api/orders/101
        Map<String, Object> remaining = new HashMap<>(args);
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            if (url.contains(placeholder)) {
                url = url.replace(placeholder, String.valueOf(entry.getValue()));
                remaining.remove(entry.getKey());
            }
        }

        // Append remaining args as query parameters (for GET)
        if (!remaining.isEmpty()) {
            StringBuilder qs = new StringBuilder("?");
            remaining.forEach((k, v) -> qs.append(k).append("=").append(v).append("&"));
            url += qs.substring(0, qs.length() - 1); // remove trailing &
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
        HttpHeaders headers = jsonHeaders();
        String json = objectMapper.writeValueAsString(body);
        HttpEntity<String> entity = new HttpEntity<>(json, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        return response.getBody();
    }

    private String put(String url, Map<String, Object> body) throws Exception {
        HttpHeaders headers = jsonHeaders();
        String json = objectMapper.writeValueAsString(body);
        HttpEntity<String> entity = new HttpEntity<>(json, headers);
        restTemplate.put(url, entity);
        return "{\"result\": \"updated\"}";
    }

    private String patch(String url, Map<String, Object> body) throws Exception {
        HttpHeaders headers = jsonHeaders();
        String json = objectMapper.writeValueAsString(body);
        HttpEntity<String> entity = new HttpEntity<>(json, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PATCH, entity, String.class);
        return response.getBody();
    }

    private String delete(String url) {
        restTemplate.delete(url);
        return "{\"result\": \"deleted\"}";
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Converts a Protobuf Struct (what Gemini returns) to a plain Java Map
     */
    private Map<String, Object> structToMap(Struct struct) {
        Map<String, Object> result = new HashMap<>();
        struct.getFieldsMap().forEach((k, v) -> result.put(k, protoValueToJava(v)));
        return result;
    }

    private Object protoValueToJava(Value value) {
        return switch (value.getKindCase()) {
            case STRING_VALUE -> value.getStringValue();
            case NUMBER_VALUE -> {
                double d = value.getNumberValue();
                yield (d == Math.floor(d)) ? (long) d : d;
            }
            case BOOL_VALUE -> value.getBoolValue();
            case NULL_VALUE -> null;
            default -> value.toString();
        };
    }

    private String extractText(Content content) {
        return content.getPartsList().stream()
                .filter(Part::hasText)
                .map(Part::getText)
                .reduce("", String::concat)
                .trim();
    }
}
