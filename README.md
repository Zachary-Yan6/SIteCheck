# SiteCheck — Backend

[![Backend CI](https://github.com/Zachary-Yan6/SIteCheck/actions/workflows/ci.yml/badge.svg)](https://github.com/Zachary-Yan6/SIteCheck/actions/workflows/ci.yml)

REST API for **SiteCheck**, an offline-first safety inspection app for construction sites.

Inspectors work through checklists on their phone, mark each item Pass / Fail / N/A, photograph failures, sign, and submit — all with or without signal. The phone queues everything locally and syncs when it gets back online. Managers see failed items across every site, download PDF reports and decide which inspector covers which site.

> The mobile app (Expo / React Native) lives in a separate repository: **[SiteCheckFrontend](https://github.com/Zachary-Yan6/SiteCheckFrontend)**.

---

## Contents

- [Tech stack](#tech-stack)
- [Features](#features)
- [How offline sync works](#how-offline-sync-works)
- [Getting started](#getting-started)
- [Demo accounts](#demo-accounts)
- [API overview](#api-overview)
- [Configuration](#configuration)
- [Data model](#data-model)
- [Project structure](#project-structure)
- [Testing and quality gates](#testing-and-quality-gates)
- [CI](#ci)

---

## Tech stack

| Area          | Choice |
|---------------|--------|
| Language      | Java 21 |
| Framework     | Spring Boot 3.5 (Web, Validation, Security, Actuator) |
| Auth          | Stateless JWT (jjwt) + BCrypt password hashes |
| Database      | PostgreSQL 17, schema managed by Flyway |
| Data access   | MyBatis-Plus |
| File storage  | AWS S3 through presigned URLs (LocalStack for local development) |
| Push          | Expo Push Service |
| Reports       | OpenPDF |
| API docs      | springdoc-openapi (Swagger UI) |
| Tests         | JUnit 5, Mockito, Testcontainers (PostgreSQL + LocalStack) |
| Quality       | JaCoCo (90% line and branch coverage gate), SpotBugs |
| CI / Deploy   | GitHub Actions, multi-stage Docker image |

## Features

- **Accounts and roles** — two roles, `INSPECTOR` and `MANAGER`. Anyone can sign up, but only as an inspector, and a new inspector sees no sites until a manager assigns them. Every request after login carries a Bearer JWT.
- **Offline bootstrap** — one call returns everything the app caches in SQLite: assigned sites, templates, checklist items and due dates.
- **Idempotent submit** — inspections are identified by a UUID generated on the phone. The first submit returns `201`; any retry returns `200` with the same receipt, so the app's sync queue can always safely try again.
- **Direct-to-S3 photos** — the app requests a short-lived (10 min) presigned PUT URL, uploads the JPEG straight to S3, then confirms it. The bucket stays private and photo bytes never pass through the API.
- **Site access control** — inspectors only see sites they are assigned to; anything else returns `403`.
- **Daily push reminders** — a scheduled job runs at 07:00 Europe/Dublin and pushes reminders for checks that are overdue, due today or due tomorrow to each inspector's registered devices. A reminder log makes sure nobody is notified twice on the same day.
- **Manager tools** — failed items filterable by site and date range, a downloadable PDF report per site, and inspector-to-site assignment.
- **Consistent errors** — every error is returned as RFC 7807 `application/problem+json`.

## How offline sync works

```
 Phone (offline)                         Backend                         S3
 ───────────────                         ───────                         ──
 GET  /api/sync/bootstrap  ───────────▶  sites, templates, due dates
 ...inspect, photograph, sign...
 (queued in SQLite)
 POST /api/inspections  ──────────────▶  store once per UUID
                         ◀──────────────  receipt: photos still needed
 POST /api/photos/{id}/upload-url ────▶  presigned PUT URL
 PUT  <presigned URL>  ─────────────────────────────────────────────▶  JPEG
 POST /api/photos/{id}/confirm  ──────▶  HEAD object, mark UPLOADED
```

Every step can be repeated without side effects, so "try again" is a correct recovery for any failure — including the app being killed in the middle of a sync. The receipt tells the phone which photos the server still needs, so finished uploads are skipped. A confirm for a photo that never reached S3 returns `409`, and the phone uploads it again.

## Getting started

### Prerequisites

- JDK 21
- Docker (for PostgreSQL and LocalStack, and for the integration tests)

### 1. Start the infrastructure

```bash
docker compose up -d
```

This starts:

- **PostgreSQL** on `localhost:5433` (database, user and password are all `sitecheck`)
- **LocalStack S3** on `localhost:4566`, with the `sitecheck-photos` bucket created automatically

`docker compose down` stops them and keeps the data; `docker compose down -v` also wipes the database.

### 2. Run the API

```bash
./mvnw spring-boot:run
```

On startup Flyway creates the schema and loads the demo data. The API is served at `http://localhost:8080`.

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health check: http://localhost:8080/actuator/health

### Or run everything in Docker

```bash
docker compose --profile app up -d --build
```

### Testing from a phone

A presigned URL is only valid for the exact host it was signed for, so the phone must be able to reach that host:

- **Android emulator** — `S3_PRESIGN_ENDPOINT=http://10.0.2.2:4566` (the default under the `app` compose profile)
- **Real phone on the same Wi-Fi** — `S3_PRESIGN_ENDPOINT=http://<your PC's LAN IP>:4566`

## Demo accounts

All demo accounts use the password `Password123!`.

| Email | Role | Sites |
|-------|------|-------|
| `aoife.murphy@sitecheck.demo`  | Inspector | Dublin Docklands, Cherrywood, Cork |
| `liam.obrien@sitecheck.demo`   | Inspector | Cork, Galway |
| `siobhan.kelly@sitecheck.demo` | Manager   | All sites |

The two inspectors share only the Cork site, which makes it easy to demo both "my site" and "someone else's site → `403`".

## API overview

Every endpoint needs `Authorization: Bearer <token>` except register, login, health and the API docs.

| Method | Path | Role | Purpose |
|--------|------|------|---------|
| POST   | `/api/auth/register` | public | Sign up as an inspector; returns a JWT like login (`409` if the email is taken) |
| POST   | `/api/auth/login` | public | Exchange email and password for a JWT |
| GET    | `/api/auth/me` | any | Current user |
| GET    | `/api/sync/bootstrap` | any | Data for the app's offline cache |
| GET    | `/api/sites/{siteId}/inspections?limit=` | any\* | Site inspection history, newest first |
| POST   | `/api/inspections` | Inspector | Submit a signed inspection (idempotent) |
| POST   | `/api/photos/{photoId}/upload-url` | Inspector | Get a presigned S3 PUT URL |
| POST   | `/api/photos/{photoId}/confirm` | Inspector | Confirm the upload (`409` = upload again) |
| POST   | `/api/devices` | any | Register an Expo push token |
| DELETE | `/api/devices/{token}` | any | Unregister a push token |
| GET    | `/api/manager/failed-items?siteId=&from=&to=` | Manager | Failed checklist items (default: last 30 days) |
| GET    | `/api/manager/sites/{siteId}/report?from=&to=` | Manager | PDF site report (default: last 30 days) |
| GET    | `/api/manager/inspectors` | Manager | All inspector accounts, including new sign-ups |
| GET    | `/api/manager/sites/{siteId}/inspectors` | Manager | Inspectors assigned to a site |
| PUT    | `/api/manager/sites/{siteId}/inspectors/{userId}` | Manager | Assign an inspector to a site (`204`, idempotent) |
| DELETE | `/api/manager/sites/{siteId}/inspectors/{userId}` | Manager | Unassign an inspector (`204`, idempotent) |

\* Inspectors only see sites they are assigned to.

Request and response schemas are in Swagger UI.

### Quick try with curl

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"aoife.murphy@sitecheck.demo","password":"Password123!"}' | jq -r .token)

curl -s http://localhost:8080/api/sync/bootstrap -H "Authorization: Bearer $TOKEN" | jq
```

## Configuration

All settings come from environment variables. The defaults in `application.yml` point at the local docker-compose services.

| Variable | Default | Notes |
|----------|---------|-------|
| `DB_URL` | `jdbc:postgresql://localhost:5433/sitecheck` | |
| `DB_USERNAME` / `DB_PASSWORD` | `sitecheck` / `sitecheck` | |
| `JWT_SECRET` | dev-only value | **Must be set in any deployed environment** (32+ bytes) |
| `JWT_TTL` | `PT24H` | ISO-8601 duration |
| `S3_BUCKET` | `sitecheck-photos` | |
| `AWS_REGION` | `eu-west-1` | |
| `S3_ENDPOINT` | `http://localhost:4566` | Set to empty on AWS to use real S3 and the instance role |
| `S3_PRESIGN_ENDPOINT` | *(empty)* | Host used in presigned URLs — see [Testing from a phone](#testing-from-a-phone) |
| `EXPO_ACCESS_TOKEN` | *(empty)* | Only needed if Expo enhanced push security is on |
| `REMINDERS_ENABLED` | `true` | |
| `REMINDERS_CRON` | `0 0 7 * * *` | Evaluated in Europe/Dublin time |

## Data model

Flyway migrations live in `src/main/resources/db/migration/`:

| Migration | Tables |
|-----------|--------|
| `V1` | `users`, `sites`, `site_inspectors`, `inspection_templates`, `template_items`, `site_templates` |
| `V2` | Demo seed data |
| `V3` | `inspections`, `inspection_results`, `photos` |
| `V4` | `device_tokens`, `reminder_log` |

Inspections and photos use UUID primary keys generated on the phone, which is what makes submitting idempotent. Each inspection keeps both the phone's timestamps (`started_at`, `submitted_at`) and the server's `received_at`, since an inspection can be signed days before it is uploaded.

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
├── config/       Security, S3, JWT, push and MyBatis configuration
└── exception/    API exceptions and the global problem+json handler
src/main/resources/db/migration/   Flyway migrations
docker/localstack/                 LocalStack init script (creates the S3 bucket)
```

## Testing and quality gates

```bash
./mvnw verify
```

- Integration tests use **Testcontainers** to start real PostgreSQL and LocalStack containers, so Docker must be running.
- **JaCoCo** fails the build below 90% line or branch coverage.
- **SpotBugs** (max effort, medium threshold) fails the build on any finding not listed in `spotbugs-exclude.xml`.

## CI

`.github/workflows/ci.yml` runs on every push to `main`/`master` and on every pull request:

1. `./mvnw verify` — all tests (Testcontainers on the GitHub runner) plus the coverage and SpotBugs gates.
2. A Docker image build, to make sure the image still builds. The image is not pushed to a registry yet.
