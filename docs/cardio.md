# Cardio tracking and benchmark sources

Open **Cardio** in the sidebar, then click an activity to open its progress modal. Log jump-rope repetitions and elapsed time, a run over 400 m, 1 km, 5 km, 10 km or 20 km, or a bicycle ride with a distance and elapsed time. Dates and optional notes are saved per user. Sessions can be deleted with confirmation.

Graphs show the best speed (km/h) or cadence (reps/min) on each training day, with all-history, 30-day and 90-day filters. The table retains every matching session. Rope sessions can be compared at an exact duration; rides can be compared at an exact distance. Different distances, durations, terrain and equipment are not equivalent efforts. Dates without sessions are not plotted as zero. For rope, count one completed single-under jump as one repetition; use elapsed time including pauses for every activity.

## Optional ranks

**My profile → Enable ranks** controls both strength and cardio rank visibility. Turning it off preserves all records and graphs. Each cardio session retains the male/female reference dataset selected when it was recorded, even if the profile changes later. The card's best rank uses the currently selected dataset; the history shows each session's original dataset.

Cardio uses the categories actually published by the sources below. It does **not** invent a Bronze–Chang conversion, additional tiers, age adjustments or benchmarks between published distances. A time equal to a threshold qualifies for that category; faster times qualify for the highest threshold they meet. Slower than the beginner threshold is shown as “Below beginner benchmark,” a descriptive state rather than an additional sourced tier.

The fixed reference ages are displayed beside the benchmarks. They are useful reference targets, not personalized age-adjusted standings or universal population percentiles. Cycling speed in particular depends on terrain, wind, bicycle and drafting; these references should not be treated as interchangeable across outdoor rides and stationary bikes.

## Sources

Accessed 30 September 2026. Exact thresholds, reference ages and individual source links are versioned in [`cardio-benchmarks.json`](../backend/src/main/resources/cardio-benchmarks.json). The modal also links directly to its source.

| Activity | Source | Reference and published categories |
|---|---|---|
| Running 400 m | [Marathon Handbook: average 400-meter time](https://marathonhandbook.com/average-400-meter-time/) | Publisher's coaching benchmarks, male/female ages 18–39: Beginner, Novice, Intermediate Recreational, High-Level Recreational, Sub-Elite, National Class, Elite |
| Running 1 km | [Running Level: 1 km](https://runninglevel.com/running-times/1k-times) | Male/female age 25: Beginner, Novice, Intermediate, Advanced, Elite |
| Running 5 km | [Running Level: 5 km](https://runninglevel.com/running-times/5k-times) | Same categories and reference age |
| Running 10 km | [Running Level: 10 km](https://runninglevel.com/running-times/10k-times) | Same categories and reference age |
| Running 20 km | [Running Level: 20 km](https://runninglevel.com/running-times/20k-times) | Same categories and reference age |
| Cycling | [Cycling Level: 20 km](https://cyclinglevel.com/cycling-times/20k-times), with the corresponding distance-specific pages linked in the benchmark file | Male/female age 25: Beginner, Novice, Intermediate, Advanced, Elite |
| Jump rope | [IJRU competition rules](https://rules.ijru.sport/) | Competition protocols are not a general single-under reps/time ranking scale; no ranks assigned |

Cycling ranks are available at exactly **1, 4, 5, 8, 10, 15, 20, 25, 30, 40, 50, 80, 100 and 160 km**. Other distances are fully tracked but unranked. No suitable general ranking scale was verified for arbitrary jump-rope repetitions over arbitrary durations, so rope remains tracking-only. Competitive speed counts use different rules and must not be substituted for ordinary jump counts.

## Storage and API

Flyway migration `V5__cardio.sql` adds a user-owned `cardio_sessions` table without changing existing strength or WOD data. Every endpoint requires authentication; mutations also require `X-Requested-With: changoff`.

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/cardio/benchmarks` | Published thresholds and sources |
| GET | `/api/cardio` | Current user's complete cardio history |
| POST | `/api/cardio` | Save a completed session |
| DELETE | `/api/cardio/{id}` | Delete an owned session |

Example run payload:

```json
{"activity":"running","distanceMeters":5000,"reps":null,"durationSeconds":1351,"performedOn":"2026-09-30","notes":"Track, elapsed time"}
```

For rope, use `activity: "rope"`, a positive `reps` count and `distanceMeters: null`. For cycling, use `activity: "cycling"`, a positive whole-meter distance and `reps: null`. Fractional seconds are supported. Future dates and invalid metric combinations are rejected. Access and deletion are restricted to the session owner.
