# Backend

Spring Boot API for BudgetSpace AI.

## Requirements

- Java 17+
- Maven
- PostgreSQL running from root `docker-compose.yml`

## Run

```bash
mvn spring-boot:run
```

## Endpoints

```text
GET  /api/products
POST /api/plans/generate
POST /api/plans/replace
```

## Notes

The app currently uses `ddl-auto: create`, so the database is recreated and reseeded on startup. This is intentional for the MVP.
