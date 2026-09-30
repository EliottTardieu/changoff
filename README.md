# Time to Chang

A personal strength and cardio tracker with an Angular / TypeScript frontend, Java 21 Spring Boot REST API, and PostgreSQL. Log weights and reps, graph your progress, and follow your complete training history. Enable optional ranks in your profile when you want benchmark targets.

## Set up from scratch

These steps start the entire site: Angular served by nginx, the Spring Boot API, and PostgreSQL. **You do not need to install Java, Maven, Node.js, or PostgreSQL on your machine.** Docker builds and runs them for you.

### 1. Prerequisites

Install Docker Engine or Docker Desktop with Docker Compose v2, and start Docker. You need internet access for the first build to download images and dependencies.

Check that both commands work:

```sh
docker --version
docker compose version
```

Copy or clone this repository onto your machine. Open a terminal in its root directory—the one containing `compose.yaml`, `backend/`, and `frontend/`. Run all commands below from that directory.

### 2. Configure the site

For a new installation, copy the example configuration:

```sh
cp .env.example .env
```

If you already have a `.env`, keep it rather than overwriting it. The `.env` file belongs beside `compose.yaml` and is read automatically by Docker Compose.

Edit it as needed:

```dotenv
PORT=8080
DB_PASSWORD=changoff-local
SECURE_COOKIE=false
```

- `PORT`: the port used to open the website. Choose another, such as `8081`, if `8080` is occupied.
- `DB_PASSWORD`: use your own password for a new installation. For an existing database volume, use its existing password: editing this setting does **not** change the password stored in PostgreSQL.
- `SECURE_COOKIE`: leave `false` for local HTTP. Use `true` when serving the site over HTTPS.

The database and database user are both named `changoff`. Only the website port is exposed; nginx forwards `/api` requests to the API, and the API reaches PostgreSQL over the internal Docker network.

### 3. Build and start everything

```sh
docker compose up --build -d
```

The first build can take several minutes. It installs dependencies, builds the frontend, and compiles and tests the backend. Later builds reuse cached layers.

Check startup:

```sh
docker compose ps
docker compose logs --tail=100 api
```

The database and API should be **healthy**, and the web container should be running. If startup fails, read the troubleshooting section below.

### 4. Open the site

Open **http://localhost:8080** (or your configured `PORT`). Choose **Create account**. There is no default administrator or demo login.

The application health check is available at **http://localhost:8080/api/health** and should return:

```json
{"status":"UP"}
```

You can now log strength sets, track runs, rides and jump-rope sessions in **Cardio**, and use **WOD workshop** to generate, save, schedule, and share workouts.

## How database initialization works

**The SQL scripts are called automatically. Do not run them manually.** There are two stages:

1. On the first start with an empty volume, the PostgreSQL container creates the `changoff` database and user using the settings in `compose.yaml`.
2. Once PostgreSQL is healthy, the API starts. Spring Boot loads Flyway, enabled by `spring.flyway.enabled=true` in `backend/src/main/resources/application.properties`. Flyway discovers SQL migrations from `backend/src/main/resources/db/migration/` and Java migrations from `backend/src/main/java/db/migration/` in version order, before the API becomes ready.

| Script | What it creates |
|---|---|
| `V1__initial.sql` | Accounts, sessions, and strength logs |
| `V2__wod_workshop.sql` | WOD tables, user preferences/restrictions, exercise catalog, levels, formats, templates, and prescriptions |
| `V3__optional_ranks.sql` | Per-user rank visibility preference, enabled by default |
| `V4__tracking_rep_range` (Java migration) | Expands tracked sets to 1–1,000 reps, preserving all existing records |
| `V5__cardio.sql` | User-owned cardio sessions with duration, distance/repetitions, notes and reference dataset |

Flyway records completed migrations in `flyway_schema_history`. On subsequent starts, it only applies new migrations; it does not recreate the database or reseed existing records. The strength-ranking exercise catalog is loaded separately from `backend/src/main/resources/exercises.json`.

To check which migrations ran:

```sh
docker compose exec db psql -U changoff -d changoff -c 'SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;'
```

If you want to initialize **only the database and API**, without starting the separate nginx frontend container:

```sh
docker compose up --build -d db api
```

Starting only `db` creates the database and user, but **does not run the application migrations**. Starting `api` is what runs them. This existing startup process is the database initialization mechanism; no extra script is required.

Do not edit an already-applied migration to change an existing installation. Add a new versioned migration instead, so Flyway can apply the change without losing data.

## Stop, restart, and update

Stop the site while keeping all accounts, logs, and WODs:

```sh
docker compose down
```

Bring it back with the same data:

```sh
docker compose up -d
```

After pulling or copying new application code:

```sh
docker compose up --build -d
```

New migrations run automatically. Data lives in the named `postgres_data` volume, not in the containers. Keep your `.env` and use the same Compose project name when restarting. By default, Compose derives that name from the repository directory; renaming or moving into a differently named directory can select a different volume and make the site appear empty. Use `docker compose -p YOUR_PREVIOUS_PROJECT_NAME ...` consistently if you need to retain the old project name.

## Optional: reset to a completely empty installation

**This deletes this Compose project's database volume, including all users, strength logs, WODs, results, and preferences.** Use it only if you want a fresh installation, not a normal restart. Back up first if you need the existing data.

```sh
docker compose down --volumes
docker compose up --build -d
```

PostgreSQL will create a new database, and Flyway will apply all migrations and seed the WOD catalog again. Create a new account afterward. You can change `DB_PASSWORD` in `.env` between these two commands because the replacement database has not been initialized yet.

## Optional: back up and restore

With the database running, create a SQL backup:

```sh
docker compose exec -T db pg_dump -U changoff -d changoff --no-owner --no-acl > changoff-backup.sql
```

Keep the backup outside version control and copy it somewhere durable. It contains account and training data, including password hashes. Preserve your `.env` separately.

To restore that backup on a **new, empty database volume**, before starting the API:

```sh
docker compose up -d db
# Wait until db is healthy in: docker compose ps
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U changoff -d changoff < changoff-backup.sql
docker compose up --build -d
```

Restore only into an empty application database. The backup includes Flyway's history, so the API will preserve restored tables and apply only migrations newer than the backup. If the API has already initialized the destination, stop and choose an empty destination rather than importing over populated tables.

## Troubleshooting startup

| Symptom | What to do |
|---|---|
| Docker cannot connect to its daemon | Start Docker Engine/Desktop, then retry. |
| Port already allocated | Change `PORT` in `.env`, rerun `docker compose up -d`, and open the new port. |
| Database or API stays unhealthy | Run `docker compose logs --tail=100 db api` and fix the reported error. |
| Password authentication failed | Use the password with which the existing volume was initialized. Changing `.env` alone does not change an existing database user's password. |
| Database or role `changoff` does not exist in an old volume | A volume from before the application rename may still use the old database/user names. Preserve and migrate that database, restore a backup into a fresh installation, or use the destructive reset above only if you do not need its data. |
| Flyway checksum mismatch | Restore the previously applied migration file, then add a new migration for the change. Do not delete migration history to bypass the error. |
| Website starts but looks empty after moving the repository | Check that you are using the same Compose project name and database volume as before. |
| Browser cannot reach the site | Check `docker compose ps`, the configured `PORT`, and `docker compose logs --tail=100 web`. |
| Sign-in does not persist on local HTTP | Set `SECURE_COOKIE=false` in `.env` and rerun `docker compose up -d`. |

## Included

- Registration, login, logout, bcrypt passwords, seven-day revocable sessions in HttpOnly / SameSite cookies; only session-token hashes are stored.
- User-owned training history, deletion with confirmation, body weight and benchmark profile settings.
- Twelve explicitly defined exercise variants, all 24 requested ranks from Bronze 1 through Chang 3, rank progress and a target calculator for 1–12 reps.
- Responsive dashboard, search, muscle-group filters, empty states, validation and source explanations.
- Database constraints and Flyway migrations; parameterized queries and server-side rank calculations.
- Same-origin reverse proxy, mutation protection header, auth rate limiting, internal-only API and database services.

## Exercise progress and optional ranks

In **My profile**, toggle **Enable ranks** and save. Ranks start enabled for compatibility, but turning them off hides the ladder, rank badges and rank notifications. Your sets are preserved and continue to count if you turn ranks back on later.

Click an exercise card in the overview to open its progress modal. Close it with Escape, the close button, or a click outside. The modal shows:

- Heaviest saved set and most-reps set, each paired with its actual load/reps and date. These may be different sets; they are never combined into an invented personal best.
- A graph of daily heaviest sets or daily highest reps, with all-history, 90-day and 30-day views. Filter by exact reps or exact weight to compare similar sets. Dates without training are not plotted as zero.
- An expandable table with the exact sets behind the graph. The training log retains **every** set and can be filtered by exercise.
- Optional ranks alongside the graph and exercise cards. Graphs include all your sets regardless of benchmark dataset; ranks retain the existing dataset-specific scoring.

Ties in daily bests prefer more reps at the same weight (or more weight at the same reps). All-time records use the same rule, then the most recent date. Pull-up and dip weights mean **added load**; zero is a valid bodyweight set. Logging or deleting a set updates the records and graphs.

Sets can contain **1–1,000 reps**. Rank calculations and estimated one-rep maximums remain limited to **1–12 reps**; higher-rep sets stay in your history and graphs without affecting ranks. Existing training data is preserved by the automatic migrations; no database reset is needed.

## WOD workshop

The WOD workshop adds a generator, history/planning, and statistics in a new sidebar entry. It supports AMRAP, EMOM, For Time and Chipper; six experience levels; adjustable difficulty; equipment/muscle/movement bans; saved preferences; substitutions; personal results; and shared WODs with self-removal. See [docs/wods.md](docs/wods.md) for the model, rules and API. The additive Flyway V2 migration preserves existing users and strength logs.

## Cardio

The **Cardio** sidebar page tracks jump rope, running (400 m, 1 km, 5 km, 10 km and 20 km), and cycling. Click an activity for its graph, logging form and session history. Cardio follows the same profile rank toggle as strength. Only verified published benchmark categories are used: rope and unsupported cycling distances stay unranked. See [docs/cardio.md](docs/cardio.md) for sources, exact coverage and comparison conventions.

## English and French

Use the **Language / Langue** selector in the top bar (also available before sign-in). URLs include `/en/` or `/fr/`, for example `/fr/cardio` or `/en/wods/history`. Switching language keeps the current page, WOD tab, query string and fragment; direct links and refreshes work. The locale is carried by the URL, not by account settings or browser storage.

English source messages are the translation keys. The standalone `TranslatePipe` and `frontend/src/locales/fr.ts` translate the interface, exercise catalog, generated WOD instructions, errors and notifications. Angular’s `LOCALE_ID` formats dates and numbers. User-entered names, workout titles and notes remain unchanged. Language switching reloads the page, so save edits before switching.

## Routing with or without a proxy

All frontend requests use root-relative `/api/...` paths. Backend controllers, health checks and the session cookie use the same prefix. Locale prefixes never apply to API requests. The included nginx forwards `/api/...` unchanged; for another nginx proxy use `proxy_pass http://api:8080;` **without a trailing slash**, so `/api/` is not stripped. Serve the frontend and API under the same origin; no backend URL needs to be compiled into Angular.

For a deployment **without any reverse proxy**, the backend Docker image includes the compiled Angular site and Spring Boot serves both the UI and API. Start just these services:

```sh
docker compose -f compose.yaml -f compose.direct.yaml up --build -d db api
```

Open **http://localhost:8081/fr/overview** or **http://localhost:8081/en/overview**. API requests go directly to Spring Boot at `http://localhost:8081/api/...`. `DIRECT_PORT` changes this optional port; it binds to loopback by default. If the separate web container is already running, it can remain in place or be stopped with `docker compose stop web`. This uses the same database volume and requires no reset.

The backend Docker build context is now the repository root (`docker build -f backend/Dockerfile .`), because it builds and embeds Angular. Host-side Maven development still uses Angular’s development server and its `/api/**` proxy, or you can copy the frontend build into Spring Boot’s static resources before packaging.

## Ranking model

See [docs/ranking.md](docs/ranking.md) for the researched data, exact conventions, formulas and rank mapping. The underlying benchmarks are sourced; the 24-rank game progression is our own interpolation. These are reference estimates, not universal strength percentiles or a prescription to attempt a maximum lift.

## Local development

Use Node 22.22+ (or Node 24), Java 21 and Maven 3.9+. Container builds supply these tools automatically.

```sh
# Start PostgreSQL with a localhost-only port for host-side development.
docker compose -f compose.yaml -f compose.dev.yaml up -d db
# If you changed DB_PASSWORD in .env, export it in this terminal too.
cd backend
mvn spring-boot:run
```

In a second terminal:

```sh
cd frontend
npm ci
npm start
```

The Angular development server runs at http://localhost:4200 and proxies `/api` to localhost:8080. Alternatively, run the full Compose stack and use its web interface.

## Checks

```sh
cd backend
mvn verify
cd ../frontend
npm ci
npm run build
```

Backend tests cover rank anchors, boundary conditions, monotonicity, all displayed targets, bodyweight scoring, authentication, request protection, per-user isolation, historical bodyweight preservation, deletion and logout. Tests use H2 in PostgreSQL compatibility mode; the actual stack uses PostgreSQL.

Against a running Compose stack, run the integration smoke test (creates two uniquely named test accounts and a set, then deletes the set):

```sh
python3 scripts/smoke.py
```

A browser smoke test covers registration, set logging, rank targets, deletion, mobile layout, profile editing and logout:

```sh
cd frontend
npm ci
npx playwright install chromium
npm run test:e2e
npm run test:wod
npm run test:progress
npm run test:cardio
npm run test:i18n
```

Set `CHANGOFF_URL` to test another local URL. Browser tests create isolated test accounts in the running database. The progress test checks daily records, graph filters, saved rank preferences, higher-rep sets, deletion, bodyweight sets, and mobile layout. The cardio test checks French and English routes, cardio logging and ranks, profile visibility, modal graphs, WOD translations and mobile layout. Set `CHANGOFF_DIRECT_URL=http://localhost:8081` to additionally check direct Spring Boot hosting. Backend cardio tests cover exact benchmark boundaries, dataset snapshots, invalid inputs and ownership.

## REST API

All routes except auth and health require the session cookie. JSON mutations also require `X-Requested-With: changoff`. No cross-origin API access is enabled.

| Method | Route | Purpose |
|---|---|---|
| GET | `/api/health` | Check API and database |
| POST | `/api/auth/register` | Create account and session |
| POST | `/api/auth/login` | Start session |
| POST | `/api/auth/logout` | Revoke current session |
| GET / PUT | `/api/me` | Read / edit profile |
| GET | `/api/exercises` | Exercise definitions and source anchors |
| GET | `/api/exercises/{id}/targets?reps=5` | Personalized 24-rank targets |
| GET | `/api/dashboard` | Best ranks and next-rank progress |
| GET / POST | `/api/lifts` | Read training history / save set |
| DELETE | `/api/lifts/{id}` | Delete own set and recalculate rank |
| GET | `/api/cardio/benchmarks` | Cardio thresholds and sources |
| GET / POST | `/api/cardio` | Read cardio history / save session |
| DELETE | `/api/cardio/{id}` | Delete an owned cardio session |

Example registration payload:

```json
{"name":"Alex","email":"alex@example.com","password":"a-long-unique-password","bodyweight":75,"standard":"male"}
```

Example set payload:

```json
{"exercise":"bench-press","weight":60,"reps":8,"performedOn":"2026-09-24"}
```

## Deployment notes

The default Compose setup is intended for local HTTP use. For public deployment, configure HTTPS, set `SECURE_COOKIE=true`, use a strong database password, configure database backups and put the service behind your TLS reverse proxy. The included nginx limits auth requests per IP. Password reset and email verification are not implemented. Fonts are bundled locally; the application makes no third-party font requests.
