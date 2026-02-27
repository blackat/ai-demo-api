package com.example.demo.nl.strategy;

import com.example.demo.nl.config.LlmProperties;
import com.example.demo.nl.converter.OpenApiConverter;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.*;
import com.google.cloud.vertexai.generativeai.ContentMaker;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * LLM strategy for Google Gemini via Vertex AI (paid, Google Cloud project required).
 *
 * Requires:
 *   llm.provider=gemini-vertex
 *   llm.vertex-project-id=YOUR_PROJECT_ID
 *   gcloud auth application-default login
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "gemini-vertex")
public class GeminiVertexStrategy implements LlmStrategy {

    @Autowired private OpenApiConverter converter;
    @Autowired private LlmProperties    props;

    private List<FunctionDeclaration> functions;
    private Tool                      tools;

    @Override
    public String providerId() { return "gemini-vertex"; }

    @Override
    public void init(String specUrl) throws Exception {
        functions = converter.loadAsGeminiTools(specUrl);
        tools     = Tool.newBuilder().addAllFunctionDeclarations(functions).build();
    }

    @Override
    public FunctionCallResult ask(String userMessage) throws Exception {
        try (VertexAI vertex = new VertexAI(props.getVertexProjectId(), props.getVertexLocation())) {
            GenerativeModel model = new GenerativeModel(props.getVertexModel(), vertex)
                    .withTools(List.of(tools));

            GenerateContentResponse response = model.generateContent(userMessage);
            Content content = response.getCandidates(0).getContent();

            Optional<Part> fnPart = content.getPartsList().stream()
                    .filter(Part::hasFunctionCall).findFirst();

            if (fnPart.isEmpty()) return null;

            FunctionCall fn = fnPart.get().getFunctionCall();
            return new FunctionCallResult(fn.getName(), structToMap(fn.getArgs()), content);
        }
    }

    @Override
    public String respond(String userMessage, FunctionCallResult call, String apiResult) throws Exception {
        try (VertexAI vertex = new VertexAI(props.getVertexProjectId(), props.getVertexLocation())) {
            GenerativeModel model = new GenerativeModel(props.getVertexModel(), vertex)
                    .withTools(List.of(tools));

            Content previousResponse = (Content) call.rawResponse();

            GenerateContentResponse response = model.generateContent(List.of(
                    ContentMaker.fromMultiModalData(userMessage),
                    previousResponse,
                    ContentMaker.fromMultiModalData(
                            FunctionResponse.newBuilder()
                                    .setName(call.functionName())
                                    .setResponse(Struct.newBuilder()
                                            .putFields("result", Value.newBuilder()
                                                    .setStringValue(apiResult).build())
                                            .build())
                                    .build()
                    )
            ));

            return extractText(response.getCandidates(0).getContent());
        }
    }

    private Map<String, Object> structToMap(Struct struct) {
        Map<String, Object> result = new HashMap<>();
        struct.getFieldsMap().forEach((k, v) -> result.put(k, protoValueToJava(v)));
        return result;
    }

    private Object protoValueToJava(Value v) {
        return switch (v.getKindCase()) {
            case STRING_VALUE -> v.getStringValue();
            case NUMBER_VALUE -> {
                double d = v.getNumberValue();
                yield (d == Math.floor(d)) ? (long) d : d;
            }
            case BOOL_VALUE -> v.getBoolValue();
            default         -> v.toString();
        };
    }

    private String extractText(Content content) {
        return content.getPartsList().stream()
                .filter(Part::hasText).map(Part::getText)
                .reduce("", String::concat).trim();
    }
}
