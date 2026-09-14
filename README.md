# Cow2Win

[![CI](https://github.com/Cow2Win/Cow2Win/actions/workflows/ci.yml/badge.svg)](https://github.com/Cow2Win/Cow2Win/actions/workflows/ci.yml)

A desktop companion tool for Hero Wars: Dominion Era's Clash of Worlds event.
It keeps a guild's roster (members, hero/titan teams), computes a suggested
lineup (which team defends which fortification) via
`BestPossibleLineupAlgorithm`, and exports the result as an HTML report.

## Requirements

- A JDK compatible with `--release 21` (`pom.xml` currently targets Java 24
  via `maven.compiler.source`/`target`). For the distribution package below
  you need a full JDK 24 (with `jmods`), not a JRE-only install - `jpackage`
  needs those to build the bundled runtime.
- Maven. No wrapper is committed (`.mvn/` exists but is empty), so a locally
  installed `mvn` is required.
- Network access on first build - Gson and JUnit Jupiter, and (for the
  distribution package) the shade/assembly/jpackage plugins, are pulled from
  Maven Central (see `pom.xml`).

## Build & test

```
mvn clean compile
mvn clean test
```

A GitHub Actions workflow (`.github/workflows/ci.yml`) runs `mvn test` on
every push and pull request (JDK 24, `ubuntu-latest` - `test` never reaches
the `package` phase, so the Windows-only jpackage step below never runs in
CI).

Run the app from your IDE for day-to-day development - this project ships an
IntelliJ `.idea` folder and is meant to be run that way (main class:
`org.c2w.C2WApp`).

## Distribution package (Windows app-image)

`mvn clean package` (and therefore also `mvn deploy`, which is now a "real"
deploy in that sense) additionally builds a self-contained Windows package
under `target/dist/Cow2Win/`, and zips it into
`target/Cow2Win-<version>-windows-app.zip`:

- `Cow2Win.exe` - launches the app, no separately installed Java needed.
- `runtime/` - a bundled JRE, built by `jpackage`/`jlink` just for this app
  (`java.desktop` + `java.base`).
- `resources/` - a plain, on-disk copy of `src/main/resources` (images, the
  catalog JSONs, the language files) alongside the app, in addition to the
  same files being on the jar's classpath as usual.
- `app/` - the executable jar (all dependencies, i.e. Gson, merged in via
  `maven-shade-plugin`) and jpackage's own launcher config.

The zip is attached as a secondary artifact, so `mvn deploy` uploads it to
the repository in `distributionManagement` alongside the plain project jar.
Pass `-Ddist.skip=true` to skip all of this (e.g. for a quick
`mvn clean test`) without touching the rest of the build.

Implementation: `maven-shade-plugin` (executable jar) ->
`org.panteleyev:jpackage-maven-plugin` (app-image, wraps the JDK's
`jpackage`) -> `maven-assembly-plugin` (zips `target/dist/Cow2Win/`, see
`src/assembly/windows-app.xml`) - all three bound to the `package` phase in
`pom.xml`.

**Known limitation:** `HeroRepository.save`/`FortificationRepository.save`
(used by the in-app catalog-editing dialogs, e.g. `HeroBuffFitScoresDialog`)
write to the hardcoded dev-time path `src/main/resources/data/*.json`, and
`C2WApp`'s first-run demo guild loads from that same source path via
`Files`/`Path`, not the classpath. Both keep working when run from the
source tree (e.g. from the IDE), but neither has anywhere to write to inside
the packaged app above (there's no `src/` there) - catalog editing and the
first-run demo guild are effectively IDE/dev-only features until those paths
are changed to something that also makes sense for a packaged install (e.g.
resolving against the new `resources/` folder next to the exe).

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
- `src/main/resources/data/catalog-version.json` records the `dataVersion`
  (a date) that the three catalog files above were last checked against,
  read via `CatalogVersion` and logged once at app startup so it's visible
  at a glance in the log panel. It's a separate sidecar file rather than a
  field inside `heroes.json`/`titans.json`/`fortifications.json` themselves,
  because those three currently have a JSON **array** as their root - see
  `CatalogVersion`'s class javadoc for why that ruled out a field on the
  files directly. Update `dataVersion` (and `note` if useful) whenever you
  work through `PATCH-CHECKLIST.md`.
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
- `BestPossibleLineupAlgorithmAssignmentTest` - the actual team-assignment
  logic in `assignStrongestFirst`/`assignOne` (sorting criteria, buff-fit
  scoring, edge cases with incomplete teams) plus `fillFortifications`'
  bridge/breadth-first-coverage orchestration via the real fortification
  catalog.
- `FortificationRepositoryValidationTest` - the load-time data validation
  described above.
- `LanguageFilesConsistencyTest` - fails the build if
  `src/main/resources/language/{deutsch,english,francais}.txt` don't define
  exactly the same set of keys (a key added to only one file after a patch
  is otherwise a silent gap - `LanguageService` just falls back to the raw
  id, see `PATCH-CHECKLIST.md`'s "all three language files" step).
- `BackupServiceTest` - the daily/weekly workspace backup logic in
  `BackupService` (first-run creation, same-day/same-ISO-week skip,
  recreation once stale, and not backing up a backup directory nested
  inside the workspace).

## More context

The broader background for this project - Clash of Worlds game rules, the
schedule/season/reward details that are deliberately *not* stored in this
repo's JSON files, buff/captureBonus reference tables, and the ongoing
improvement roadmap - lives in the "Cow2Win" Claude Project
(`clash-of-worlds-event-recherche.md` and
`cow2win-verbesserungsvorschlaege.md`), not in this repository.
