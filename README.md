# Demo REST API — NL to REST via LLM

Spring Boot REST API for Products and Orders, with natural language invocation
via a pluggable LLM strategy (Gemini Vertex AI, Gemini Free, or Ollama).

---

## Scaffolding

```bash
nl/
├── config/
│   └── LlmProperties.java          ← all config for all providers
├── converter/
│   ├── OpenApiConverter.java        ← shared parsing, TWO output formats
│   └── OperationInfo.java           ← method + path registry record
├── strategy/
│   ├── LlmStrategy.java             ← interface
│   ├── FunctionCallResult.java      ← provider-neutral result record
│   ├── GeminiVertexStrategy.java    ← Vertex AI SDK + protobuf tools
│   ├── GeminiFreeStrategy.java      ← REST API + API key + JSON tools
│   └── OllamaStrategy.java          ← local Ollama + OpenAI-style tools
├── NlOrchestrator.java              ← picks strategy, executes REST calls
└── NlController.java                ← POST /api/nl/command
```

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

| File                                    | Purpose                                             |
|-----------------------------------------|-----------------------------------------------------|
| `nl/config/LlmProperties.java`          | All config for all providers in one place           |
| `nl/converter/OpenApiConverter.java`    | Reads OpenAPI spec → Gemini format OR OpenAI format |
| `nl/strategy/LlmStrategy.java`          | Interface — add new providers here                  |
| `nl/strategy/GeminiVertexStrategy.java` | Gemini via Vertex AI SDK                            |
| `nl/strategy/GeminiFreeStrategy.java`   | Gemini via REST API + API key                       |
| `nl/strategy/OllamaStrategy.java`       | Ollama local model                                  |
| `nl/NlOrchestrator.java`                | Picks active strategy, executes REST calls          |
| `nl/NlController.java`                  | POST /api/nl/command                                |

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

## How to Start and Use the App

The default configured LLM provider is Ollama.

### 1. Make sure Ollama is running (default provider is ollama)

```bash
ollama serve
ollama pull llama3.1
```

### 2. Start the Spring Boot app

```bash
mvn spring-boot:run
```

You should see in the logs:

```bash
Active LLM provider: ollama
[OpenAI] registered: getAllProducts → GET /api/products
[OpenAI] registered: getOrdersById → GET /api/orders
... (one line per endpoint)
Orchestrator ready
```

### The Endpoint

```
POST http://localhost:8080/api/nl/command
Content-Type: application/json

{ "message": "your natural language here" }
```

### Examples

#### Get all products

```bash
curl -X POST http://localhost:8080/api/nl/command \
  -H "Content-Type: application/json" \
  -d '{"message": "show me all products"}' | jq -r '.reply'
```

#### Search by name

```bash
bashcurl -X POST http://localhost:8080/api/nl/command \
-H "Content-Type: application/json" \
-d '{"message": "find products with laptop in the name"}' | jq -r '.reply'
```

#### Filter orders

```bash
bashcurl -X POST http://localhost:8080/api/nl/command \
-H "Content-Type: application/json" \
-d '{"message": "show all pending orders for customer 42"}' | jq -r '.reply'
```

#### Create an order

```bash
bashcurl -X POST http://localhost:8080/api/nl/command \
-H "Content-Type: application/json" \
-d '{"message": "create an order for customer 55, product 2, quantity 3"}' | jq -r '.reply'
```

#### Update status

```bash
bashcurl -X POST http://localhost:8080/api/nl/command \
-H "Content-Type: application/json" \
-d '{"message": "mark order 101 as shipped"}' | jq -r '.reply'
```bash

#### Delete

```bash
bashcurl -X POST http://localhost:8080/api/nl/command \
-H "Content-Type: application/json" \
-d '{"message": "delete product 3"}' | jq -r '.reply'
```

---

# Tips and Tricks

## The 4 Roles in a Conversation

| Role        | Who sends it      | When                                               |
|-------------|-------------------|----------------------------------------------------|
| `user`      | The human         | The original natural language question             |
| `assistant` | The LLM           | Its response — either text or a tool_call decision |
| `tool`      | Your backend      | The result of executing the REST call              |
| `user`      | The human (again) | The follow-up asking Ollama to summarize           |

### The Full Conversation We Send

```
[user]       "show me all products"
      ↓
[assistant]  { tool_call: getAllProducts() }       ← Ollama's decision
      ↓
[tool]       [{"id":1,"name":"Laptop",...}, ...]   ← your REST API result
      ↓
[user]       "Based on the tool result above,      ← explicit prompt to summarize
              please answer in plain English: 
              show me all products"
      ↓
[assistant]  "Here are all 5 products: ..."        ← final answer to the user
```

### Why `tool` and Not `assistant`?

The `tool` role is a **special signal** to the model meaning:

> "This message is not from a human — it's the output of a function call you previously requested"

Without it, the model wouldn't know whether the JSON data came from the user typing it manually or from an actual API
execution. It uses this context to understand that it previously asked for data and now has the result.

### Why the Extra `user` Message?

Because `llama3.1` in particular doesn't automatically summarize tool results — it needs to be explicitly asked. More
capable models like `gemini-1.5-pro` handle this automatically without the extra prompt.