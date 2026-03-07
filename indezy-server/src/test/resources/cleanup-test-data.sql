-- Cleanup script for integration tests
-- This script removes all test data to ensure test isolation

-- Delete in reverse order of dependencies to avoid foreign key constraint violations
DELETE FROM interview_steps WHERE 1=1;
DELETE FROM projects WHERE 1=1;
DELETE FROM contacts WHERE 1=1;
DELETE FROM sources WHERE 1=1;
DELETE FROM clients WHERE 1=1;
DELETE FROM freelances WHERE 1=1;

-- Reset sequences to ensure consistent IDs across test runs
ALTER SEQUENCE IF EXISTS freelances_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS clients_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS contacts_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS sources_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS projects_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS interview_steps_id_seq RESTART WITH 1;
