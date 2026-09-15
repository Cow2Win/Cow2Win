# Patch checklist

Hero Wars: Dominion Era patches regularly (new heroes/titans, balance changes,
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
      (`src/main/resources/language/deutsch.txt`, `english.txt`,
      `francais.txt`) - `displayName` is looked up from there at runtime
      (`LanguageService`), not stored on the `Hero` record itself.
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

## 3. Fortification changes (`fortifications.json`) - highest risk

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

## 4. After any data change

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
