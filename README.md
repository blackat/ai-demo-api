# Demo REST API — NL to REST via LLM

Spring Boot REST API for Products and Orders, with natural language invocation
via a pluggable LLM strategy (Gemini Vertex AI, Gemini Free, or Ollama).

---

## Architecture

```
POST /api/nl/command { "message": "show pending orders for customer 42" }
        ↓
NlOrchestrator
        ↓ picks strategy based on llm.provider
LlmStrategy (GeminiVertex | GeminiFree | Ollama)
        ↓ sends user message + all API endpoints as tools
LLM decides: call getOrders(customerId=42, status=pending)
        ↓
NlOrchestrator executes: GET /api/orders?customerId=42&status=pending
        ↓
LLM receives result → produces natural language response
        ↓
"Customer 42 has 2 pending orders: ..."
```

---

## Requirements

- Java 17+
- Maven 3.8+

---

## Configuration — choose your provider

Edit `src/main/resources/application.properties`:

### Option 1 — Ollama (free, local)
```properties
llm.provider=ollama
llm.ollama-model=llama3.1
```
Setup:
```bash
# Install from https://ollama.com, then:
ollama pull llama3.1
```

### Option 2 — Gemini Free (free tier, API key only)
```properties
llm.provider=gemini-free
llm.gemini-api-key=YOUR_KEY   # https://aistudio.google.com/app/apikey
llm.gemini-model=gemini-1.5-flash
```

### Option 3 — Gemini Vertex AI (paid, Google Cloud)
```properties
llm.provider=gemini-vertex
llm.vertex-project-id=YOUR_PROJECT_ID
```
Setup:
```bash
gcloud auth application-default login
```

---

## Run

```bash
mvn spring-boot:run
```

---

## Usage

```bash
curl -X POST http://localhost:8080/api/nl/command \
  -H "Content-Type: application/json" \
  -d '{"message": "show all pending orders for customer 42"}'

curl -X POST http://localhost:8080/api/nl/command \
  -d '{"message": "create an order for customer 55, product 1, quantity 3"}'

curl -X POST http://localhost:8080/api/nl/command \
  -d '{"message": "find products with laptop in the name"}'
```

---

## Key Files

| File | Purpose |
|------|---------|
| `nl/config/LlmProperties.java` | All config for all providers in one place |
| `nl/converter/OpenApiConverter.java` | Reads OpenAPI spec → Gemini format OR OpenAI format |
| `nl/strategy/LlmStrategy.java` | Interface — add new providers here |
| `nl/strategy/GeminiVertexStrategy.java` | Gemini via Vertex AI SDK |
| `nl/strategy/GeminiFreeStrategy.java` | Gemini via REST API + API key |
| `nl/strategy/OllamaStrategy.java` | Ollama local model |
| `nl/NlOrchestrator.java` | Picks active strategy, executes REST calls |
| `nl/NlController.java` | POST /api/nl/command |

---

## Adding a New Provider

1. Implement `LlmStrategy`
2. Annotate with `@Component` and `@ConditionalOnProperty(name="llm.provider", havingValue="your-id")`
3. Add config fields to `LlmProperties`
4. Set `llm.provider=your-id` in properties

No other files need to change.

---

## Swagger UI
```
http://localhost:8080/swagger-ui.html
```
