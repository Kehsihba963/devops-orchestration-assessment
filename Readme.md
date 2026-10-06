# End-to-End DevOps Orchestration

A Spring Boot REST API backed by PostgreSQL, containerized with Docker and served through an Nginx reverse proxy at a custom local domain (`devopsclass.com`).

## Architecture

```
Browser
  │  http://devopsclass.com
  ▼
hosts file: devopsclass.com → 127.0.0.1
  │
  ▼  port 80 (only port published to the host)
┌─────────────┐      ┌─────────────────┐      ┌────────────────┐
│ nginx-proxy │ ───▶ │   backend-api   │ ───▶ │   db-service   │
│ nginx:stable│      │ Spring Boot :8080│     │ postgres:16 :5432│
└─────────────┘      └─────────────────┘      └────────────────┘
        └───────── private Docker network (Compose DNS) ─────────┘
```

Only Nginx is reachable from the host. The backend and database are visible only inside the Docker network.

## Tech stack

| Component | Technology |
|---|---|
| Backend | Spring Boot 4.x, Spring Web, Spring Data JPA |
| Language | Java 21 |
| Database | PostgreSQL 16 (Alpine) |
| Reverse proxy | Nginx (stable-alpine) |
| Build | Maven (multi-stage Docker build) |
| Orchestration | Docker Compose |

## Project structure

```
devops assessment/
├── docker-compose.yml          # Orchestrates the three services
├── .env                        # Database credentials (not committed)
├── .env.example                # Template for .env
├── nginx/
│   └── nginx.conf              # Reverse proxy configuration
└── student-api/
    └── student-api/            # Spring Boot project
        ├── Dockerfile          # Multi-stage build, non-root runtime
        ├── .dockerignore
        ├── pom.xml
        └── src/main/
            ├── java/com/example/student_api/
            │   ├── StudentApiApplication.java   # Entry point + seed data
            │   ├── Student.java                 # JPA entity
            │   ├── StudentRepository.java       # Data access
            │   └── StudentController.java       # REST endpoints
            └── resources/application.properties
```

## What each part does

**Spring Boot app**
- `Student` is an `@Entity` mapped to the `students` table (`id`, `name`, `email`).
- `StudentRepository` extends `JpaRepository`, giving CRUD methods without writing SQL.
- `StudentController` exposes the endpoints below.
- `StudentApiApplication` seeds two students (Alice, Bob) on first start if the table is empty.
- `application.properties` reads all connection details from environment variables using the `${VARIABLE:default}` format, so nothing sensitive is hardcoded. Hibernate's `ddl-auto=update` creates the `students` table automatically.

**Dockerfile (multi-stage)**
- Stage 1 uses `maven:3.9-eclipse-temurin-21` to compile and package the jar. Dependencies are downloaded in a separate layer so rebuilds are fast.
- Stage 2 uses `eclipse-temurin:21-jre-alpine` and copies in only the jar, keeping the image small.
- The app runs as an unprivileged user (`spring`), not root.

**docker-compose.yml**
- `db-service`: Postgres, configured through `.env`, with a `pg_isready` healthcheck. It runs with `log_statement=ddl` so table creation appears in its logs.
- `backend-api`: built from the Java project. Receives `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and starts only once the database is healthy (`condition: service_healthy`).
- `nginx-proxy`: mounts `nginx.conf` read-only and publishes port 80.

**nginx.conf**
- Listens on port 80 for `server_name devopsclass.com` and forwards requests to `http://backend-api:8080`, passing along the original host and client IP headers.

## API endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/` | Health message |
| GET | `/students` | List all students (JSON) |
| POST | `/students` | Create a student (JSON body: `name`, `email`) |

## Prerequisites

- Docker Desktop (running) with Compose v2
- Java 21 and Maven (only needed for the optional local build)

## Setup and run

### 1. Create the `.env` file

Copy `.env.example` to `.env` and set your values:

```env
POSTGRES_USER=student_user
POSTGRES_PASSWORD=ChangeMe123
POSTGRES_DB=studentdb
```

### 2. Add the local DNS entry

Add this line to your hosts file (administrator rights required):

- Windows: `C:\Windows\System32\drivers\etc\hosts`
- Linux/Mac: `/etc/hosts`

```
127.0.0.1 devopsclass.com
```

Make sure no other active line maps `devopsclass.com` to a different IP, since the first match wins.

### 3. Start everything

From the project root:

```bash
docker compose up --build
```

Wait for `Started StudentApiApplication` in the backend logs.

### 4. Test

Open in a browser:

- http://devopsclass.com
- http://devopsclass.com/students

Or from a terminal:

```bash
curl http://devopsclass.com/students
```

Add a student:

```bash
curl -X POST http://devopsclass.com/students \
  -H "Content-Type: application/json" \
  -d '{"name":"Carol","email":"carol@example.com"}'
```

## Verification

**Table created by the Java app** (from the db-service logs):

```bash
docker compose logs db-service
```

Look for:

```
LOG:  execute <unnamed>: create table students (id bigint generated by default as identity, email varchar(255), name varchar(255), primary key (id))
```

To see this on a clean start, remove the data volume first (`docker compose down -v`), then run `docker compose up --build` again. If the volume already exists, the table is not created a second time.

**Query the database directly:**

```bash
docker compose exec db-service psql -U student_user -d studentdb -c "\dt"
docker compose exec db-service psql -U student_user -d studentdb -c "SELECT * FROM students;"
```

**Backend runs as non-root:**

```bash
docker compose exec backend-api whoami
# spring
```

## Build the jar locally (optional)

```bash
cd student-api/student-api
mvn clean install -DskipTests
```

`-DskipTests` is used because the default generated test starts the full app and needs a running database.

## Troubleshooting

| Problem | Cause / fix |
|---|---|
| `docker ps` cannot connect to the daemon | Docker Desktop is not running. Start it and wait for "Engine running". |
| Browser times out on `devopsclass.com` | The domain resolves elsewhere. Run `ping devopsclass.com`; it must show `127.0.0.1`. Check for an older conflicting hosts entry, then run `ipconfig /flushdns`. |
| Port 80 already in use | Stop the other service (IIS, Apache, Skype), or change the mapping to `"8080:80"` and browse to `devopsclass.com:8080`. |
| `password authentication failed` | `.env` was changed after the volume was created. Run `docker compose down -v` and start again. |
| Backend cannot reach the database | `DB_URL` must use the host `db-service`, not `localhost`. |
| Nginx: `host not found in upstream` | The backend crashed or is not running. Check `docker compose logs backend-api`. |

## Cleanup

```bash
docker compose down        # stop and remove containers
docker compose down -v     # also delete the database volume
```

## Security notes

- Credentials live in `.env` and are injected at runtime. Never commit `.env`; add it to `.gitignore` and share `.env.example` instead.
- The application container runs as a non-root user.
- Only port 80 is published; the database and backend are not exposed to the host.
