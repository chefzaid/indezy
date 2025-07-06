-- Cleanup script for ProjectIntegrationTest
-- This script removes all test data to ensure test isolation

-- Delete in reverse order of dependencies to avoid foreign key constraint violations
DELETE FROM interview_steps WHERE project_id IN (SELECT id FROM projects WHERE freelance_id = 100);
DELETE FROM projects WHERE freelance_id = 100;
DELETE FROM contacts WHERE freelance_id = 100;
DELETE FROM sources WHERE freelance_id = 100;
DELETE FROM clients WHERE freelance_id = 100;
DELETE FROM freelances WHERE id = 100;

-- Reset sequences for the separate test database
ALTER SEQUENCE IF EXISTS freelances_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS clients_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS contacts_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS sources_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS projects_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS interview_steps_id_seq RESTART WITH 1;
