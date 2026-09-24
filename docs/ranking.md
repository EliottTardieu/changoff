# Ranking methodology · v1

Reference data accessed **2026-09-24**. The JSON dataset is versioned in `backend/src/main/resources/exercises.json`. These values are a fixed snapshot; the app does not scrape or depend on an external service at runtime.

## Sources and conventions

Strength Level publishes community-derived beginner, novice, intermediate, advanced and elite benchmarks. We use the **bodyweight-ratio** tables for ten exercises. These rounded ratios are a simplified reference; we do not reproduce their exact bodyweight or age-specific calculator. See their [methodology](https://strengthlevel.com/about).

Each linked source supplies the five male and female anchors in the application dataset:

| Exercise in the app | Source / specific variant | Weight entered |
|---|---|---|
| Bench press | [Bench press](https://strengthlevel.com/strength-standards/bench-press/kg) | Total barbell weight including bar |
| Biceps curl | [Dumbbell curl](https://strengthlevel.com/strength-standards/dumbbell-curl/kg) | One dumbbell; weaker arm reps |
| Triceps pushdown | [Tricep pushdown](https://strengthlevel.com/strength-standards/tricep-pushdown/kg) | Cable stack load; same attachment and machine |
| Lat pulldown | [Lat pulldown](https://strengthlevel.com/strength-standards/lat-pulldown/kg) | Cable stack load; same grip and machine |
| Pull-ups / tractions | [Pull ups](https://strengthlevel.com/strength-standards/pull-ups/kg) | Added load, 0 for unweighted; strict full-range reps |
| Dips | [Dips](https://strengthlevel.com/strength-standards/dips/kg) | Added load, 0 for unweighted; strict full-range reps |
| Shoulder press | [Shoulder press](https://strengthlevel.com/strength-standards/shoulder-press/kg) | Strict standing barbell press, including bar |
| Quads / leg press | [Sled leg press](https://strengthlevel.com/strength-standards/sled-leg-press/kg) | Total sled plus plates; same machine |
| Leg curl | [Lying leg curl](https://strengthlevel.com/strength-standards/lying-leg-curl/kg) | Bilateral machine load |
| Split squat | [Bulgarian split squat](https://strengthlevel.com/strength-standards/bulgarian-split-squat/kg) | Barbell variant, total external load; weaker leg reps |
| Squat | [Squat](https://strengthlevel.com/strength-standards/squat/kg) | Back squat, including bar, at least parallel |
| Deadlift | [Deadlift](https://strengthlevel.com/strength-standards/deadlift/kg) | Conventional deadlift, including bar |

Machine geometry, pulley ratios, sled angles and technique affect comparability. Keep equipment and range of motion consistent. Standards are not age-adjusted and are not universally validated for every individual.

### Pull-ups and dips

The source publishes **added-load** 1RM tables rather than bodyweight ratios. We take the 75 kg male and 60 kg female rows, add reference body weight, then divide by that reference body weight to obtain total-load ratios. Scaling these ratios to other body weights is an **app approximation**, not the source's exact model. Negative source beginner weights indicate assistance; assisted sets are not supported by this version.

| Movement | Reference | Beginner → elite added load (kg) |
|---|---|---|
| Pull-ups | Male, 75 kg | -2, 14, 32, 52, 73 |
| Pull-ups | Female, 60 kg | -16, -4, 9, 23, 38 |
| Dips | Male, 75 kg | 3, 25, 49, 77, 106 |
| Dips | Female, 60 kg | -15, 0, 17, 37, 58 |

## Reps and scoring

For a set of **1–12 full-range repetitions**, estimate 1RM using Epley:

- One repetition: `estimated1RM = load`.
- Two to twelve repetitions: `estimated1RM = load × (1 + reps / 30)`.
- External-load exercises: `load = enteredWeight`.
- Pull-ups and dips: `load = bodyweightAtLogging + addedWeight`.
- Comparable score: `estimated1RM / bodyweightAtLogging`.

The source's [1RM calculator](https://strengthlevel.com/one-rep-max-calculator) discusses estimation from reps. Epley and the 12-rep cutoff are explicit application choices, not claims of identical implementation. Estimates become less reliable at higher repetitions, particularly for isolation movements. No actual maximum attempt is necessary.

## Mapping five anchors into 24 ranks

| Level | Rank | Threshold |
|---|---|---|
| 1 | Bronze 1 | Beginner |
| 4 | Silver 1 | Novice |
| 10 | Platinum 1 | Intermediate |
| 16 | Diamond 1 | Advanced |
| 21 | Master 3 | Elite |
| 24 | Chang 3 | Elite × 1.15 (app-defined stretch target) |

All intervening levels use **linear interpolation** between the surrounding anchors. Tier order is Bronze, Silver, Gold, Platinum, Emerald, Diamond, Master, Chang, each with divisions 1, 2 and 3. Values below Bronze 1 remain Unranked; no logged sets also means Unranked. Chang ranks are not validated population percentiles.

At 75 kg, male bench Bronze 1 requires a 37.5 kg estimated 1RM; Silver 1 requires 75 kg; Platinum 1 requires 93.75 kg. For five reps, Silver 1 displays 64.3 kg because `75 / (1 + 5/30) = 64.2857…`.

Target calculator: divide the target ratio × current body weight by the rep multiplier, subtract body weight for pull-ups/dips, clamp to zero, and round **up** to 0.1 kg. The displayed set therefore meets or exceeds the unrounded rank threshold. Choose an available equipment increment at or above that target; the application does not model specific plates or stacks. Multiple early bodyweight ranks can show zero added load and unlock together.

## History and recalculation

Each saved set stores the body weight, dataset and score used at logging time. Ranks reflect the highest historical score in the user's **currently selected dataset**, without time decay. A later bodyweight change affects future entries and target weights, not old scores. Changing reference dataset shows ranks within that dataset. Deleting the best set recalculates the rank from remaining sets. Account settings have a 30–300 kg bodyweight range. Existing historical entries retain the benchmark version implied by this v1 implementation; future changes should introduce explicit score-version migration.
