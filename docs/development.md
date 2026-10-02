# Editing Time to Chang

This guide maps the code to the parts of the application you can change. Setup, environment variables and backups are in [the README](../README.md). The frontend and backend remain separate applications; their shared contract is JSON over `/api`.

## Find the right file

| Change | Frontend (`frontend/src/`) | Backend (`backend/src/main/java/dev/changoff/`) |
| --- | --- | --- |
| Sidebar, top bar, page composition | `app/app.component.*` | — |
| Login, registration, profile | `features/account/` | `auth/`, `account/` |
| Strength overview, history, rank ladder | `features/strength/` | `strength/` |
| Strength graph calculations | `features/strength/progress/progress-calculations.ts` | — |
| Cardio forms, graphs, comparisons | `features/cardio/` | `cardio/` |
| WOD generation, history, statistics tabs | `features/wod/` | `wod/` |
| Language messages and URL locale | `core/i18n/` | Keep response contracts stable; messages are translated in the UI |
| Navigation and browser history | `core/navigation/navigation.service.ts` | — |
| Request transport and error reporting | `core/http/`, `core/feedback/` | `web/` |
| Modal lifecycle and keyboard helpers | `shared/dialog/` | — |
| SQL storage | — | `Jdbc*Repository.java` inside each feature |

Templates live beside their components. Application-wide styling is in `frontend/src/styles.css`; WOD styling lives in `features/wod/wod.css` and is scoped under `app-wod` so it can reach the three tab components without leaking into other features.

## Backend: HTTP, use cases, storage

The normal dependency direction is:

```text
Controller → Service → Repository interface ← JdbcRepository
                 ↓
         calculation / policy classes
```

Controllers bind and validate requests, obtain the current user, and return results. Services implement the use case and enforce ownership and business rules. Repository interfaces describe the storage operations each feature needs; JDBC implementations contain parameterized SQL. Spring injects the implementation, so a service can be tested with a substitute repository without an HTTP server or database.

For example, a logged set travels through `StrengthController`, `StrengthService`, and `LiftRepository` / `JdbcLiftRepository`. `Ranking` owns the strength formulas. Cardio follows the same structure, with benchmark calculations in `CardioRanking`.

Authentication is shared through `CurrentUser`; controllers never call another controller for authentication. `SessionService` owns token generation, hashing and lookup; `SessionCookies` owns HTTP cookie handling. `AuthService` coordinates account and session creation in one transaction. `AccountProfile` deliberately preserves the existing `ranks_enabled` JSON field even though its Java field is `ranksEnabled`.

WOD responsibilities are split further because they have different reasons to change:

- `WodGenerationService` loads and validates generation inputs.
- `MovementPolicy` applies hard restrictions; `WodSelection` scores eligible movements and balances selection.
- `WodProgramming` calculates prescriptions, duration and difficulty; `WodGenerator` coordinates those calculations and alternatives.
- `WodPreferenceService` manages saved equipment, levels and bans.
- `WodHistoryService` saves, shares and records WODs, with ownership checks, revision checks and transactions.
- `WodSnapshots` serializes saved prescriptions. Historical snapshots must not be recomputed from a changed catalog.
- `WodExposureService` reads recent training exposure; `WodStatisticsService` builds the statistics response.

Keep multi-write operations transactional on public service methods. Do not move individual writes out of the existing WOD transactions: row locks, revision checks, membership changes and snapshot updates form one operation. Add a repository method with a meaningful name instead of passing arbitrary SQL from a controller or service. HTTP validation is not a substitute for service-level ownership checks.

Reference files remain under `backend/src/main/resources/`: `exercises.json` contains strength anchors and `cardio-benchmarks.json` contains supported cardio comparisons. Consult [ranking.md](ranking.md), [cardio.md](cardio.md) and [wods.md](wods.md) before changing these rules.

## Frontend: templates, state, API clients

The usual flow is:

```text
Component / template → feature store → feature API client → ApiClient → /api
                              ↓
                     pure calculation helpers
```

`AppComponent` composes the shell. Account forms own their form values, `SessionStore` owns the signed-in user, and `StrengthStore` owns shared strength state. `WodStore` is provided by the WOD component so its state and subscriptions live only as long as that feature. Its tab components focus on rendering. Cardio keeps its page-local state in its component, with transport and graph calculations extracted into separate files.

Feature API clients expose typed operations such as `history()` and `log(...)`. Only `ApiClient` handles fetch, common headers, response errors and expired sessions. Keep URLs root-relative and beginning with `/api`; locale prefixes belong to page URLs only.

`RequestState` handles pending work and notifications with `try/finally`, and prevents duplicate submissions through its `run()` method. Account and strength share the shell's feedback instance; WOD and cardio provide local instances. Do not accidentally provide another root state service at a child component when you need its existing shared state.

Graph helpers are pure functions. They select real saved sets, apply filters and project chart coordinates without HTTP or Angular state. Edit them when changing calculations, and test them directly. Do not combine the weight from one set with the reps from another into a fabricated record.

The shared modal directive owns native dialog opening, closing and focus restoration. Keep dialog state in the feature and DOM lifecycle in the directive rather than introducing document-wide selectors or timeout-based opening.

## Common edits

### Change text or a layout

1. Find the feature template in the table above.
2. Use the existing `t` pipe for interface text. English text is the translation key.
3. Add or update the matching French entry in `core/i18n/locales/fr.ts`. Keep placeholders such as `{0}` consistent.
4. Run `npm run test:i18n` and `npm run build` from `frontend/`.

### Add a field to a form/API

1. Update the feature's TypeScript model, form and API operation.
2. Update the Java request record and validation, then the service and repository as needed.
3. Preserve existing JSON names unless deliberately migrating the API contract.
4. If storage changes, add a new Flyway migration. Never edit an applied SQL or Java migration.
5. Add an API test for invalid input, ownership and persistence as applicable.

### Change WOD or ranking logic

Start in the calculation/policy classes, not the controller or template. Preserve hard bans separately from soft selection preferences. WOD generation is intentionally randomized; test invariants (allowed equipment, restrictions, formats, bounds) rather than a particular random exercise order. Unsupported cardio comparisons must remain unranked rather than being extrapolated.

## Run and check changes

Use the README's local development instructions for PostgreSQL, `mvn spring-boot:run` and Angular's `npm start`. Angular's development proxy forwards `/api` to Spring Boot. Docker's web container uses the bundled nginx for the same purpose; the backend never bundles or serves Angular.

From `frontend/`:

```sh
npm ci
npm run format        # Format application Java, TypeScript, templates and JS tests
npm run check         # Formatting, pure-function tests, translations, production build
```

The shared Prettier configuration is at the repository root and includes the Java plugin. Applied migrations are intentionally excluded. `backend/pom.xml` is ordinary XML and is maintained separately.

From `backend/` with Java 21 and Maven installed:

```sh
mvn verify
```

Alternatively, `docker compose up --build -d` from the repository root builds both applications and runs the backend tests during the image build. Backend integration tests use H2 in PostgreSQL compatibility mode; browser tests exercise the running PostgreSQL stack.

Against a running stack, from `frontend/`:

```sh
npx playwright install chromium
npm run test:e2e
npm run test:progress
npm run test:wod
npm run test:cardio
```

Use `CHANGOFF_URL=http://localhost:YOUR_PORT` if necessary. These suites create separate test accounts in that database. They cover registration, profile settings, strength records, modal focus, mobile layouts, WOD generation/sharing/planning, cardio and locale navigation. Run them against a development instance.

`npm run test:unit` runs fast Node tests for date handling and graph/record calculations. `scripts/helpers/load-typescript.mjs` uses the existing TypeScript compiler to load pure helpers for these tests; it is test tooling, not application code. Backend tests cover API ownership and validation, ranking formulas, WOD lifecycle and restrictions, and rollback when session creation fails during registration.

## Boundaries worth preserving

- Keep user identity on the server; derive it from the session, not a user ID supplied by a form.
- Keep database queries out of controllers and calculation classes, and fetch calls out of templates and stores.
- Keep schema migrations and stored WOD snapshots stable so existing histories remain readable.
- Keep rank visibility independent of tracking: disabling ranks must not discard records or graphs.
- Keep frontend and backend independently buildable and retain `/api` end to end.
- Prefer a small named function or focused class when a responsibility grows. Add abstractions at real boundaries, rather than an interface for every class.
