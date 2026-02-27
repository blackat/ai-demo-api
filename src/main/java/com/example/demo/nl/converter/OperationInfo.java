package com.example.demo.nl.converter;

/**
 * Holds the HTTP method and path for a given operationId.
 * Stored in the converter registry so the orchestrator can
 * execute the correct REST call after the LLM decides which function to invoke.
 */
public record OperationInfo(String method, String path, String operationId) {}
