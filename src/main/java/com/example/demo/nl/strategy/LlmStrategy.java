package com.example.demo.nl.strategy;

/**
 * Common interface for all LLM providers.
 *
 * Each implementation:
 *  1. Loads tools from the OpenAPI spec in its own format
 *  2. Sends the user message + tools to its LLM
 *  3. Returns the function name + arguments the LLM decided to call
 *  4. Accepts the REST API result and returns a natural language response
 *
 * The orchestrator owns steps that are the same for all providers:
 *  - executing the actual REST call
 *  - URL building
 */
public interface LlmStrategy {

    /** Called once at startup to load and register tools from the OpenAPI spec */
    void init(String specUrl) throws Exception;

    /**
     * Step 1 — Ask the LLM what to do.
     * Returns a FunctionCall describing which endpoint to invoke and with what args.
     * Returns null if the LLM answered directly (no function call needed).
     */
    FunctionCallResult ask(String userMessage) throws Exception;

    /**
     * Step 2 — Send the API result back to the LLM for a natural language response.
     */
    String respond(String userMessage, FunctionCallResult call, String apiResult) throws Exception;

    /** Provider identifier — matches the llm.provider property value */
    String providerId();
}
