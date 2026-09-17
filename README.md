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

**Known limitation:** `HeroRepository.saveCowScores`/`FortificationRepository.save`
(used by the in-app catalog-editing dialogs, e.g. `HeroBuffFitScoresDialog`)
write to the hardcoded dev-time path `src/main/resources/data/*.json`, and
`C2WApp`'s first-run demo guild loads from that same source path via
`Files`/`Path`, not the classpath. Both keep working when run from the
source tree (e.g. from the IDE), but neither has anywhere to write to inside
the packaged app above (there's no `src/` there) - catalog editing and the
first-run demo guild are effectively IDE/dev-only features until those paths
are changed to something that also makes sense for a packaged install (e.g.
resolving against the new `resources/` folder next to the exe).

## Releases & update check (GitHub)

- `org.c2w.util.AppVersion` reads this build's own version at runtime from
  `app-version.properties`, a classpath resource whose `${app.version}`
  placeholder is filled in at build time by Maven resource filtering (see
  `pom.xml`'s `<resources>` section) - kept as its own tiny sidecar file
  (filtered) rather than turning on filtering for all of `src/main/resources`
  (unfiltered for everything else, e.g. the JSON catalogs/language files, so
  none of them can ever have a `${...}`-looking substring "resolved" away).
- `org.c2w.util.UpdateChecker` compares that version against
  `GET /repos/Cow2Win/Cow2Win/releases/latest` on GitHub's public REST API
  and reports whether a newer release exists. `Cow2Frame` uses it twice: a
  silent check once at startup (only ever shows a dialog when an update was
  actually found - no popup for "up to date" or a failed/offline check), and
  the "Settings" > "Check for Updates" menu item, which always reports back.
  A found update offers to open the release's GitHub page in the system
  browser.
- **Requires the `Cow2Win/Cow2Win` repository - or at least its Releases -
  to be public.** The check is a plain, unauthenticated HTTPS request (no
  token shipped with the app, which would be extractable from a distributed
  client and is not attempted here); GitHub's API returns 404 for a private
  repository's releases to anyone without access, which `UpdateChecker`
  treats the same as "no release yet"/a failed check (silently at startup,
  reported as a failed check from the menu item) rather than surfacing a
  wrong or confusing message.
- `.github/workflows/release.yml` publishes the actual GitHub Release
  `UpdateChecker` checks against: pushing a tag like `v1.0.0` builds the
  Windows app-image (`mvn package`, same as the "Distribution package"
  section above, on a `windows-latest` runner) and publishes it as a GitHub
  Release with the resulting zip attached, using that tag to also set
  `app.version` for the build (so the packaged app, the release tag, and
  what `UpdateChecker` compares against always agree). Separate from
  `ci.yml`'s `test`-only job (see its own comment) since this needs a
  Windows runner and only ever runs for an actual release tag, not on every
  push/PR.

## Data model overview

- **Hero** (`id`, `roles[]`, `image`) - see `Role.java` for the valid roles;
  some heroes have two roles (e.g. Cleaver = TANK + CONTROL). Its manually
  curated `CowScore` (`generalScore`/`buffFitScores`) lives in a separate
  file, `cowScore.json` - see "Canonical data files" below.
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
- `src/main/resources/data/cowScore.json` (since 2026-09-14) holds Thorsten's
  manually curated per-hero `CowScore` (`generalScore`/`buffFitScores`,
  keyed by hero `id`) - deliberately kept OUT of `heroes.json`, which now
  only ever carries "objective" master data (`id`/`roles`/`image`). The
  split means a future wholesale refresh of `heroes.json` (e.g. new heroes
  pulled from GitHub) can't accidentally clobber these hand-tuned scores,
  and vice versa: `HeroBuffFitScoresDialog`/`HeroRepository#saveCowScores`
  only ever write `cowScore.json`, never `heroes.json`. Titans don't (yet)
  have this split - `Titan#generalScore()`/`#buffFitScores()` still live
  directly in `titans.json`, same as before.
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
- `src/main/resources/language/<name>/<name>.properties` (e.g.
  `language/deutsch/deutsch.properties`) hold display names and UI strings,
  looked up at runtime via `LanguageService` - they are not stored on the
  `Hero`/`Titan` records themselves. One subdirectory per language (added
  2026-09-16, replacing a flat `language/deutsch.txt` layout) so a language
  can also carry longer, non-properties content later (HTML/XML help texts
  etc.) alongside its `.properties` file. `LanguageService.availableLanguages()`
  discovers the languages to offer by listing these subdirectories at
  runtime (from the classpath - works both from an IDE run and from the
  packaged jar) rather than a hardcoded list, and the directory name is
  exactly what's shown in the language combo box - no separate display-name
  mapping in code anymore.
- Each language's `.properties` file also carries `algorithm.<key>.description`
  entries (since 2026-09-16, one per `LineupAlgorithm` in `LineupAlgorithms.ALL`)
  - a short prose explanation of how that algorithm works, read via
  `org.c2w.eval.AlgorithmDescriptions#forAlgorithm`/`#forDisplayName`. Not
  wired into any UI yet, ready to surface later (e.g. a tooltip next to the
  algorithm dropdown in `SettingsDialog`, or a new section in
  `ReportGenerator`'s HTML report). This used to be its own English-only
  sidecar JSON under `data/` - moved here because the report is **not**
  English-only throughout: fortification/hero/titan names in it already go
  through `LanguageService.displayName`, so this text should too, the same
  way. `AlgorithmDescriptions.KEY_BY_DISPLAY_NAME` maps each algorithm's
  `displayName()` to its key suffix - update it (and add the matching
  `algorithm.<key>.description` line to **all three** language files)
  whenever a new `LineupAlgorithm` is added to `LineupAlgorithms.ALL`.
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
- `LanguageFilesConsistencyTest` - fails the build if the `deutsch`,
  `english` and `francais` language directories under
  `src/main/resources/language/` don't define exactly the same set of keys
  in their `<name>.properties` file (a key added to only one language after
  a patch is otherwise a silent gap - `LanguageService` just falls back to
  the raw id, see `PATCH-CHECKLIST.md`'s "all three language files" step).
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
