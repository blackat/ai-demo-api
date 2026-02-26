package com.example.demo.gemini;

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
 * Reads the live OpenAPI spec and converts every endpoint into a
 * Gemini FunctionDeclaration — no manual mapping needed.
 *
 * Also keeps an internal registry so the orchestrator can look up
 * method + path by operationId when Gemini returns a function call.
 */
@Component
public class OpenApiToGeminiConverter {

    private static final Logger log = LoggerFactory.getLogger(OpenApiToGeminiConverter.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // Registry: operationId → OperationInfo (method + path)
    private final Map<String, OperationInfo> registry = new LinkedHashMap<>();

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    /**
     * Loads the OpenAPI spec from the given URL and returns the full list
     * of Gemini FunctionDeclarations — one per endpoint.
     */
    public List<FunctionDeclaration> load(String specUrl) throws Exception {
        registry.clear();

        String specJson = restTemplate.getForObject(specUrl, String.class);
        JsonNode spec   = objectMapper.readTree(specJson);

        List<FunctionDeclaration> functions = new ArrayList<>();
        JsonNode paths = spec.path("paths");

        paths.fields().forEachRemaining(pathEntry -> {
            String path = pathEntry.getKey();                 // e.g. /api/orders
            pathEntry.getValue().fields().forEachRemaining(methodEntry -> {
                String     httpMethod = methodEntry.getKey().toUpperCase(); // GET / POST / ...
                JsonNode   operation  = methodEntry.getValue();

                try {
                    FunctionDeclaration fn = convert(httpMethod, path, operation, spec);
                    functions.add(fn);
                    log.info("Registered function: {} → {} {}", fn.getName(), httpMethod, path);
                } catch (Exception e) {
                    log.warn("Skipped {} {}: {}", httpMethod, path, e.getMessage());
                }
            });
        });

        log.info("Total functions loaded: {}", functions.size());
        return functions;
    }

    /**
     * Returns the HTTP method + path for a given operationId.
     * Used by the orchestrator to execute the actual REST call.
     */
    public Optional<OperationInfo> getOperation(String operationId) {
        return Optional.ofNullable(registry.get(operationId));
    }

    // ---------------------------------------------------------------
    // Conversion logic
    // ---------------------------------------------------------------

    private FunctionDeclaration convert(String httpMethod, String path,
                                        JsonNode operation, JsonNode spec) {

        // operationId is used as the function name Gemini will call
        String operationId = operation.path("operationId")
                .asText(sanitize(httpMethod + "_" + path));

        String description = operation.path("summary").asText(
                operation.path("description").asText(httpMethod + " " + path));

        // Build the parameter schema
        Schema paramSchema = buildParamSchema(operation, spec);

        // Store in registry for later execution
        registry.put(operationId, new OperationInfo(httpMethod, path, operationId));

        return FunctionDeclaration.newBuilder()
                .setName(operationId)
                .setDescription(description)
                .setParameters(paramSchema)
                .build();
    }

    /**
     * Builds a Gemini Schema from the operation's parameters and request body.
     *
     * Covers:
     *  - Path parameters      e.g. /api/orders/{id}
     *  - Query parameters     e.g. /api/orders?status=pending
     *  - Request body fields  e.g. POST /api/orders { productId, quantity }
     */
    private Schema buildParamSchema(JsonNode operation, JsonNode spec) {
        Schema.Builder schema   = Schema.newBuilder().setType(Type.OBJECT);
        List<String>   required = new ArrayList<>();

        // --- Path & query parameters ---
        JsonNode parameters = operation.path("parameters");
        if (parameters.isArray()) {
            for (JsonNode param : parameters) {
                String name        = param.path("name").asText();
                String description = param.path("description").asText("");
                String typeStr     = param.path("schema").path("type").asText("string");
                boolean isRequired = param.path("required").asBoolean(false);

                schema.putProperties(name, Schema.newBuilder()
                        .setType(mapType(typeStr))
                        .setDescription(description)
                        .build());

                if (isRequired) required.add(name);
            }
        }

        // --- Request body ---
        JsonNode requestBody = operation.path("requestBody");
        if (!requestBody.isMissingNode()) {
            JsonNode bodySchema = requestBody
                    .path("content")
                    .path("application/json")
                    .path("schema");

            // Resolve $ref if present
            bodySchema = resolveRef(bodySchema, spec);

            JsonNode properties = bodySchema.path("properties");
            if (properties.isObject()) {
                properties.fields().forEachRemaining(propEntry -> {
                    String propName = propEntry.getKey();
                    String propType = propEntry.getValue().path("type").asText("string");
                    String propDesc = propEntry.getValue().path("description").asText("");

                    schema.putProperties(propName, Schema.newBuilder()
                            .setType(mapType(propType))
                            .setDescription(propDesc)
                            .build());
                });
            }

            // Required fields from body schema
            JsonNode bodyRequired = bodySchema.path("required");
            if (bodyRequired.isArray()) {
                bodyRequired.forEach(r -> required.add(r.asText()));
            }
        }

        required.forEach(schema::addRequired);
        return schema.build();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** Resolves a JSON $ref pointer within the spec (e.g. #/components/schemas/Order) */
    private JsonNode resolveRef(JsonNode node, JsonNode spec) {
        String ref = node.path("$ref").asText("");
        if (ref.isEmpty()) return node;

        // "#/components/schemas/Order" → ["components", "schemas", "Order"]
        String[] parts = ref.replace("#/", "").split("/");
        JsonNode resolved = spec;
        for (String part : parts) {
            resolved = resolved.path(part);
        }
        return resolved;
    }

    /** Maps OpenAPI type strings to Gemini Schema Types */
    private Type mapType(String openApiType) {
        return switch (openApiType.toLowerCase()) {
            case "integer", "int64", "int32" -> Type.INTEGER;
            case "number", "float", "double" -> Type.NUMBER;
            case "boolean"                   -> Type.BOOLEAN;
            case "array"                     -> Type.ARRAY;
            case "object"                    -> Type.OBJECT;
            default                          -> Type.STRING;
        };
    }

    /** Sanitizes a string to be a valid Gemini function name */
    private String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9_]", "_").replaceAll("_+", "_");
    }

    // ---------------------------------------------------------------
    // Inner record
    // ---------------------------------------------------------------

    public record OperationInfo(String method, String path, String operationId) {}
}
