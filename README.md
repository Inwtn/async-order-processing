# Async Order Processing

[![Java CI](https://github.com/Inwtn/async-order-processing/actions/workflows/ci.yml/badge.svg)](https://github.com/Inwtn/async-order-processing/actions/workflows/ci.yml)

An event-driven order processing demo built with **Java 17, Spring Boot, Kafka, and PostgreSQL**. The API accepts an order immediately and returns `202 Accepted`; a Kafka consumer processes it afterward. An outbox record is committed with each new order, so a broker outage does not silently discard the order event.

## What this demonstrates

- `POST /api/orders` accepts multiple line items and calculates the total.
- An `Idempotency-Key` prevents duplicate orders on safe client retries. Reusing a key with different content returns `409 Conflict`.
- A transactional outbox stores an event in PostgreSQL alongside the order. A scheduled publisher sends pending events to Kafka and marks them published only after an acknowledgment.
- The consumer updates the order through `RECEIVED → PROCESSING → COMPLETED`.
- Spring Kafka retry topics retry failed processing twice before routing to a dead-letter topic; the DLT handler marks the order `FAILED`.
- Duplicate events are safe: completed orders are not processed again.
- Flyway migrations define the schema. PostgreSQL integration tests cover idempotency, the outbox, transient retry behavior, and terminal failures.

The processor has explicit `failureMode` values for demonstrating retry behavior: `NONE`, `TRANSIENT` (fails once), and `PERMANENT` (always fails). It does not charge a card, reserve inventory, or fulfill real customer orders.

## Run locally

Install Docker and Docker Compose. Copy `.env.example` to `.env` and replace the PostgreSQL password, then run:

```bash
docker compose up --build
```

The API is at `http://localhost:8081`; its health endpoint is `http://localhost:8081/actuator/health`. Compose also starts PostgreSQL and a single-node Kafka broker for local development.

Create a normal order:

```bash
curl -i -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-order-001" \
  -d '{"customerId":"customer-42","items":[{"sku":"BOOK-1","quantity":2,"unitPrice":19.90},{"sku":"PEN-2","quantity":1,"unitPrice":5.00}],"failureMode":"NONE"}'
```

The response includes an order ID and a `Location` header. Poll `GET /api/orders/{id}` until the status becomes `COMPLETED`. Repeat the exact POST with the same key to receive the same order; changing the body while keeping the key returns `409`.

To inspect retries, create orders with new keys and `"failureMode":"TRANSIENT"` or `"failureMode":"PERMANENT"`. A transient failure completes after a retry. A permanent failure ends in `FAILED` with a recorded reason after the DLT handler runs.

## API

| Method | Path | Result |
| --- | --- | --- |
| `POST` | `/api/orders` | `202 Accepted` for a new order; `200 OK` for an idempotent replay; `409 Conflict` for a key reused with different content |
| `GET` | `/api/orders/{id}` | Current status, total, attempt count, and failure reason |
| `GET` | `/actuator/health` | Health status |

The local demo has no authentication and should be bound behind an authenticated gateway before public deployment. Kafka and PostgreSQL use local development configuration in Compose.

## Delivery guarantees

The outbox and Kafka give **at-least-once** event delivery. A crash after Kafka acknowledges a send but before the outbox update can publish the event again, so the consumer checks the persisted order status. This project uses one outbox publisher instance; a multi-instance deployment would also need a claim or lock strategy for pending rows. Retry topics can change event ordering, as described in the [Spring Kafka retry documentation](https://docs.spring.io/spring-kafka/reference/retrytopic/how-the-pattern-works.html).

## Tests

```bash
mvn --batch-mode --no-transfer-progress verify
```

Integration tests use PostgreSQL Testcontainers and require Docker. GitHub Actions runs the tests for pull requests and pushes to `main`.

## License

MIT. See [LICENSE](LICENSE).
