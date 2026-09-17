# DebtMNGR

A minimalist, double-entry bookkeeping debt manager application for tracking and settling shared expenses.

This served as a multi-agent test project utilising the antigravity CLI. It demonstrates agentic capabilities by explicitly following Matt Pocock's workflow stages: `grill-with-docs`, `to-spec`, `to-tickets`, `implement`, and `code-review`.


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

Simple, one-line commands to dump, restore, and clear database state across **macOS**, **Linux**, and **Windows**.

### Method 1: Using Gradle (Simplest & Recommended)

Gradle runs identically across all platforms without shell quirks, pipe escaping, or character encoding issues:

| Action | macOS / Linux | Windows (CMD or PowerShell) | Description |
| :--- | :--- | :--- | :--- |
| **Dump Database** | `./gradlew dbDump` | `gradlew dbDump` | Dumps current DB to `backup.sql`. |
| **Restore Database** | `./gradlew dbRestore` | `gradlew dbRestore` | Resets schema & restores from `backup.sql`. |
| **Clear Database** | `./gradlew dbClear` | `gradlew dbClear` | Wipes tables and resets schema. |

> **Custom file path:** Add `-Pfile=my-backup.sql` to specify a custom filename (e.g. `./gradlew dbDump -Pfile=state_v1.sql`).

---

### Method 2: Using Helper Scripts

Dedicated helper scripts are also provided in the `scripts/` directory:

- **macOS & Linux:**
  ```bash
  ./scripts/db.sh dump [filename]      # Defaults to backup.sql
  ./scripts/db.sh restore [filename]   # Defaults to backup.sql
  ./scripts/db.sh clear                # Wipes public schema
  ```

- **Windows (Command Prompt / PowerShell):**
  ```cmd
  scripts\db.bat dump [filename]       # Defaults to backup.sql
  scripts\db.bat restore [filename]    # Defaults to backup.sql
  scripts\db.bat clear                 # Wipes public schema
  ```

---

### Method 3: Direct Docker Compose Commands

If you prefer raw Docker commands:

- **Clear / Fresh Volume:**
  ```bash
  docker compose down -v && docker compose up -d
  ```

- **Dump to SQL:**
  ```bash
  docker compose exec -T db pg_dump -U postgres -d debtmngr_db > backup.sql
  ```

- **Restore from SQL:**
  ```bash
  # Clear existing schema first:
  docker compose exec -T db psql -U postgres -d debtmngr_db -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
  # Restore:
  docker compose exec -T db psql -U postgres -d debtmngr_db < backup.sql
  ```
  *(Note for Windows PowerShell: use `Get-Content backup.sql | docker compose exec -T db psql -U postgres -d debtmngr_db` as `<` is not supported).*

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
