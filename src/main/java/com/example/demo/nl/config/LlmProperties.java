package com.example.demo.nl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * All LLM-related configuration, loaded from application.properties.
 *
 * Set llm.provider to one of: gemini-vertex, gemini-free, ollama
 */
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /** Which provider to use: gemini-vertex | gemini-free | ollama */
    private String provider = "ollama";

    /** URL of this app's OpenAPI spec */
    private String specUrl = "http://localhost:8080/v3/api-docs";

    /** Base URL of this app (used to execute REST calls) */
    private String apiBaseUrl = "http://localhost:8080";

    // --- Gemini Vertex AI ---
    private String vertexProjectId;
    private String vertexLocation  = "us-central1";
    private String vertexModel     = "gemini-1.5-pro";

    // --- Gemini Free (AI Studio) ---
    private String geminiApiKey;
    private String geminiModel     = "gemini-1.5-flash";

    // --- Ollama ---
    private String ollamaBaseUrl   = "http://localhost:11434";
    private String ollamaModel     = "llama3.1";

    // ---------------------------------------------------------------
    // Getters & Setters
    // ---------------------------------------------------------------

    public String getProvider()                      { return provider; }
    public void   setProvider(String v)              { this.provider = v; }

    public String getSpecUrl()                       { return specUrl; }
    public void   setSpecUrl(String v)               { this.specUrl = v; }

    public String getApiBaseUrl()                    { return apiBaseUrl; }
    public void   setApiBaseUrl(String v)            { this.apiBaseUrl = v; }

    public String getVertexProjectId()               { return vertexProjectId; }
    public void   setVertexProjectId(String v)       { this.vertexProjectId = v; }

    public String getVertexLocation()                { return vertexLocation; }
    public void   setVertexLocation(String v)        { this.vertexLocation = v; }

    public String getVertexModel()                   { return vertexModel; }
    public void   setVertexModel(String v)           { this.vertexModel = v; }

    public String getGeminiApiKey()                  { return geminiApiKey; }
    public void   setGeminiApiKey(String v)          { this.geminiApiKey = v; }

    public String getGeminiModel()                   { return geminiModel; }
    public void   setGeminiModel(String v)           { this.geminiModel = v; }

    public String getOllamaBaseUrl()                 { return ollamaBaseUrl; }
    public void   setOllamaBaseUrl(String v)         { this.ollamaBaseUrl = v; }

    public String getOllamaModel()                   { return ollamaModel; }
    public void   setOllamaModel(String v)           { this.ollamaModel = v; }
}
