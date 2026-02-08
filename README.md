# Internal Fund Transfer Service

A robust, high-throughput fund transfer microservice built with Java 17, Spring Boot 3, and Redis. This service implements a multi-layered concurrency control strategy to ensure data integrity and prevent race conditions.

## 🚀 Features

-   **Triple-Layer Concurrency Control**:
    -   **Layer 1 (Distributed)**: Redisson locks on wallet IDs to prevent cross-pod race conditions.
    -   **Layer 2 (Database - Optimistic)**: JPA `@Version` to handle concurrent modifications.
    -   **Layer 3 (Database - Integrity)**: Atomic SQL updates (`SET balance = balance - amount WHERE id = :id AND balance >= :amount`) to prevent negative balances.
-   **Deadlock Prevention**: Deterministic lock ordering by sorting resource IDs.
-   **Idempotency & Resilience**: 
    -   Reference code tracking to prevent duplicate transactions.
    -   Automatic retries for optimistic locking failures.
-   **Enterprise Exception Handling**:
    -   Centralized `@RestControllerAdvice` for uniform error responses.
    -   Domain-driven exception hierarchy with descriptive `ErrorCode` enums.
-   **Observability**: Micrometer-based metrics for lock acquisition and transaction duration.
-   **Architecture**: Follows **Hexagonal/Clean Architecture** principles.

## 🛠 Tech Stack

-   **Java 17** (Eclipse Temurin)
-   **Spring Boot 3.2.2**
-   **Redis** (via Redisson)
-   **PostgreSQL 15**
-   **Testcontainers** (for local dev)
-   **Docker & Docker Compose**

## 🏃 How to Run the Project

### Prerequisites
-   Docker and Docker Compose installed.

### Start the Application
To start the application, database, and Redis:

```powershell
docker-compose up --build
```

The application will be available at `http://localhost:8080`.

### Health & Metrics
-   Health Check: `http://localhost:8080/actuator/health`
-   Prometheus Metrics: `http://localhost:8080/actuator/prometheus` (search for `fund_transfer`)

### Example Request (CURL)
```bash
curl -X POST http://localhost:8080/api/v1/transfers \
-H "Content-Type: application/json" \
-d '{
    "sourceWalletId": 1,
    "destinationWalletId": 2,
    "amount": 100.00,
    "referenceCode": "unique-tx-777"
}'
```

---

## 🧪 How to Run Tests

### 1. Local Environment
If you have Java 17 and Maven installed locally:
```powershell
mvn test
```

### 2. Containerized Tests (Clean Environment)
Run the full test suite inside a dedicated container that is already connected to the database and Redis:

```powershell
# Run all tests
docker compose --profile test run --rm test-runner test
```

### Specific Test Suites
| Test Class | Objective |
| :--- | :--- |
| `TransferServiceUnitTest` | Validates business logic isolation with mocks. |
| `TransferServiceConcurrencyIntegrationTest` | Stress tests 50 concurrent requests for the same account. |
| `DeadlockPreventionIntegrationTest` | Verifies mutual transfer (A↔B) scenarios. |
| `RaceConditionBalanceTest` | Ensures balance never drops below zero under race conditions. |

---

## 🛡️ Error Handling
The service returns a standard error response for all failures:

```json
{
    "message": "Insufficient balance or concurrency failure in wallet: 1",
    "errorCode": "INSUFFICIENT_BALANCE",
    "status": 422,
    "timestamp": "2026-02-08T20:30:45.123"
}
```

| Error Code | HTTP Status | Description |
| :--- | :--- | :--- |
| `WALLET_NOT_FOUND` | 404 | One of the wallet IDs does not exist. |
| `INSUFFICIENT_BALANCE` | 422 | Source wallet does not have enough funds. |
| `LOCK_ACQUISITION_FAILED`| 503 | Distributed locks could not be acquired (system under heavy load). |
| `INTERNAL_SERVER_ERROR` | 500 | Unexpected system failure. |

---

## 🏗 Project Structure
-   `domain.model`: Core entities and business rules.
-   `domain.exception`: Domain-driven exception definitions and `ErrorCode` enum.
-   `application.service`: Orchestration, transactions, and locking logic.
-   `infrastructure.persistence`: JPA repositories and custom SQL updates.
-   `infrastructure.web.exception`: Global exception handler and error response DTOs.
-   `infrastructure.config`: Redisson and system configuration.
