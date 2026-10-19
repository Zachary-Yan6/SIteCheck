# SiteCheck – Backend

REST API for **SiteCheck**, an offline-first construction site safety inspection app. Inspectors fill in checklists and take photos on their phone, even with no signal. The app syncs everything when it's back online. Managers can review failed items across all sites and download PDF reports.

The mobile client (Expo / React Native) lives in a separate repository: **SiteCheckFrontEnd**.

## Tech stack

| Area          | Choice |
|---------------|--------|
| Language      | Java 21 |
| Framework     | Spring Boot 3.5 (Web, Validation, Security, Actuator) |
| Auth          | Stateless JWT (jjwt) + BCrypt |
| Database      | PostgreSQL 17, migrations with Flyway |
| Data access   | MyBatis-Plus |
| File storage  | AWS S3 via presigned URLs (LocalStack locally) |
| Push          | Expo Push Service |
| Reports       | OpenPDF |
| API docs      | springdoc-openapi (Swagger UI) |
| Tests         | JUnit 5, Mockito, Testcontainers (Postgres + LocalStack) |
| CI / Deploy   | GitHub Actions, multi-stage Docker image |

## Features

- **Sign-up, login and roles**: two roles, `INSPECTOR` and `MANAGER`. Anyone can sign up, but only as an Inspector, and a new inspector sees no sites until a manager assigns them to one. Every request after login carries a Bearer JWT.
- **Offline bootstrap**: one call returns everything the app caches in SQLite, including assigned sites, templates, checklist items and due dates.
- **Idempotent inspection submit**: the app's sync queue can safely retry. The first call returns `201`, and each retry returns `200` with the same receipt.
- **Direct-to-S3 photos**: the app asks for a short-lived presigned PUT URL, uploads the JPEG straight to S3 and then confirms it. The bucket stays private.
- **Site access control**: inspectors can only see the sites they are assigned to. Anyone else's site returns `403`.
- **Daily push reminders**: a scheduled job runs at 07:00 Europe/Dublin. It pushes a reminder about checks that are overdue, due today or due tomorrow to each inspector's registered devices.
- **Manager dashboard**: a list of failed items, filterable by site and date range, plus a downloadable PDF report per site.
- **Consistent errors**: all errors are returned as RFC 7807 `application/problem+json`.

## Getting started

### Prerequisites

- JDK 21
- Docker (for Postgres and LocalStack, and for running the tests)

### 1. Start the infrastructure

```bash
docker compose up -d
```

This starts:
- **PostgreSQL** on `localhost:5433` (db/user/password: `sitecheck`)
- **LocalStack S3** on `localhost:4566`. The `sitecheck-photos` bucket is created automatically.

`docker compose down` stops them and keeps the data. `docker compose down -v` also wipes the database.

### 2. Run the app

```bash
./mvnw spring-boot:run
```

On startup, Flyway creates the schema and loads the demo data. The API is served at `http://localhost:8080`.

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

### Or run everything in Docker

```bash
docker compose --profile app up -d --build
```

### Testing from a phone

Presigned URLs only work for the exact host they were signed for, so the phone has to be able to reach that host:

- **Android emulator**: `S3_PRESIGN_ENDPOINT=http://10.0.2.2:4566` (the default under the `app` compose profile)
- **Real phone on the same Wi-Fi**: `S3_PRESIGN_ENDPOINT=http://<your PC's LAN IP>:4566`

## Demo accounts

All demo accounts use the password `Password123!`.

| Email | Role | Sites |
|-------|------|-------|
| `aoife.murphy@sitecheck.demo`  | Inspector | Dublin Docklands, Cherrywood, Cork |
| `liam.obrien@sitecheck.demo`   | Inspector | Cork, Galway |
| `siobhan.kelly@sitecheck.demo` | Manager   | All sites |

The two inspectors share only the Cork site. That makes it easy to demo both the "own site" case and the "someone else's site → 403" case.

## API overview

All endpoints need `Authorization: Bearer <token>` except register, login, health and the API docs.

| Method | Path | Role | Purpose |
|--------|------|------|---------|
| POST   | `/api/auth/register` | public | Sign up as an Inspector; returns a JWT like login (`409` if the email is taken) |
| POST   | `/api/auth/login` | public | Exchange email and password for a JWT |
| GET    | `/api/auth/me` | any | Current user |
| GET    | `/api/sync/bootstrap` | any | Data to fill the app's offline cache |
| GET    | `/api/sites/{siteId}/inspections?limit=` | any* | Site inspection history, newest first |
| POST   | `/api/inspections` | Inspector | Submit a signed inspection (idempotent) |
| POST   | `/api/photos/{photoId}/upload-url` | Inspector | Get a presigned S3 PUT URL |
| POST   | `/api/photos/{photoId}/confirm` | Inspector | Confirm the upload (`409` = upload again) |
| POST   | `/api/devices` | any | Register an Expo push token |
| DELETE | `/api/devices/{token}` | any | Unregister a push token |
| GET    | `/api/manager/failed-items?siteId=&from=&to=` | Manager | Failed checklist items (default: last 30 days) |
| GET    | `/api/manager/sites/{siteId}/report?from=&to=` | Manager | PDF site report (default: last 30 days) |
| GET    | `/api/manager/inspectors` | Manager | All inspector accounts, including new sign-ups |
| GET    | `/api/manager/sites/{siteId}/inspectors` | Manager | Inspectors assigned to a site |
| PUT    | `/api/manager/sites/{siteId}/inspectors/{userId}` | Manager | Assign an inspector to a site (`204`, safe to repeat) |
| DELETE | `/api/manager/sites/{siteId}/inspectors/{userId}` | Manager | Unassign an inspector from a site (`204`, safe to repeat) |

\* Inspectors only see sites they are assigned to.

See Swagger UI for the request and response schemas.

## Configuration

Settings come from environment variables. The defaults in `application.yml` point at the local docker-compose services.

| Variable | Default | Notes |
|----------|---------|-------|
| `DB_URL` | `jdbc:postgresql://localhost:5433/sitecheck` | |
| `DB_USERNAME` / `DB_PASSWORD` | `sitecheck` / `sitecheck` | |
| `JWT_SECRET` | dev-only value | **Must be set in any deployed environment** (32+ bytes) |
| `JWT_TTL` | `PT24H` | ISO-8601 duration |
| `S3_BUCKET` | `sitecheck-photos` | |
| `AWS_REGION` | `eu-west-1` | |
| `S3_ENDPOINT` | `http://localhost:4566` | Set it to empty on AWS to use real S3 and the instance role |
| `S3_PRESIGN_ENDPOINT` | *(empty)* | Host used in presigned URLs; see "Testing from a phone" above |
| `EXPO_ACCESS_TOKEN` | *(empty)* | Only needed if Expo enhanced push security is on |
| `REMINDERS_ENABLED` | `true` | |
| `REMINDERS_CRON` | `0 0 7 * * *` | Runs in Europe/Dublin time |

## Project structure

```
src/main/java/com/zachary/sitecheck/
├── controller/   REST endpoints
├── service/      Business logic (auth, sync, inspections, photos, reminders, reports)
├── mapper/       MyBatis-Plus mappers and query row types
├── entity/       Database entities
├── dto/          Request / response records
├── security/     JWT filter and authenticated principal
├── client/       External clients (S3 presigning, Expo Push)
├── job/          Scheduled reminder job
├── config/       Security, S3, JWT, push and MyBatis config
└── exception/    API exceptions and global problem+json handler
src/main/resources/db/migration/   Flyway migrations (V1–V4, V2 = demo seed)
```

## Testing

```bash
./mvnw verify
```

The integration tests use Testcontainers to start real PostgreSQL and LocalStack containers, so Docker must be running.

## CI

`.github/workflows/ci.yml` runs on every push to `main`/`master` and on every pull request:
1. `./mvnw verify` runs all tests, with Testcontainers on the GitHub runner.
2. It checks that the Docker image still builds. The image is not pushed anywhere yet.
