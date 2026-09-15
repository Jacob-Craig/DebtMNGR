# DebtMNGR

A minimalist, double-entry bookkeeping debt manager application for tracking and settling shared expenses.

## Tech Stack
- **Language:** Kotlin (JVM 17)
- **Framework:** Spring Boot
- **Database:** PostgreSQL 15
- **Build Tool:** Gradle (Kotlin DSL)
- **Frontend:** Thymeleaf

## Getting Started

### Prerequisites
- JDK 17+
- Docker & Docker Compose

### 1. Start Infrastructure
Start the local PostgreSQL database:
```bash
docker compose up -d
```

### 2. Run the Application
```bash
./gradlew bootRun
```
The app will start at `http://localhost:8080`.

### 3. Run Tests
```bash
./gradlew test
```

## Architecture & Development Guidelines
- For development guidelines, layered architecture constraints, and agent operating instructions, see [AGENTS.md](AGENTS.md).
- For common build issues and environment fixes (e.g. JDK toolchain errors), see [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md).

