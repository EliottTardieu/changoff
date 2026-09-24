# WOD workshop

The sidebar's **WOD workshop** has three tabs: Generate, History & plans, and Statistics. Existing strength tracking and ranks are independent of WOD results.

## Generator

Choose AMRAP, EMOM, For Time, Chipper, or automatic format rotation. Set the intended stimulus, one of six experience levels, difficulty, movement count, duration/time cap, and date. For Time also accepts a round count. Generate previews freely; only saving creates history.

The initial catalog has 34 movements with families, categories, movement types, patterns, muscles, equipment, skill difficulty and six level-specific prescriptions. The experience levels are Beginner, Initiated, Intermediate, Confirmed, Advanced and Mad Man. Load and volume presets are application defaults, not official CrossFit standards. Edit quantities and load before saving. Units include reps, seconds, meters and machine calories; each exercise explains its load convention. WOD loads do not come from your strength ranks.

The programming approach is informed by CrossFit's [format glossary](https://www.crossfit.com/essentials/crossfit-terms-explained) and [substitution guidance](https://www.crossfit.com/faq/substitutions), accessed 2026-09-24. Scaling seeks to preserve movement and workout intent. The application is not affiliated with CrossFit.

### Constraints and variety

- Every equipment requirement must be available. Select none for bodyweight-only workouts.
- Temporary bans can exclude specific movements, movement patterns, muscle groups, or equipment. Any matching muscle or pattern excludes a movement, including compound movements.
- Saved defaults include experience level, available equipment and persistent restrictions with an optional reason and active flag. Save changes before generating. Saved equipment limits and active restrictions intersect with temporary selections; a temporary selection cannot override them.
- The generator limits movement difficulty to experience level + 1 (at most 6). It never silently drops a restriction. If fewer eligible exercises remain than requested, it returns an actionable conflict.
- Candidate scores penalize recent exercise repetition, recent muscle exposure, duplicate families and duplicate patterns in the current session. Completed WODs from the last 28 days use decaying recency weights. Planned WODs within seven days of the selected date contribute a smaller penalty. Skipped WODs and other users' results do not contribute.
- A small random component adds variety. Automatic format selection favors the least used format among the last four saved non-skipped WODs; short sessions cannot select an EMOM with more movements than minutes.
- Suggested substitutions first favor the same exercise family, then overlapping movement patterns, and no higher skill difficulty. They respect all current bans and equipment constraints and exclude movements already in the WOD. They are alternatives, not guaranteed equivalents or injury-specific advice.

### Format rules

| Format | Prescription |
|---|---|
| AMRAP | Repeat the circuit for the selected duration; score complete rounds plus extra reps. Stored target rounds are 0 because the target is open-ended. |
| EMOM | One listed movement per minute, cycling in order. Suggested station work is at most 40 estimated seconds; manually edited stations may not exceed 45 estimated seconds. Rest fills the remainder. The last cycle may be partial. |
| For Time | Complete the selected number of rounds before the time cap. A warning appears if estimated moving time already exceeds the cap. |
| Chipper | Complete each listed movement once, in order. Quantity scaling roughly targets 75% of the time cap as moving time, subject to scaling bounds. |

Work duration estimates use simple per-unit catalog values; they cannot predict individual pace. The 1–10 assessed difficulty combines movement skill, selected experience, intensity and relative prescription volume/load. Changing quantities or load requires reassessment to refresh the displayed estimate; saving always validates and reassesses on the server. Record your perceived effort separately after training.

## History, scheduling and results

Each saved WOD stores an immutable catalog snapshot for each prescribed movement, plus the generator settings. History includes future plans, overdue plans, completed and skipped sessions. Search by title and filter by status/date range. Dates are calendar dates rather than timed appointments.

Select a WOD to inspect its exact prescription, change your scheduled date, record completion date, elapsed seconds, complete rounds/cycles, extra reps, perceived effort and private notes. Marking a WOD completed permanently freezes the shared prescription; changing the result back to planned does not unfreeze it. Use **Repeat / scale as new** for a new variation. Planned prescriptions can be edited by the organizer before anyone completes them; revision checks reject stale edits.

Results and schedules belong to each participant. Editing an organizer's schedule does not move another participant's personal date. A completion must have a non-future date. Non-completed records have no completion date or numeric results. Prescriptions are snapshotted; later catalog updates do not rewrite saved history.

## Shared WODs

Organizers can add an existing account by exact email (up to 20 participants). The WOD immediately appears in that user's WOD history as planned, not completed. Their saved equipment and active restrictions must be compatible; their experience/difficulty may still warrant their own scaled copy. A newly added member starts on the organizer's scheduled date. Only participant names and IDs are shared; other users' results and notes remain private.

Any participant can remove themselves and their own result. Other records remain. If the organizer leaves, the earliest remaining participant becomes organizer; if the last participant leaves, the WOD and its prescription are removed. Nonmembers cannot read, edit, score, or share a WOD. Membership is checked again on every request, and shared mutations lock the WOD row.

## Statistics

Statistics count only the current user's completed WODs:

- Eight Monday–Sunday calendar weeks of completion counts.
- Last 28 days: WOD count, distinct training days, entered duration in minutes, mean entered effort, unique movements and format counts.
- Each muscle is counted at most once per completed WOD. All catalog muscles appear, including zero exposure, making least-hit groups visible.
- Upcoming counts include planned WODs dated today or later.

Muscle exposure is a rough coverage measure, not a measure of workload, hypertrophy or recovery. Missing durations/effort do not become invented values. Empty history shows no most/least ranking and no average effort.

## Data model

Flyway V2 adds normalized movement patterns/types, categories, equipment, families, muscles, exercises, per-level prescriptions, WOD formats/stimuli/templates, user equipment preferences and restrictions. It adds `athletes.wod_level_id` with a default for existing accounts. Registration names its original columns explicitly so existing account behavior is preserved.

`wods` owns the shared prescription; `history_exercise` stores ordered movement snapshots; `user_wod` joins participants and stores their schedule and result. This replaces a separate History/UserHistory pair while keeping the same intent. WOD exercises are separate from the pre-existing strength-ranking JSON catalog.

## API

All WOD routes require the existing session cookie and the existing mutation protection header.

| Method | `/api/wods` route | Purpose |
|---|---|---|
| GET | `/catalog` | Catalog and templates |
| GET / PUT | `/preferences` | Equipment, level, persistent restrictions |
| POST | `/generate` | Generate or validate/reassess a preview |
| GET / POST | (base route) | Personal history / save a WOD |
| GET / PUT | `/{id}` | Participant detail / organizer prescription edit |
| PUT | `/{id}/result` | Personal schedule, status and result |
| POST | `/{id}/participants` | Add account by email |
| DELETE | `/{id}/participation` | Leave and remove your own record |
| GET | `/statistics` | Personal completion statistics |

## Verification

`WodTest` covers constraints and alternatives, insufficient candidates, formats and automatic variety, saved preferences, tampering, validation, private results, recipient restrictions, stale edits, organizer transfer, removal/access revocation, frozen history and user-scoped exposure. Existing strength and authentication tests also run in every backend container build. The browser WOD smoke test exercises the generator, scheduling, sharing, results, statistics and leaving through two accounts against the full stack.
