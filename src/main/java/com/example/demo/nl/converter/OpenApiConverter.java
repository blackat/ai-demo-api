package com.example.demo.nl.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.api.FunctionDeclaration;
import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.api.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Reads the live OpenAPI spec and converts every endpoint into either:
 *
 *  A) Gemini FunctionDeclaration objects  (for Vertex AI and Gemini Free)
 *  B) OpenAI-style Map<String,Object>     (for Ollama)
 *
 * The parsing logic is shared — only the output format differs.
 * Also maintains an internal registry (operationId → OperationInfo)
 * used by the orchestrator to execute the correct REST call.
 */
@Component
public class OpenApiConverter {

    private static final Logger log = LoggerFactory.getLogger(OpenApiConverter.class);

    @Autowired private RestTemplate restTemplate;
    @Autowired private ObjectMapper objectMapper;

    /** operationId → method + path, populated on every load() call */
    private final Map<String, OperationInfo> registry = new LinkedHashMap<>();

    // ---------------------------------------------------------------
    // Public — Gemini format (Vertex AI + Gemini Free)
    // ---------------------------------------------------------------

    public List<FunctionDeclaration> loadAsGeminiTools(String specUrl) throws Exception {
        registry.clear();
        JsonNode spec = fetchSpec(specUrl);
        List<FunctionDeclaration> result = new ArrayList<>();

        forEachOperation(spec, (method, path, operation) -> {
            String id   = resolveOperationId(method, path, operation);
            String desc = resolveDescription(operation);
            registry.put(id, new OperationInfo(method, path, id));

            Schema paramSchema = buildGeminiSchema(operation, spec);

            result.add(FunctionDeclaration.newBuilder()
                    .setName(id)
                    .setDescription(desc)
                    .setParameters(paramSchema)
                    .build());

            log.info("[Gemini] registered: {} → {} {}", id, method, path);
        });

        return result;
    }

    // ---------------------------------------------------------------
    // Public — OpenAI/Ollama format
    // ---------------------------------------------------------------

    public List<Map<String, Object>> loadAsOpenAiTools(String specUrl) throws Exception {
        registry.clear();
        JsonNode spec = fetchSpec(specUrl);
        List<Map<String, Object>> result = new ArrayList<>();

        forEachOperation(spec, (method, path, operation) -> {
            String id   = resolveOperationId(method, path, operation);
            String desc = resolveDescription(operation);
            registry.put(id, new OperationInfo(method, path, id));

            Map<String, Object> parameters = buildOpenAiSchema(operation, spec);

            result.add(Map.of(
                "type", "function",
                "function", Map.of(
                    "name",        id,
                    "description", desc,
                    "parameters",  parameters
                )
            ));

            log.info("[OpenAI] registered: {} → {} {}", id, method, path);
        });

        return result;
    }

    // ---------------------------------------------------------------
    // Registry lookup
    // ---------------------------------------------------------------

    public Optional<OperationInfo> getOperation(String operationId) {
        return Optional.ofNullable(registry.get(operationId));
    }

    // ---------------------------------------------------------------
    // Shared parsing
    // ---------------------------------------------------------------

    @FunctionalInterface
    private interface OperationConsumer {
        void accept(String method, String path, JsonNode operation);
    }

    private void forEachOperation(JsonNode spec, OperationConsumer consumer) {
        spec.path("paths").fields().forEachRemaining(pathEntry -> {
            String path = pathEntry.getKey();
            pathEntry.getValue().fields().forEachRemaining(methodEntry -> {
                String method    = methodEntry.getKey().toUpperCase();
                JsonNode operation = methodEntry.getValue();
                try {
                    consumer.accept(method, path, operation);
                } catch (Exception e) {
                    log.warn("Skipped {} {}: {}", method, path, e.getMessage());
                }
            });
        });
    }

    private String resolveOperationId(String method, String path, JsonNode operation) {
        return operation.path("operationId")
                .asText(sanitize(method + "_" + path));
    }

    private String resolveDescription(JsonNode operation) {
        return operation.path("summary").asText(
               operation.path("description").asText("No description"));
    }

    // ---------------------------------------------------------------
    // Gemini schema builder → returns protobuf Schema
    // ---------------------------------------------------------------

    private Schema buildGeminiSchema(JsonNode operation, JsonNode spec) {
        Schema.Builder schema = Schema.newBuilder().setType(Type.OBJECT);
        List<String> required = new ArrayList<>();

        // Path & query parameters
        operation.path("parameters").forEach(param -> {
            String name    = param.path("name").asText();
            String typeStr = param.path("schema").path("type").asText("string");
            String desc    = param.path("description").asText("");
            boolean req    = param.path("required").asBoolean(false);

            schema.putProperties(name, Schema.newBuilder()
                    .setType(mapGeminiType(typeStr))
                    .setDescription(desc)
                    .build());
            if (req) required.add(name);
        });

        // Request body
        JsonNode bodySchema = resolveBodySchema(operation, spec);
        if (bodySchema != null) {
            bodySchema.path("properties").fields().forEachRemaining(prop -> {
                String propType = prop.getValue().path("type").asText("string");
                String propDesc = prop.getValue().path("description").asText("");
                schema.putProperties(prop.getKey(), Schema.newBuilder()
                        .setType(mapGeminiType(propType))
                        .setDescription(propDesc)
                        .build());
            });
            bodySchema.path("required").forEach(r -> required.add(r.asText()));
        }

        required.forEach(schema::addRequired);
        return schema.build();
    }

    // ---------------------------------------------------------------
    // OpenAI schema builder → returns plain Map (JSON-serializable)
    // ---------------------------------------------------------------

    private Map<String, Object> buildOpenAiSchema(JsonNode operation, JsonNode spec) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        // Path & query parameters
        operation.path("parameters").forEach(param -> {
            String name    = param.path("name").asText();
            String typeStr = param.path("schema").path("type").asText("string");
            String desc    = param.path("description").asText("");
            boolean req    = param.path("required").asBoolean(false);

            properties.put(name, Map.of(
                "type",        mapOpenAiType(typeStr),
                "description", desc
            ));
            if (req) required.add(name);
        });

        // Request body
        JsonNode bodySchema = resolveBodySchema(operation, spec);
        if (bodySchema != null) {
            bodySchema.path("properties").fields().forEachRemaining(prop -> {
                String propType = prop.getValue().path("type").asText("string");
                String propDesc = prop.getValue().path("description").asText("");
                properties.put(prop.getKey(), Map.of(
                    "type",        mapOpenAiType(propType),
                    "description", propDesc
                ));
            });
            bodySchema.path("required").forEach(r -> required.add(r.asText()));
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type",       "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) schema.put("required", required);
        return schema;
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private JsonNode fetchSpec(String specUrl) throws Exception {
        String json = restTemplate.getForObject(specUrl, String.class);
        return objectMapper.readTree(json);
    }

    private JsonNode resolveBodySchema(JsonNode operation, JsonNode spec) {
        JsonNode bodySchema = operation
                .path("requestBody")
                .path("content")
                .path("application/json")
                .path("schema");

        if (bodySchema.isMissingNode()) return null;
        return resolveRef(bodySchema, spec);
    }

    private JsonNode resolveRef(JsonNode node, JsonNode spec) {
        String ref = node.path("$ref").asText("");
        if (ref.isEmpty()) return node;
        String[] parts = ref.replace("#/", "").split("/");
        JsonNode resolved = spec;
        for (String part : parts) resolved = resolved.path(part);
        return resolved;
    }

    private Type mapGeminiType(String t) {
        return switch (t.toLowerCase()) {
            case "integer", "int32", "int64" -> Type.INTEGER;
            case "number", "float", "double" -> Type.NUMBER;
            case "boolean"                   -> Type.BOOLEAN;
            case "array"                     -> Type.ARRAY;
            case "object"                    -> Type.OBJECT;
            default                          -> Type.STRING;
        };
    }

    private String mapOpenAiType(String t) {
        return switch (t.toLowerCase()) {
            case "integer", "int32", "int64" -> "integer";
            case "number", "float", "double" -> "number";
            case "boolean"                   -> "boolean";
            case "array"                     -> "array";
            case "object"                    -> "object";
            default                          -> "string";
        };
    }

    private String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9_]", "_").replaceAll("_+", "_");
    }
}
