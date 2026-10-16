-- Clash of Worlds journal, schema version 2: seasons are no longer stored per journal.
-- The season of a battle is computed from its date with the shipped season calendar
-- (org.c2w.data.journal.SeasonCalendar). Stored seasons and their notes are dropped
-- on purpose; the battles stay.
DROP INDEX ix_battle_season;
ALTER TABLE battle DROP CONSTRAINT fk_battle_season;
ALTER TABLE battle DROP COLUMN season_id;
DROP TABLE season;
