# Cow2Win

A desktop companion tool for Hero Wars: Dominion Era's Clash of Worlds event.
It keeps a guild's roster (members, hero/titan teams), computes a suggested
lineup (which team defends which fortification) via
`BestPossibleLineupAlgorithm`, and exports the result as an HTML report.

## Requirements

- A JDK compatible with `--release 21` (`pom.xml` currently targets Java 24
  via `maven.compiler.source`/`target`).
- Maven. No wrapper is committed (`.mvn/` exists but is empty), so a locally
  installed `mvn` is required.
- Network access on first build - Gson and JUnit Jupiter are pulled from
  Maven Central (see `pom.xml`).

## Build & test

```
mvn clean compile
mvn clean test
```

There's no `exec`/`shade`/`assembly` plugin configured yet, so there's no
`mvn exec:java` or runnable fat jar. Run the app from your IDE instead - this
project ships an IntelliJ `.idea` folder and is meant to be run that way
(main class: `org.c2w.C2WApp`).

On first start (no `workspace/config.properties` yet), the app asks for a
language and a guild name, then creates that guild's folder under
`workspace/` with an initial `guild.json` and `default.lineup`.

## Data model overview

- **Hero** (`id`, `roles[]`, `image`) - see `Role.java` for the valid roles;
  some heroes have two roles (e.g. Cleaver = TANK + CONTROL).
- **Titan** (`id`, `element`, `image`) - see `TitanElement.java`, which
  includes the rare `DISTORTION` element used by some event titans.
- **Fortification** (`id`, `type` HERO/TITAN, `capacity`, `captureBonus`,
  `row`/`column` for the map layout, `buff`, `prerequisites`,
  `strategicImportance`) - `prerequisites` is an **OR-relation**: capturing
  *any one* of the listed fortifications unlocks this one, not all of them
  together. See the class javadoc on `Fortification` for the full rules,
  including how `strategicImportance` is assessed.
- **Guild** (`id`, `name`, up to 30 `members`, `season`, `seasonStart`) - one
  guild is one subfolder under `workspace/`.
- **Lineup** (`guildId`, `algorithmName`, `createdAt`, `entries[]`) - the
  saved result of one assignment run (manual and algorithm-produced entries
  can be mixed); persisted as `workspace/<guild>/<name>.lineup`.

## Canonical data files

- `src/main/resources/data/heroes.json`, `titans.json`, `fortifications.json`
  are the canonical source for the app's catalog. When the game itself
  changes (new heroes/titans, balance changes, new fortifications), edit
  these files, not the research doc.
- `src/main/resources/images/heroes/`, `images/titans/` hold the avatar
  icons (a missing icon falls back to `placeholder.png`, not an error).
- `src/main/resources/language/{deutsch,english,francais}.txt` hold display
  names and UI strings, looked up at runtime via `LanguageService` - they are
  not stored on the `Hero`/`Titan` records themselves.
- After any patch that could touch this data, work through
  [`PATCH-CHECKLIST.md`](./PATCH-CHECKLIST.md) in the repo root - it lists
  exactly which files/checks are affected per kind of change.
- Since 2026-09-10, `HeroRepository`/`TitanRepository`/`FortificationRepository`
  validate their data on load (unknown enum values, prerequisites referencing
  an unknown fortification id, prerequisite cycles) and report problems via
  `Logger` (visible in the app's log panel) instead of failing silently.

## Tests

`src/test/java` currently covers:

- `BestPossibleLineupAlgorithmTest` - unlock-depth computation (the OR- vs.
  AND-semantics of `prerequisites`, unknown/cyclic prerequisites, and a
  regression test against the real catalog's documented depth table).
- `FortificationRepositoryValidationTest` - the load-time data validation
  described above.
- `LanguageFilesConsistencyTest` - fails the build if
  `src/main/resources/language/{deutsch,english,francais}.txt` don't define
  exactly the same set of keys (a key added to only one file after a patch
  is otherwise a silent gap - `LanguageService` just falls back to the raw
  id, see `PATCH-CHECKLIST.md`'s "all three language files" step).

Not yet covered: the actual team-assignment logic in
`fillFortifications`/`assignOne`/`assignStrongestFirst` (sorting criteria,
buff-fit scoring, edge cases with incomplete teams).

## More context

The broader background for this project - Clash of Worlds game rules, the
schedule/season/reward details that are deliberately *not* stored in this
repo's JSON files, buff/captureBonus reference tables, and the ongoing
improvement roadmap - lives in the "Cow2Win" Claude Project
(`clash-of-worlds-event-recherche.md` and
`cow2win-verbesserungsvorschlaege.md`), not in this repository.
