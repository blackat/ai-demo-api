package com.example.demo.gemini;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {

    private String projectId;
    private String location = "us-central1";
    private String model = "gemini-1.5-pro";
    private String apiBaseUrl = "http://localhost:8080";
    private String specUrl = "http://localhost:8080/v3/api-docs";

    // getters and setters
    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String v) {
        this.projectId = v;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String v) {
        this.location = v;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String v) {
        this.model = v;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String v) {
        this.apiBaseUrl = v;
    }

    public String getSpecUrl() {
        return specUrl;
    }

    public void setSpecUrl(String v) {
        this.specUrl = v;
    }
}