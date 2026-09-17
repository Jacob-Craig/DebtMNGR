# DebtMNGR

A minimalist, double-entry bookkeeping debt manager application for tracking and settling shared expenses.

## Tech Stack
- **Language:** Kotlin 2.x (JVM 17)
- **Framework:** Spring Boot
- **Database:** PostgreSQL 15
- **Build Tool:** Gradle (Kotlin DSL)
- **Frontend:** Thymeleaf & Tailwind CSS

---

## How to Run in Your Browser

### Prerequisites
- **Docker & Docker Compose** (must be installed and running)
- **JDK 17+** (see [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) if Gradle reports toolchain or Java home issues)

### 1. Start the Database
Start the local PostgreSQL container in the background:
```bash
docker compose up -d
```
> PostgreSQL will run on port `5432` with database `debtmngr_db`.

### 2. Build & Launch the Application
Compile the code and start the Spring Boot web server:
```bash
./gradlew bootRun
```
Wait a few seconds until the console displays:
```text
Started DebtMngrApplicationKt in ... seconds
```

### 3. Open in Your Browser
Open your web browser and visit:
[http://localhost:8080](http://localhost:8080)

From the browser you can:
- **View Dashboard (`/`)**: See your active groups, or an empty state callout if no groups exist yet.
- **Create Group (`/groups/new`)**: Click **"+ Create Group"** to add a new group with a name and optional description. When submitted, the group is saved to the PostgreSQL database and immediately listed on the dashboard.

### 4. Stopping the Application & Services
- **Stop Web Server:** Press `Ctrl + C` in the terminal where `./gradlew bootRun` is running.
- **Stop Database Container:**
  ```bash
  docker compose down
  ```

---

## Database Management

### Clearing / Resetting the Database

If you want to start with a fresh, empty database:

- **Option A (Container wipe & fresh volume - Recommended):**
  Stops the database and destroys any persisted volume data, then starts a brand-new PostgreSQL instance:
  ```bash
  docker compose down -v
  docker compose up -d
  ```

- **Option B (Reset schema without restarting container):**
  Drops all tables/sequences and recreates the `public` schema in the running container:
  ```bash
  docker compose exec -T db psql -U postgres -d debtmngr_db -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
  ```
  *(When you next start or run the app, Hibernate's `ddl-auto=update` and the built-in initializers will recreate the tables and default categories automatically).*

---

### Saving & Restoring Database State

#### 1. Save (Backup) Database State
To export a snapshot of all groups, participants, transactions, entries, and audit logs:

- **Plain SQL Dump:**
  ```bash
  docker compose exec -T db pg_dump -U postgres -d debtmngr_db > backup.sql
  ```

- **Custom Binary Format (Compressed):**
  ```bash
  docker compose exec -T db pg_dump -U postgres -Fc -d debtmngr_db > backup.dump
  ```

#### 2. Restore Database State
To restore from a previous snapshot:

- **From Plain SQL Dump:**
  ```bash
  # Clear existing schema first (recommended to avoid conflict with existing data)
  docker compose exec -T db psql -U postgres -d debtmngr_db -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

  # Restore data
  docker compose exec -T db psql -U postgres -d debtmngr_db < backup.sql
  ```

- **From Custom Binary Archive:**
  ```bash
  docker compose exec -T db pg_restore -U postgres -d debtmngr_db --clean --if-exists backup.dump
  ```

---

## Testing & Verification

Run the full test suite (unit tests, `@DataJpaTest` repository tests, `@WebMvcTest` controller tests, and end-to-end integration tests):
```bash
./gradlew test
```

Build and compile without running tests:
```bash
./gradlew build -x test
```

---

## Architecture & Documentation
- **Architecture & Operating Standards:** [AGENTS.md](AGENTS.md)
- **Domain Ubiquitous Language:** [CONTEXT.md](CONTEXT.md)
- **Troubleshooting & Environment Setup:** [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)
- **Feature Specs & Ticket Tracker:** [.scratch/debtmngr-mvp/spec.md](.scratch/debtmngr-mvp/spec.md)
