# Demo REST API

A Spring Boot REST API for **Products** and **Orders** with no database (in-memory), fully documented with
OpenAPI/Swagger, and integrated with **Gemini** for natural language → REST API invocation.

---

## Requirements

- Java 17+
- Maven 3.8+
- Google Cloud project with Vertex AI enabled

---

## Configuration

Edit `src/main/resources/application.properties`:

```properties
gemini.project-id=YOUR_GOOGLE_CLOUD_PROJECT_ID
gemini.location=us-central1
gemini.model=gemini-1.5-pro
```

Also authenticate with Google Cloud before running:

```bash
gcloud auth application-default login
```

---

## Run

```bash
mvn spring-boot:run
```

---

## How It Works

```
Startup:
  App reads /v3/api-docs (OpenAPI spec)
  → Converts every endpoint to a Gemini FunctionDeclaration automatically
  → Gemini now knows about all your REST endpoints

Request:
  POST /api/nl/command { "message": "show pending orders for customer 42" }
  → Sent to Gemini with all function declarations
  → Gemini returns: call get_orders(customerId=42, status=pending)
  → App executes: GET /api/orders?customerId=42&status=pending
  → Result returned to Gemini → natural language response
  → User gets: "Customer 42 has 2 pending orders: ..."
```

---

## Natural Language Endpoint

```
POST /api/nl/command
{ "message": "your natural language instruction" }
```

**Example inputs:**

- `"Show me all products"`
- `"Find products with laptop in the name"`
- `"Get all pending orders for customer 42"`
- `"Create an order for customer 55, product 1, quantity 2"`
- `"Update order 101 status to shipped"`
- `"Delete product 3"`

---

## Other Endpoints

### Swagger UI

```
http://localhost:8080/swagger-ui.html
```

### OpenAPI JSON Spec

```
http://localhost:8080/v3/api-docs
```

### Products `/api/products`

| Method | Path                       | Description       |
|--------|----------------------------|-------------------|
| GET    | /api/products              | Get all products  |
| GET    | /api/products/{id}         | Get product by ID |
| GET    | /api/products/search?name= | Search by name    |
| POST   | /api/products              | Create a product  |
| PUT    | /api/products/{id}         | Update a product  |
| DELETE | /api/products/{id}         | Delete a product  |

### Orders `/api/orders`

| Method | Path                    | Description                                   |
|--------|-------------------------|-----------------------------------------------|
| GET    | /api/orders             | Get all orders (filter by customerId, status) |
| GET    | /api/orders/{id}        | Get order by ID                               |
| POST   | /api/orders             | Create an order                               |
| PATCH  | /api/orders/{id}/status | Update order status                           |
| DELETE | /api/orders/{id}        | Delete an order                               |

---

## Key Files

| File                                   | Purpose                                                    |
|----------------------------------------|------------------------------------------------------------|
| `gemini/OpenApiToGeminiConverter.java` | Reads OpenAPI spec → generates Gemini FunctionDeclarations |
| `gemini/GeminiOrchestrator.java`       | Calls Gemini, executes REST call, returns NL response      |
| `gemini/NlCommandController.java`      | Exposes `POST /api/nl/command` endpoint                    |
