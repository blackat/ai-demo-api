package com.example.demo.nl.strategy;

import java.util.Map;

/**
 * The structured decision returned by the LLM:
 * "call this function with these arguments".
 *
 * Provider-neutral — each strategy maps its own response format into this record.
 * The orchestrator uses it to execute the actual REST call.
 */
public record FunctionCallResult(
        String functionName,
        Map<String, Object> arguments,
        Object rawResponse   // kept for sending back in the follow-up (provider-specific)
) {}
