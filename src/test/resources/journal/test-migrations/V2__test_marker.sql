-- Artificial schema version 2 for JournalDatabaseTest: proves that a V1 database is migrated on opening.
CREATE TABLE test_marker (
    id   INT NOT NULL PRIMARY KEY,
    note VARCHAR
);
INSERT INTO test_marker (id, note) VALUES (1, 'migrated');
