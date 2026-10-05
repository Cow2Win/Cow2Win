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
into in-game steps (the "Output" stage view, `ChangePlanPanel`) is built in as well.

## Requirements

- A JDK compatible with `--release 21` (`pom.xml` currently targets Java 24
  via `maven.compiler.source`/`target`). For the distribution package below
  you need a full JDK 24 (with `jmods`), not a JRE-only install - `jpackage`
  needs those to build the bundled runtime.
- Maven. No wrapper is committed (`.mvn/` exists but is empty), so a locally
  installed `mvn` is required.
- Network access on first build - Gson, H2 and JUnit Jupiter, and (for the
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
- `runtime/` - a bundled JRE, built by `jpackage`/`jlink` just for this app,
  containing only the JDK modules listed under `addModules` in `pom.xml`
  (`java.base`, `java.desktop`, `java.net.http` for the update check, and
  `java.sql`/`java.naming`/`java.management` for H2) plus their
  dependencies. `runtime/release` lists what actually ended up in there.
- `resources/` - a plain, on-disk copy of `src/main/resources` (images, the
  catalog JSONs, the language files) alongside the app, in addition to the
  same files being on the jar's classpath as usual. Exception:
  `app-version.properties` is left out there - it is only read from the
  (filtered) copy inside the jar, and an on-disk copy would just show the
  unresolved `${app.version}` placeholder.
- `app/` - the executable jar (all dependencies, e.g. Gson and H2, merged in
  via `maven-shade-plugin`) and jpackage's own launcher config. The shade
  plugin also merges `META-INF/services/*` (`ServicesResourceTransformer`,
  so `META-INF/services/java.sql.Driver` -> `org.h2.Driver` survives) and
  sets `Multi-Release: true` in the manifest (H2 ships Java-21-specific
  classes under `META-INF/versions/21/`).

The zip is attached as a secondary artifact, so `mvn deploy` uploads it to
the repository in `distributionManagement` alongside the plain project jar.
Pass `-Ddist.skip=true` to skip all of this (e.g. for a quick
`mvn clean test`) without touching the rest of the build.

Implementation: `maven-shade-plugin` (executable jar) ->
`org.panteleyev:jpackage-maven-plugin` (app-image, wraps the JDK's
`jpackage`) -> `maven-assembly-plugin` (zips `target/dist/Cow2Win/`, see
`src/assembly/windows-app.xml`) - all three bound to the `package` phase in
`pom.xml`.

**Adding a dependency or JDK API:** the IDE runs on the full JDK, the
installed app only on the trimmed `runtime/` - a missing module only shows
up there (`NoClassDefFoundError`, e.g. `java/sql/DriverManager`). After
`mvn package`, check the shaded jar and compare with `addModules`:

```
jdeps --multi-release 24 --print-module-deps --ignore-missing-deps target/jpackage-input/Cow2Win.jar
```

Modules that jdeps lists but `addModules` deliberately leaves out (H2
features not used: `java.compiler`, `java.scripting`, `java.instrument`,
`jdk.net`) are explained in the comment there. Afterwards check
`target/dist/Cow2Win/runtime/release` (`MODULES=...`).

If `mvn clean` fails with "Failed to delete ...\Cow2Win.exe", the exe from
the previous jpackage run is read-only - `attrib -R target\dist\* /S /D`
fixes it.

## Workspace, configuration & backups

- **Workspace** - all user data: one subfolder per guild (`guild.json` plus
  any number of `.lineup` files), the editable CowScore files
  (`cowScore.json`, `titanCowScore.json`, `petCowScore.json`,
  `warFlagCowScore.json`), the hand-maintained hero combos
  (`heroCombos.json`), the team templates (`heroTemplates.json`,
  `titanTemplates.json`), the log file and - per guild that has imported
  battle logs - the Weltenschlacht journal database (see below). Defaults to
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
  The journal databases are part of the ZIPs; the backup runs before any of
  them is opened.
- **Weltenschlacht journal** - one embedded H2 database per guild,
  `<guild folder>/journal.mv.db`, created only when the first battle log of
  that guild is saved (`org.c2w.data.journal.db.JournalDatabase`). Each
  stored log keeps its original CSV (gzip) with SHA-256 and the parser
  version, so logs can be read again after a parser improvement. The schema
  is versioned (table `schema_version`, scripts
  `src/main/resources/journal/schema/V<n>__<name>.sql`, registered in
  `SchemaMigrator.SCRIPTS`) and migrated when the database is opened; a
  database from a newer Cow2Win is refused, not touched. The connection
  belongs to the open guild (`org.c2w.service.JournalService`): opened on
  first use, closed on a guild switch, before the guild folder is deleted
  and at exit. H2 locks the file, so a second Cow2Win instance cannot open
  the same journal.
- **First start** (`org.c2w.service.WorkspaceBootstrap`) - creates a "Demo" guild pre-filled from
  `data/guild.json`/`data/default.lineup` (read from the packaged
  `resources/` folder, or from `src/main/resources` in the IDE).

## Weltenschlacht Journal (user guide)

The journal collects the battle logs of Clash of Worlds, one database per
guild (see "Workspace, configuration & backups").

- **Export in the game:** only the **guild master** can export the logs of
  the own guild - per battle an attack log and a defense log (CSV). Older
  battles and running battles (partial state) can be exported too; the file
  name must stay as the game wrote it (date, guilds, result).
- **Import:** menu **Weltenschlacht Journal → Import …** (or drop the CSV
  files onto the battle list). One or both files of a battle, also several
  battles at once. The assistant shows an overview (new / replaces the
  stored log / unchanged, ranking points check) and asks only what it needs:
  link the Cow2Win guild with the game guild (first import), the season
  (12 weeks), unknown players, unknown names. "Cancel" changes nothing.
- **Defense only:** Cow2Win plans the defense. Players are only assigned
  from the **defense log** (our defenders); new members are created
  **without teams**. The attack log is stored completely but never changes
  the guild. If the import changes the guild (link, new/renamed members),
  the guild is unsaved afterwards - save it ("Save guild now" on the result
  page).
- **What is stored:** every single fight with both sides, teams (if the log
  has them), fortification buffs, points, the original CSV file and the
  season. A later export of the same battle replaces the earlier one.
- **Battle list:** menu **Weltenschlacht Journal → Battle list …** - date,
  opponent, result, ranking points, points, stored logs, season. Filters:
  season, opponent (contains), period from/to, status (running/finished),
  result; "n of m battles". Double click, Enter or **Details …** opens the
  battle; right click (or the Delete key) offers **Parse again**, **Save
  original CSV as**, **Assign season …** and **Delete battle …** - several
  selected battles at once for parsing again and deleting.
- **Battle detail** (several windows at once): head data (guilds with server
  and game id, result or "running", ranking points and their check, points,
  season, per log language, file, import time, parser version, parse
  problems) and the tabs
  - **Defense** first - per fortification our defenders in file order with
    the assigned member ("Puschel → Puschel", "Vale·· (open)"), team power,
    the attacker, **held/fallen** from our side and the opponent's points,
    plus a short evaluation (held/lost, points per fortification, fallen
    fortifications). Without a defense log: a hint and **Import …**.
  - **Attack** - display only, our attackers against the opponent's defense
    teams.
  - **Fortifications** - per fortification and direction: positions,
    undefended, fights, wins/losses from our side, points, captured.
  - **Problems** - only if the parser reported any.

  Selecting a fight shows both teams with images, stars, color, level,
  power, damage dealt/taken, healing and pet/patronage - if the log has them
  (attack logs always, defense logs rarely, e.g. 17.09.2026).
- **Deleting:** a battle (or several) from the list or the detail, a season
  in **Seasons …** - only the season (battles stay, without season) or with
  all its battles. Every confirmation says exactly what goes (e.g. "battle of
  24.09.2026 against Das Schwarze Auge – 2 logs, 124 single fights").
  Deleting the guild deletes its folder including the journal; the
  confirmation mentions the journal (with its number of battles) if there is
  one.
- **Players …:** the own guild's players exactly as in the log (spaces made
  visible) with status, assigned member (hint if that member no longer
  exists; several log names of one member after renames are visible),
  defenses, last seen and last team power(s) from the defense logs. Change
  the status or the member - this never changes the guild (no renaming, no
  new members). Filters "only open"/"only problems"; below, members without
  a journal player or missing from the last 3 defense logs (possible typo,
  name change or left the guild).
- **Seasons …:** create (suggested after the last season in the 12-week
  raster), edit (end suggestion start + 12 weeks), delete. Overlapping
  seasons are refused. After every change all battles are assigned to the
  season containing their date (battles outside all seasons get none) -
  the dialog says beforehand how many battles change their season.
  **Assign season …** in the battle list offers only seasons containing the
  battle's date, or none.
- **Name mappings …:** the manual mappings of unknown names (kind, raw name,
  catalog entry with image) - change or delete. They apply to future imports;
  stored battles follow with **Parse all battles again**.
- **Starting with an empty guild:** create the guild, then
  1. import the battle logs (several battles at once is fine) and press
     **Create all without suggestion as new members** in the "Players" step
     (respects the limit of 30 members; **Reset all** undoes it) - the
     members come from the defenders of the defense logs, without teams;
  2. **Weltenschlacht Journal → Build teams from logs …** (also offered on the
     import result page): per member the teams of the newest battle it
     defended in (kind from the fortification, power, fortification/position)
     are preselected; teams seen only in older battles are offered unchecked.
     Heroes/titans come only from the defense log's units (rare, e.g.
     17.09.2026) or from an attack team of the same player in the **same
     battle with exactly the same power** (no tolerance - verified against a
     real guild); otherwise the team gets its power only. Compositions known
     from other battles can be taken over by hand. Teams with heroes/titans
     are never changed - only missing teams are added and empty teams filled;
     lineups are not touched. The guild is unsaved afterwards ("Save guild
     now");
  3. add the missing heroes/titans by hand in the guild editor. Every further
     imported battle can complete more teams.
- **Update teams (sync):** menu **Weltenschlacht Journal → Update teams (sync) …**
  (also offered on the import result page when the import had a defense log)
  keeps the existing teams up to date from the **defense log of the newest
  battle** that has one (never the attack log; older battles never overwrite
  newer values). Each team of an assigned member in the log is matched to a
  stored team of the same kind - by the same heroes/titans if the log has
  units, otherwise by power (one-to-one, smallest total deviation): **≤ 3 %
  sure**, **≤ 10 % unsure**, another stored team within 0.5 percentage points
  makes it **ambiguous**, beyond 10 % there is no match (shown below the table,
  with a link to "Build teams from logs …"). Per row you choose whether to take
  over the **power** and - only if the defense log has units - the **units**
  (heroes/titans with pet or totems; war flags are not in the log and stay).
  Sure matches with a different power are preselected, unless the team was
  edited after the battle; units never are. The team of a row can be changed
  (no team twice). Hints show teams edited since the battle, empty teams
  (power only), and teams that stand elsewhere in the open lineup. No teams or
  members are created and lineups are never changed. Changed teams get the
  battle day as modification date; the guild is unsaved afterwards ("Save guild
  now").
- **Parse again:** reads the stored original CSVs with the current parser and
  name mappings and replaces the logs - for one, several or all battles.
  Seasons and player assignments stay, no questions are asked and the guild
  is not changed (new unknown players stay open). The result shows the number
  of logs and the parse problems before → after.

## Architecture

Swing desktop app, entry point `org.c2w.C2WApp`. Packages under `org.c2w`,
roughly from top to bottom:

| Package | Contents |
|---|---|
| `gui` (+ subpackages `fort`, `guild`, `hero`, `titan`, `pet`, `flag`, `journal`, `common`) | Swing frames, panels and dialogs. How data looks on screen (e.g. `FortificationTypeStyle` for the hero/titan colors and slot icons) lives here, not in the model. They only collect input and show results; every change to the open guild/lineup goes through `service`. |
| `service` | Application layer. `AppContext` holds the open guild/lineup, their files, the unsaved-changes state and the selected fortification type (heroes or titans), and notifies `AppContext.Listener`s (map, context bar, action bar, window title) of every change. `JournalService` owns the journal database of the open guild; `JournalImportService` imports battle logs into it in two steps (`prepare` collects GUI-independent questions in `service.journal`, `execute` applies the answers); `JournalMaintenanceService` deletes battles/seasons, edits seasons (reassigning battles by date in the same transaction), corrects player assignments and name mappings and parses stored logs again - never changing the guild; `JournalTeamBuilderService` proposes defense teams for guild members from the defense logs (`prepare` → `TeamBuildPlan`, `apply` with a `TeamBuildSelection`); `JournalSyncService` updates the existing teams (power, units if the log has them) from the newest defense log (`prepare` → `SyncPlan`, `apply` with a `SyncSelection`); both share the building blocks in `service.journal.JournalTeams` (defenders → members, log teams per fortification/position, stored-team view, building hero/titan teams). Cow2Win is about defense: only the defense log may change the guild (player assignment, rename, new members without teams), the attack log is only stored - with one exception: when building teams, an attack team of the same player with exactly the same power in the same battle may supply the composition. `GuildService`/`LineupService` are the use cases (create/switch/delete/save guilds and lineups, run algorithms, assign teams) and keep context, files and `config.properties` consistent. `WorkspaceBootstrap` does everything before the first window opens (first-run setup, config, backups, catalogs, reopening the last guild/lineup). |
| `eval` | The lineup algorithms (`LineupAlgorithm`, registered per side in `LineupAlgorithms`). |
| `domain` | Scoring and lineup analysis: `TeamScoreCalculator` (CowScore), `BuffCalculationService`, `LineupBaseline`, `LineupComparisonService`, `LineupChangePlanService`. |
| `report` | `ReportGenerator` - the HTML lineup report. |
| `data.model` | Immutable records (`Hero`, `Titan`, `Pet`, `WarFlag`, `Fortification`, `Guild`, `Lineup`, ...). |
| `data.repository` | Loading/saving. `Catalog` bundles the hero/titan/pet/war flag repositories of one workspace (created once at startup, reachable via `AppContext#catalog()`); `FortificationRepository` (pure classpath data) is still static. `GuildRepository`/`LineupRepository` read and write guild and lineup files, `LineupFiles` holds the rules for the reserved "Original" lineup. |
| `data.journal` (+ `parse`, `db`) | Weltenschlacht journal: immutable records of a parsed battle log (`BattleLog`, `Fight`, `FightUnit`, ...), the CSV parser in `parse` (`BattleLogParser`, `BattleLogFileName`, `BattleLogVocabulary`, `NameResolver`, `BattleLogCheck`) and the per-guild H2 database in `db` (`JournalDatabase`, `SchemaMigrator`, `JournalRepository`). The GUI is in `gui.journal` (menu `JournalActions`, import assistant, battle list and detail, players/seasons/name mapping windows; "build teams from logs" (`JournalTeamBuilderDialog`) and "update teams (sync)" (`JournalSyncDialog`); the logic in Swing-free models such as `ImportWizardModel`, `BattleDetailModel`, `BattleListFilter`, `JournalPlayersModel`, `TeamBuilderModel`, `SyncModel`). |
| `i18n` | `LanguageService` (UI texts, see "Canonical data files"), `BuffTexts`, `TotemTexts` and `GameNameNormalizer` (how in-game names are compared). |
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
  and reports whether a newer release exists. `Cow2Frame` runs a silent
  check once at startup (only ever shows a dialog when an update was
  actually found - no popup for "up to date" or a failed/offline check).
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
  includes the rare `DISTORTION` element used by some event titans. Like
  heroes, its curated fortification marks (`FortMarks`: per titan
  fortification `POSITIVE` or `NEGATIVE`, unmarked = neutral) live in a
  separate file, `titanCowScore.json`.
- **Pet** / **War flag** (`id`, `image`) - optional per hero team, each at
  most once per member. Their `FortMarks` (positive only) live in
  `petCowScore.json`/`warFlagCowScore.json`.
- **CowScore** of a hero team at a fortification: `totalPower / 100 000 x
  (1 + B)`, where the bonus `B` comes from matching roles, marked heroes,
  a marked pet, the war flag and a matching hero combo - see
  `TeamScoreCalculator`'s class Javadoc.
  Titan teams use the same formula, with `B` from matching elements
  (1.5 % per titan), marked titans (+/-1.25 % once per team) and totems
  (1.25 % per totem).
- **Fortification** (`id`, `type` HERO/TITAN, `capacity`, `captureBonus`,
  `row`/`column` for the map layout, `buff`, `prerequisites`,
  `strategicImportance`) - `prerequisites` is an **OR-relation**: capturing
  *any one* of the listed fortifications unlocks this one, not all of them
  together. See the class javadoc on `Fortification` for the full rules,
  including how `strategicImportance` is assessed.
- **Guild** (`id`, `name`, up to 30 `members`, optional `gameGuildId`) - one
  guild is one subfolder under `workspace/`. `gameGuildId` is the guild's id in
  the game (from a battle log's file name), set by the journal import.
  Code that rebuilds a guild keeps it via `Guild.withMembers`.
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
- Display names live in the language files (keyed by id) and are the
  **in-game names** as they appear in an exported battle log, per language -
  the only basis for mapping log names back to catalog ids (see
  `PATCH-CHECKLIST.md`, "Game names").
- `src/test/resources/battlelog/de|en|fr/` hold the same 6 battles (attack
  and defense log each, unchanged file names) in all three game languages,
  `battlelog/partial/` an earlier export of the running battle of 01.10.2026 -
  test data for the Weltenschlacht journal; `.gitattributes` keeps them
  byte-identical (CRLF) on every machine.
- `src/test/resources/guild/deutscher-bund.json` is a copy of the real guild
  "Deutscher Bund" (state 04.10.2026) - the truth for the team compositions
  the journal's team builder proposes (`JournalTeamBuilderServiceTest`) and
  the guild the sync is checked against (`JournalSyncServiceTest`).
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
  "CowScore settings ..." - one `CowScoreDialog` with a tab (panel) each for heroes,
  titans, pets and war flags (`HeroCowScorePanel`, `TitanCowScorePanel`,
  `PetCowScorePanel`, `WarFlagCowScorePanel`) -
  whose "restore defaults" button resets the active tab to the shipped values.
- Format: all four files use `FortMarkFiles`
  (`[{"id": "corvus", "fortMarks": {"foundry": "POSITIVE"}}, ...]`; a
  workspace copy still in the former tier-based format
  (`generalScore`/`buffFitScores`) is backed up as
  `<name>.legacy-<date>.bak` and migrated on load). A
  `generalScore`/`buffFitScores` left over in `titans.json` is ignored and
  logged.
- Hero combos (`heroCombos.json`, see `TeamComboFiles`): heroes with
  synergies not visible in the power -
  `[{"id": "krista-lars", "heroIds": ["krista", "lars"], "source": "C2W", "deactivated": "2026-09-30"}, ...]`
  (2-5 heroes, `deactivated` optional). Combo names are not in the
  language files: a combo is displayed by its heroes' localized names
  ("Krista + Lars", see `ComboTexts`), unless the optional `name` sets a
  custom label, which is shown untranslated. There is no in-app editor yet - the
  workspace copy is edited by hand. On every start the shipped `C2W` combos
  are merged in (replaced/added/removed by `id`, the previous file backed up
  as `heroCombos.json.before-update-<date>.bak`); `USER` combos are never
  touched. **A shipped combo edited or deactivated by hand must get
  `"source": "USER"`**, otherwise the next start replaces it. Invalid
  combos are logged and ignored.
- Team templates (`heroTemplates.json`/`titanTemplates.json`, see
  `TeamTemplateFiles`): in any team row F1-F5 fills the 5 slots with template
  1-5 (power, war flag and pet stay), Shift+F1-F5 saves the row as that
  template right away (asking before overwriting) -
  `[{"slot": 1, "titanIds": ["eden", "angus"]}, ...]` (slot 1-5, 1-5 ids).
  Workspace-wide, not per guild. Only titans ship defaults (one per element,
  F1 earth, F2 fire, F3 water, F4 light, F5 dark), copied once when
  `titanTemplates.json` does not exist yet - afterwards the file belongs to
  the user. Deleting a template: by hand in the file. Invalid entries are
  logged and ignored; ids no longer in the catalog are skipped when applying.
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
- Next to its `.properties` file, every language folder holds a
  `battleLogVocabulary.json`: the texts of the Clash of Worlds battle logs in
  that game language (file name words, column header, results, "captured"
  and "undefended" sentences, stats header, buff and color texts), read by
  `org.c2w.data.journal.parse.BattleLogVocabulary`. Not shown in the UI, so
  not part of the `.properties` files; same keys in every language
  (`BattleLogVocabularyConsistencyTest`).- Each language's `.properties` file also carries an `algorithm.<key>.name`
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
- `TitanRepositoryTest` - the titan marks in `titanCowScore.json`
  (positive/negative round trip, migration of the former tier-based
  format including the `.legacy-<date>.bak` backup).
- `FortMarkFilesTest` - the fortification-mark format of
  `cowScore.json`/`titanCowScore.json`/`petCowScore.json`/`warFlagCowScore.json`, including the
  migration of the former tier-based format and a save/reload round trip
  through `HeroRepository`.
- `TeamScoreCalculatorHeroTest` - the hero-team CowScore formula (role
  buff, hero relation, pet and war flag bonuses).
- `TeamScoreCalculatorComboTest` - the hero combo bonus (+1.25 % once,
  deactivated/partial combos, effect on `sortScore`).
- `TeamComboFilesTest` - loading/validating `heroCombos.json` and merging
  the shipped combos into the workspace copy.
- `ComboTextsTest` - a combo's display name (localized hero names or the
  custom `name`).
- `TeamScoreCalculatorTitanTest` - the titan-team CowScore formula
  (element buff, titan relation, totems, breakdown).
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
- `BattleLogGameNamesTest` - every hero, titan, pet, totem and fortification
  name in the sample battle logs is a game name in that log's language file.
- `org.c2w.data.journal.parse` tests - the battle log parser against all 38
  sample logs: acceptance table (fights, undefended/captured rows, points,
  units, buffs per file), DE/EN/FR yield the same language-neutral result,
  append-only (an earlier export is a prefix of a later one), file names,
  ranking points control calculation, spot checks and robustness against
  broken rows; plus the vocabulary consistency of the three languages.
- `org.c2w.data.journal.db` tests - the journal database in temp folders:
  creating/opening/migrating (incl. an artificial V2, a database from a newer
  version and a second process holding the lock), the round trip of all 38
  sample logs (saved and loaded again = parser result, original bytes back),
  replacing a partial export, status, seasons, assignments, name mappings,
  deleting with cleanup and the battle list. `JournalServiceTest` covers the
  connection lifecycle (lazy, guild switch, `deleteGuild`).
  `JournalRepositoryMaintenanceTest` covers the phase-5 additions: deleting
  name mappings, `reassignSeasonsByDate` with both previews, counts for the
  confirmations, log infos, player statistics (one query, defense logs only),
  the defenders of the latest defense logs, replacing an unchanged file and
  `reparseLog` with new name mappings.
- `JournalMaintenanceServiceTest` - deleting battles and seasons, editing
  seasons (preview, reassignment, overlap), manual season, player
  assignments, name mappings and parsing again - each test checks that the
  guild (in memory and `guild.json`) is unchanged.
- `JournalImportServiceTest` - import scenarios with a temp workspace and the
  sample logs: first import (guild link, first season), `prepare` writes
  nothing, attack log only (no player questions), second direction later,
  replacing a partial export, several battles and the season raster,
  duplicate direction, foreign guild, players from the defense log (exact,
  normalized, similar, unknown; ASSIGN/CREATE/NOT_IN_COW2WIN/OPEN), renames
  via team power and via units, the attack log changes nothing, member limit,
  unknown names, rollback on a write error.
- `org.c2w.gui.journal` tests - the Swing-free import assistant model
  (`ImportWizardModelTest`: steps, defaults, answers, blocks, summary - with
  real plans from the sample logs), texts for every journal enum in all
  languages (`JournalTextsTest`), the battle list table model incl. season
  filter and the empty state without journal file, `Config.lastJournalImportDir`,
  and a smoke test of the assistant pages (`JournalPanelsSmokeTest`).
  Phase 5: `BattleDetailModelTest` (grouping per fortification, held/fallen,
  points per fortification, units, attack log only, player labels),
  `JournalPlayersModelTest`, the battle list filters in
  `JournalBattleTableModelTest`, `JournalMaintenanceTextsTest` (confirmation
  texts, guild delete note, every used key in every language) and
  `JournalDialogsSmokeTest`, which builds and paints all journal windows
  off-screen (skipped without a display; `-Djournal.smoke.out=<folder>` saves
  the renderings as PNG).
  Phase 6: "create all without suggestion" in `ImportWizardModelTest`,
  `TeamBuilderModelTest`, and `JournalTeamBuilderServiceTest` - end to end
  (empty guild → 6 battles → create all → build teams; every exact-power
  composition matches the real guild), exact power without tolerance, limits,
  older battles, skipped players, filling an empty team, pet/totem
  validation, and the real guild as open guild (teams with heroes untouched).
  Phase 7: `JournalSyncServiceTest` - source (newest battle with a defense
  log, no journal, attack log only), the real guild against the running
  battle of 01.10. and the units of 17.09., power matching (exact, sure,
  unsure, no match, ambiguous), optimal one-to-one pairing, empty team, edited
  since the battle, skipped players, renames, lineup hints, apply (only the
  selection, lastModified, war flag kept, same team twice refused, dropped
  pet/totem) and that the attack log never matters; `SyncModelTest` (filters,
  preselection, swapping targets, hints) and a smoke test of the dialog.
- `H2SmokeTest` - the H2 dependency: driver registered via
  `META-INF/services`, a file database in a temp directory, a Unicode round
  trip (Cyrillic, accents, umlauts, non-breaking space, emoji) and deleting
  the database files after the last connection is closed (no Windows file
  lock left).

## More context

The broader background for this project - Clash of Worlds game rules, the
schedule/season/reward details that are deliberately *not* stored in this
repo's JSON files, buff/captureBonus reference tables, and the ongoing
improvement roadmap - lives in the "Cow2Win" Claude Project
(`clash-of-worlds-event-recherche.md` and
`cow2win-verbesserungsvorschlaege.md`), not in this repository.
