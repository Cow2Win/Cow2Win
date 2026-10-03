# Patch checklist

Hero Wars: Dominion Era patches regularly (new heroes/titans/pets, balance changes,
sometimes new Clash of Worlds fortifications or rule tweaks). Cow2Win's data
files don't update themselves, so after any patch that could touch Clash of
Worlds, work through this list before trusting the app's output again. It's
organized by the actual files/checks involved, not by patch-note wording.

## 1. New heroes

- [ ] Add an entry to `src/main/resources/data/heroes.json`: `id`, `roles`
      (array - check for heroes with **two** roles, e.g. Cleaver =
      TANK + CONTROL; see `Role.java` for the valid values), `image`.
- [ ] Export/add the avatar icon under `src/main/resources/images/heroes/`.
      Missing icon → falls back to `placeholder.png` (see `Hero.java`), not
      an error, but worth noticing.
- [ ] Add the display name to **all three** language files
      (`src/main/resources/language/deutsch/deutsch.properties`,
      `english/english.properties`, `francais/francais.properties`) -
      `displayName` is looked up from there at runtime (`LanguageService`),
      not stored on the `Hero` record itself.
- [ ] If the new hero deserves a deliberate `generalScore`/`buffFitScores`
      assessment (most don't - `STANDARD`/no override is the expected
      default), set it via the "Hero Buff Fit Scores" dialog in-app, or by
      hand in `src/main/resources/data/cowScore.json` - NOT in
      `heroes.json` (see README.md, "Canonical data files": the two are
      deliberately separate files since 2026-09-14).

## 2. New titans

- [ ] Add an entry to `src/main/resources/data/titans.json`: `id`, `element`
      (see `TitanElement.java` - includes the rare `DISTORTION` element for
      event titans), `image`.
- [ ] Avatar under `src/main/resources/images/titans/` (same
      placeholder-fallback note as heroes).
- [ ] Display name in all three language files.
- [ ] If the titan brings a **new element** (new `TitanElement` constant):
      besides `titanElement.<NAME>`, also add `titanTotem.<NAME>` in all three
      language files - the totem's in-game name exactly as it appears in a
      Clash of Worlds battle log (needed to map log entries back to the
      element; `TotemTextsGameNameTest` fails without it).
- [ ] If the new titan deserves deliberate fortification marks
      (`fortMarks`, positive/negative), set them via "File" > "CowScore - Titans" in-app, or by
      hand in `src/main/resources/data/titanCowScore.json` -
      NOT in `titans.json` (separate files since 2026-09-28, same as heroes).

## 3. New pets

- [ ] Add an entry to `src/main/resources/data/pets.json`: `id` (lowercase,
      used as the language key) and `image`. Pets have **no** roles and no
      element - there is nothing else to fill in.
- [ ] Avatar under `src/main/resources/images/pets/` (same
      placeholder-fallback note as heroes - see `Pet.java`).
- [ ] Display name in all three language files (section `# Pets` /
      `# Familiers`). Check that the new `id` doesn't collide with an
      existing hero, titan or fortification key - all of them share one
      flat key namespace in the language files.
- [ ] If the new pet deserves a deliberate `generalScore`/`buffFitScores`
      assessment, set it via "File" > "CowScore - Pets" in-app, or by hand
      in `src/main/resources/data/petCowScore.json` - NOT in `pets.json`
      (same master-data/score split as heroes and titans, see
      `PetRepository.java`).

## 4. New war flags

- [ ] Add an entry to `src/main/resources/data/warFlags.json`: `id`
      (lowercase with a `flag-` prefix, e.g. `flag-bastion` - used as the
      language key; the prefix avoids collisions like the `bastion`
      fortification) and `image`. War flags have **no** roles and no
      element - there is nothing else to fill in.
- [ ] Icon under `src/main/resources/images/flags/` (same
      placeholder-fallback note as heroes - see `WarFlag.java`).
- [ ] Display name in all three language files (section `# Kriegsflaggen` /
      `# War flags` / `# Drapeaux de guerre`).
- [ ] If the new war flag deserves a deliberate `generalScore`/`buffFitScores`
      assessment, set it via "File" > "CowScore - War Flags" in-app, or by
      hand in `src/main/resources/data/warFlagCowScore.json` - NOT in
      `warFlags.json` (same master-data/score split as heroes, titans and
      pets, see `WarFlagRepository.java`).

## 5. Fortification changes (`fortifications.json`) - highest risk

This file drives `BestPossibleLineupAlgorithm` directly, so an error here
silently produces a wrong "optimal" lineup rather than an obvious crash.

- [ ] **New fortification added to the Clash of Worlds map?** New entry with
      `id`, `type` (HERO/TITAN), `capacity`, `captureBonus`, `row`/`column`
      (map layout only), `buff` (or omit for none), `prerequisites`,
      `strategicImportance`. `strategicImportance` is a manually-assessed
      catalog value (1-10, NOT read from the game) - see the heuristic in
      `Fortification.java`'s class javadoc ("how many other fortifications
      only get unlocked by capturing THIS one").
- [ ] **Buff value or effect changed** on an existing fortification? Update
      `bonusPercent`/`effect` in its `buff` object. Cross-check against the
      role/element buff tables in `clash-of-worlds-event-recherche.md`
      (Cow2Win Claude Project) and update those tables too.
- [ ] **captureBonus changed**? Update it, and refresh the captureBonus
      table in the same research doc.
- [ ] **capacity (team slots) changed**?
- [ ] **Prerequisites/unlock chain changed** (fortification added to or
      removed from the graph, or renamed)? Update `prerequisites`.
      Remember: it's an **OR relation** - capturing ANY ONE listed
      prerequisite unlocks the fortification, not all of them together (see
      `Fortification.java` javadoc; the research doc got this wrong once
      before a codebase re-check).

## 6. Game names (battle logs)

The display names in the language files are the **in-game names** exactly as
they appear in an exported Clash of Worlds battle log (`.csv`) - the
Weltenschlacht journal maps log names back to catalog ids through them, and
only through them (there are no aliases).

- [ ] New or renamed hero/titan/pet/fortification: take the name for each
      language from a battle log in that language (game language DE/EN/FR),
      not from the patch notes or a wiki - spelling, apostrophe (`'`) and
      "und/and/et" exactly as in the log.
- [ ] Got a new battle log? Copy it into the folder of its game language,
      `src/test/resources/battlelog/de|en|fr/` (file name unchanged, it is
      test data too; `partial/` holds an earlier export of a running battle),
      and run `mvn test`: `BattleLogGameNamesTest` lists every name in the
      sample logs that has no matching game name in its language.
      The acceptance tests in `org.c2w.data.journal.parse` count the sample
      files - adjust them when adding a file.
- [ ] New log texts (e.g. a new fortification buff, a new hero color, a new
      wording of the "undefended" sentence) go into
      `language/<name>/battleLogVocabulary.json` of **all three** languages,
      same keys everywhere (`BattleLogVocabularyConsistencyTest`). Never
      invent a text that no log has shown - leave the list empty instead
      (like `buff.SKILL_COOLDOWN_DECREASE`). The parser reports unknown texts
      as parse problems.
- [ ] New language: besides `<name>/<name>.properties` the language folder
      also needs a `battleLogVocabulary.json` - without it, battle logs in
      that language cannot be read.

## 7. After any data change

- [ ] Re-run `BestPossibleLineupAlgorithmTest` (`mvn test`). If the total
      fortification count changed from 20, or the unlock chain changed,
      update both `realCatalogMatchesDocumentedDepths`'s expected table in
      the test AND the depth table in `clash-of-worlds-event-recherche.md` -
      they're independent copies of the same fact and won't warn you if they
      drift apart.
- [ ] Spot-check in the running app: reload a guild, run the algorithm once,
      sanity-check the exported report against what you just changed.
- [ ] Once confirmed, update `clash-of-worlds-event-recherche.md` in the
      Cow2Win Claude Project (captureBonus table, buff tables, unlock-depth
      table) so it doesn't quietly go stale relative to the actual data
      files.
- [ ] Update `dataVersion` (and `note`, if useful) in
      `src/main/resources/data/catalog-version.json` to today's date - it's
      logged at app startup so it's visible at a glance which patch state
      the catalog data was last checked against.
