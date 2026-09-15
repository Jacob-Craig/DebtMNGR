# DebtMNGR: AI Agent Context & Operating Guidelines

## 1. Mission & Domain Model
- **Project:** A minimalist, double-entry bookkeeping debt manager application for tracking and settling shared expenses.
- **Core Principle:** Double-entry ledger invariant: Every transaction consists of balanced entries where total debits equal total credits ($\sum \text{debit} = \sum \text{credit}$). Account balances are derived from ledger entries.
- **Architecture Constraint:** Strict layered MVC:
  - `domain`: Core entities & ledger accounting rules (pure Kotlin, decoupled).
  - `repository`: Spring Data JPA repositories.
  - `service`: Business logic, balance calculations, transaction coordination. Never import or reference web-layer classes (`HttpServletRequest`, `Model`, `ModelAndView`, etc.).
  - `web` / `controller`: Thymeleaf controller endpoints handling view models and form validation. Designed to easily expose or transition into REST endpoints (`/api/...`).
- **Base Package:** `com.jacobcraig.debtmngr`

---

## 2. Toolchain & Environment Registry
- **Language:** Kotlin 2.x (JVM Toolchain: Java 17)
- **Framework:** Spring Boot (`build.gradle.kts`)
- **Build System:** Gradle Kotlin DSL (`./gradlew`). Never use Maven or create `pom.xml`.
- **Database:** PostgreSQL 15 running locally or via Docker.
- **ORM / DDL Strategy:** 
  - Current configuration uses Spring Data JPA with `spring.jpa.hibernate.ddl-auto=update` in `application.properties`.
  - *Target Schema Migration:* Flyway (`src/main/resources/db/migration`). *(Note: Flyway dependency will be introduced before production release. Always consult human before adding dependencies).*
- **Local Infrastructure:** Docker Compose (`docker-compose.yml`) providing PostgreSQL on port `5432`.

### Essential Commands
| Intent | Command | Notes |
| :--- | :--- | :--- |
| Build & Compile | `./gradlew build -x test` | Compiles Kotlin and resources |
| Run Test Suite | `./gradlew test` | Runs JUnit 5 test suite |
| Run Application | `./gradlew bootRun` | Starts application on port 8080 |
| Start Infrastructure | `docker compose up -d` | Starts PostgreSQL container (`debtmngr_postgres`) |
| Stop Infrastructure | `docker compose down` | Stops background docker services |

---

## 3. Judgment Boundaries & Architecture Rules

### NEVER (Hard Limits):
- **Secrets:** Never commit database credentials, API keys, or `.env` files into source control.
- **Layer Violations:** Never allow `service` or `repository` layers to depend on web MVC or presentation classes (`org.springframework.ui.Model`, `HttpServletResponse`, etc.).
- **Swallowed Exceptions:** Catch specific exceptions (e.g., `EntityNotFoundException`, `IllegalArgumentException`, `IllegalStateException`). Never write empty catch blocks or catch generic `Throwable` / `Exception` without rethrowing or handling properly.
- **Bypassing Double-Entry:** Never record an expense or transfer without balancing entries.

### ASK (Human-in-the-Loop):
- Ask before adding new external Gradle dependencies in `build.gradle.kts`.
- Ask before changing the database schema strategy (e.g. introducing Flyway or dropping database tables).
- Ask before restructuring existing package hierarchies.

### ALWAYS (Proactive Standards):
- **TDD & Test-First:** Follow Test-Driven Development (Red-Green-Refactor) bottom-up:
  1. Unit tests for pure domain logic & ledger balance rules.
  2. `@DataJpaTest` for Spring Data JPA repositories.
  3. Mockito/JUnit 5 unit tests for isolated service layer validation.
  4. `@WebMvcTest` for Thymeleaf/web controllers.
- **Kotlin Idioms:**
  - Prefer immutable values (`val`) over mutable variables (`var`).
  - Use Kotlin's null-safety features (`?`, `?:`, `let`). Avoid `!!` (force unwrapping).
  - Note: For JPA entities, declare mutable properties (`var`) where required by Hibernate proxy generation, and ensure classes are open (configured via the `allOpen` Gradle plugin for `@Entity`).
- **Leverage Skills:** Consult the project skills in `.agents/skills/`:
  - `.agents/skills/tdd/SKILL.md` for test-driven workflows.
  - `.agents/skills/code-review/SKILL.md` before finalizing major changes.
  - `.agents/skills/diagnosing-bugs/SKILL.md` when diagnosing unexpected failures.

---

## 4. Documentation & Workspace Layout
```
DebtMNGR/
├── .agents/skills/          # Agent skills (tdd, code-review, diagnosing-bugs, etc.)
├── docs/                    # Architecture records, feature specs, and troubleshooting
│   └── TROUBLESHOOTING.md   # Environment gotchas & build troubleshooting guide
├── src/main/
│   ├── kotlin/com/jacobcraig/debtmngr/
│   │   ├── DebtMngrApplication.kt
│   │   ├── domain/          # Entities & domain models (Account, Transaction, Entry)
│   │   ├── repository/      # Spring Data JPA repositories
│   │   ├── service/         # Business & ledger balance logic
│   │   └── web/             # MVC Controllers & View DTOs
│   └── resources/
│       ├── application.properties
│       ├── templates/       # Thymeleaf HTML views
│       └── static/          # CSS, JS, static assets
└── src/test/kotlin/com/jacobcraig/debtmngr/
```