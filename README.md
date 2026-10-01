# Cow2Win

[![CI](https://github.com/Cow2Win/Cow2Win/actions/workflows/ci.yml/badge.svg)](https://github.com/Cow2Win/Cow2Win/actions/workflows/ci.yml)

A desktop companion tool for Hero Wars: Dominion Era's Clash of Worlds event.
It keeps a guild's roster (members, hero/titan teams), computes a suggested
lineup (which team defends which fortification) and exports the result as an
HTML report. There are fewer defense slots than teams (55 hero slots for up
to 90 hero teams, 40 titan slots for up to 60 titan teams), so the core job
is picking the best lineup and leaving the weakest teams out.

Lineup strategies (`org.c2w.eval`, registered in `LineupAlgorithms`) are
chosen separately for the hero side and the titan side (since 2026-09-24):

- **Best possible lineup** (`BestPossibleLineupAlgorithm`) - bridge first
  with the strongest teams, then fortifications by strategic importance.
- **Balanced defense** (`BalancedDefenseAlgorithm`) - always feeds the
  currently weakest open fortification.
- **CowScore maximizer** (`CowScoreMaximizerAlgorithm`) - one consistent
  measure (CowScore plus power) for every decision.
- **Consensus picks** (`ConsensusAlgorithm`) - only assignments all three
  strategies above agree on; everything else is left for a manual decision.
- **Manual** (`ManualLineupAlgorithm`) - leaves that side to manual picks.

All strategies are additive: they never change an existing assignment.
Comparing two lineups (`LineupComparisonDialog`) and turning the difference
into in-game steps (`LineupChangePlanDialog`) is built in as well.

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
  same files being on the jar's classpath as usual. Exception:
  `app-version.properties` is left out there - it is only read from the
  (filtered) copy inside the jar, and an on-disk copy would just show the
  unresolved `${app.version}` placeholder.
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

## Workspace, configuration & backups

- **Workspace** - all user data: one subfolder per guild (`guild.json` plus
  any number of `.lineup` files), the editable CowScore files
  (`cowScore.json`, `titanCowScore.json`, `petCowScore.json`,
  `warFlagCowScore.json`) and the log file. Defaults to
  `<user.home>/.cow2Win/workspace`, configurable in the Settings dialog; a
  change takes effect after a restart.
- **`config.properties`** - language, workspace/backup folder, default
  algorithms and the last opened guild/lineup (`org.c2w.infra.Config`). Lives
  in the packaged app's `resources/` folder next to `Cow2Win.exe`, or in the
  project root when run from the IDE - so several installations can use
  different workspaces.
- **Backups** - a daily and a weekly ZIP of the workspace, checked once at
  startup (`org.c2w.infra.BackupService`), by default in
  `<user.home>/.cow2Win/backup`.
- **First start** (`org.c2w.service.WorkspaceBootstrap`) - creates a "Demo" guild pre-filled from
  `data/guild.json`/`data/default.lineup` (read from the packaged
  `resources/` folder, or from `src/main/resources` in the IDE).

## Architecture

Swing desktop app, entry point `org.c2w.C2WApp`. Packages under `org.c2w`,
roughly from top to bottom:

| Package | Contents |
|---|---|
| `gui` (+ subpackages `fort`, `guild`, `hero`, `titan`, `pet`, `flag`, `common`) | Swing frames, panels and dialogs. How data looks on screen (e.g. `FortificationTypeStyle` for the hero/titan colors and slot icons) lives here, not in the model. They only collect input and show results; every change to the open guild/lineup goes through `service`. |
| `service` | Application layer. `AppContext` holds the open guild/lineup, their files and the unsaved-changes state, and notifies `AppContext.Listener`s (map, toolbar, window title) of every change. `GuildService`/`LineupService` are the use cases (create/switch/delete/save guilds and lineups, run algorithms, assign teams) and keep context, files and `config.properties` consistent. `WorkspaceBootstrap` does everything before the first window opens (first-run setup, config, backups, catalogs, reopening the last guild/lineup). |
| `eval` | The lineup algorithms (`LineupAlgorithm`, registered per side in `LineupAlgorithms`). |
| `domain` | Scoring and lineup analysis: `TeamScoreCalculator` (CowScore), `BuffCalculationService`, `LineupBaseline`, `LineupComparisonService`, `LineupChangePlanService`. |
| `report` | `ReportGenerator` - the HTML lineup report. |
| `data.model` | Immutable records (`Hero`, `Titan`, `Pet`, `WarFlag`, `Fortification`, `Guild`, `Lineup`, ...). |
| `data.repository` | Loading/saving. `Catalog` bundles the hero/titan/pet/war flag repositories of one workspace (created once at startup, reachable via `AppContext#catalog()`); `FortificationRepository` (pure classpath data) is still static. `GuildRepository`/`LineupRepository` read and write guild and lineup files, `LineupFiles` holds the rules for the reserved "Original" lineup. |
| `i18n` | `LanguageService` (UI texts, see "Canonical data files") and `BuffTexts`. |
| `infra` | Technical infrastructure: `Config`, `Logger`, `JsonSupport`, `BackupService`, `UpdateChecker`, `AppVersion`, `CatalogVersion`. |

`Config`, `Logger` and `LanguageService` are still static singletons; the
catalog repositories are instances, so tests can load them for a temp
folder (`new Catalog(tempDir)`).

## Releases & update check (GitHub)

- `org.c2w.infra.AppVersion` reads this build's own version at runtime from
  `app-version.properties`, a classpath resource whose `${app.version}`
  placeholder is filled in at build time by Maven resource filtering (see
  `pom.xml`'s `<resources>` section) - kept as its own tiny sidecar file
  (filtered) rather than turning on filtering for all of `src/main/resources`
  (unfiltered for everything else, e.g. the JSON catalogs/language files, so
  none of them can ever have a `${...}`-looking substring "resolved" away).
- `org.c2w.infra.UpdateChecker` compares that version against
  `GET /repos/Cow2Win/Cow2Win/releases/latest` on GitHub's public REST API
  and reports whether a newer release exists. `Cow2Frame` uses it twice: a
  silent check once at startup (only ever shows a dialog when an update was
  actually found - no popup for "up to date" or a failed/offline check), and
  the "File" > "Check for Updates" menu item, which always reports back.
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
  curated fortification marks (`FortMarks`: per fortification `POSITIVE` or
  `NEGATIVE`, unmarked = neutral) live in a separate file, `cowScore.json` -
  see "Canonical data files" below.
- **Titan** (`id`, `element`, `image`) - see `TitanElement.java`, which
  includes the rare `DISTORTION` element used by some event titans. Its
  curated score still uses the older tier-based `CowScore`
  (`generalScore`/`buffFitScores`) and lives in `titanCowScore.json`.
- **Pet** / **War flag** (`id`, `image`) - optional per hero team, each at
  most once per member. Their `FortMarks` (positive only) live in
  `petCowScore.json`/`warFlagCowScore.json`.
- **CowScore** of a hero team at a fortification: `totalPower / 100 000 x
  (1 + B)`, where the bonus `B` comes from matching roles, marked heroes,
  a marked pet and the war flag - see `TeamScoreCalculator`'s class Javadoc.
  Titan teams still use the tier sum plus power.
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
  can be mixed); persisted as `workspace/<guild>/<name>.lineup`. The
  reserved `Original.lineup` records the actual in-game deployment: it is
  only edited through the guild-wide team-entry dialogs and is the starting
  point of the in-game change plan (`LineupChangePlanDialog`).

## Canonical data files

- `src/main/resources/data/heroes.json`, `titans.json`, `pets.json`,
  `warFlags.json`, `fortifications.json` are the canonical source for the
  app's catalog ("objective" master data only). When the game itself
  changes (new heroes/titans, balance changes, new fortifications), edit
  these files, not the research doc.
- The curated scores are kept OUT of those master data files, one score
  file per catalog: `cowScore.json` (heroes), `titanCowScore.json`,
  `petCowScore.json`, `warFlagCowScore.json`. The split means a wholesale
  refresh of the master data (e.g. new heroes pulled from GitHub) can't
  clobber the hand-tuned scores, and vice versa.
- Each score file exists twice: the copy in `src/main/resources/data` holds
  the **shipped defaults** (read-only at runtime); the app reads and saves
  the **workspace copy** (see "Workspace, configuration & backups"), created
  from the defaults on the first start and completed with defaults for
  entities added by an update. They are edited in-app via "File" >
  "CowScore - Heroes/Titans/Pets/War Flags" (`HeroCoreScoreDialog`,
  `TitanCoreScoreDialog`, `PetCoreScoreDialog`, `WarFlagCoreScoreDialog`),
  whose "restore defaults" button resets to the shipped values.
- Formats: heroes, pets and war flags use `FortMarkFiles`
  (`[{"id": "corvus", "fortMarks": {"foundry": "POSITIVE"}}, ...]`; a
  workspace copy still in the former tier-based format is backed up as
  `<name>.legacy-<date>.bak` and migrated on load). Titans still use
  `CowScoreFiles` (`generalScore`/`buffFitScores`); a
  `generalScore`/`buffFitScores` left over in `titans.json` is ignored and
  logged.
- `src/main/resources/data/catalog-version.json` records the `dataVersion`
  (a date) that the three catalog files above were last checked against,
  read via `CatalogVersion` and logged once at app startup so it's visible
  at a glance in the log panel. It's a separate sidecar file rather than a
  field inside `heroes.json`/`titans.json`/`fortifications.json` themselves,
  because those three currently have a JSON **array** as their root - see
  `CatalogVersion`'s class javadoc for why that ruled out a field on the
  files directly. Update `dataVersion` (and `note` if useful) whenever you
  work through `PATCH-CHECKLIST.md`.
- `src/main/resources/images/heroes/`, `images/titans/`, `images/pets/`,
  `images/flags/` hold the avatar icons (a missing icon falls back to
  `placeholder.png`, not an error).
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
- Each language's `.properties` file also carries an `algorithm.<key>.name`
  and an `algorithm.<key>.description` entry per lineup strategy - the
  localized name shown in the UI and a short prose explanation of how the
  strategy works, read via `org.c2w.eval.AlgorithmDescriptions`
  (`#localizedName`, `#forAlgorithm`/`#forDisplayName`). The HTML report
  prints the description below each side's algorithm.
  `AlgorithmDescriptions.KEY_BY_DISPLAY_NAME` maps each algorithm's stable
  English `displayName()` to its key suffix - update it (and add both
  matching lines to **all three** language files) whenever a new strategy is
  added to `LineupAlgorithms`.
- After any patch that could touch this data, work through
  [`PATCH-CHECKLIST.md`](./PATCH-CHECKLIST.md) in the repo root - it lists
  exactly which files/checks are affected per kind of change.
- The repositories validate their data on load and report problems via
  `Logger` (visible in the app's log panel) instead of failing silently:
  invalid entries and unknown roles/elements in `heroes.json`/`titans.json`
  are skipped, and `FortificationRepository` checks for prerequisites
  referencing an unknown fortification id and for prerequisite cycles.

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
- `SideSpecificLineupAlgorithmsTest` - the hero/titan split of the lineup
  strategies: each side only fills its own fortifications, both sides can be
  combined freely (including `ManualLineupAlgorithm`), and the combined
  `algorithmName` records both.
- `UpdateCheckerVersionTest` - version parsing/comparison of
  `UpdateChecker`, without touching the network.
- `CowScoreFilesTest` - the tier-based `titanCowScore.json` format
  (parsing, tolerance for unknown tiers, sparse writing, round trip) and
  loading the real titan catalog.
- `FortMarkFilesTest` - the fortification-mark format of
  `cowScore.json`/`petCowScore.json`/`warFlagCowScore.json`, including the
  migration of the former tier-based format and a save/reload round trip
  through `HeroRepository`.
- `TeamScoreCalculatorHeroTest` - the hero-team CowScore formula (role
  buff, hero relation, pet and war flag bonuses).
- `TeamScoreCalculatorTitanTest` - the titan-team score (general score
  without a buff, element-match defaults and explicit overrides with one).
- `LineupComparisonServiceTest`, `LineupChangePlanServiceTest` - comparing
  two lineups (per-team status, per-fortification and summary figures) and
  turning the difference into ordered in-game change steps.
- `ReportGeneratorTest` - report file naming and the main content of the
  HTML report (HTML escaping, algorithm, used heroes).
- `GuildMemberTest`, `GuildRepositoryPetWarFlagTest`,
  `GuildDraftConverterPetWarFlagTest`, `TeamEditorPanelExtrasTest` - a hero
  team's optional pet/war flag: the "at most once per member" rule, guild
  file round trip (including older files and unknown ids), the editing
  dialogs' draft round trip and the combo boxes in `TeamEditorPanel`.
- `AppContextTest` - change notifications and the unsaved-changes state.
- `GuildServiceTest`, `LineupServiceTest` - the guild/lineup use cases
  against a temp workspace: files on disk, the open guild/lineup and the
  unsaved-changes state stay consistent. Also checks that a background
  algorithm run is discarded if the lineup changed in the meantime.

## More context

The broader background for this project - Clash of Worlds game rules, the
schedule/season/reward details that are deliberately *not* stored in this
repo's JSON files, buff/captureBonus reference tables, and the ongoing
improvement roadmap - lives in the "Cow2Win" Claude Project
(`clash-of-worlds-event-recherche.md` and
`cow2win-verbesserungsvorschlaege.md`), not in this repository.
