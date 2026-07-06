-- Salary-cap roster support.
-- players.salary is populated by the data worker (or seeded manually) and may be
-- NULL until then; the engine treats NULL as 0 when summing a roster.

ALTER TABLE players
  ADD COLUMN salary DECIMAL(12, 2);

ALTER TABLE league
  ADD COLUMN salary_cap DECIMAL(14, 2) NOT NULL DEFAULT 50000000.00,
  ADD COLUMN roster_size INT NOT NULL DEFAULT 10;
