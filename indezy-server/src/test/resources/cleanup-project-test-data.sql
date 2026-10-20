-- Cleanup script for ProjectIntegrationTest
-- This script removes all test data to ensure test isolation

-- Delete in reverse order of dependencies to avoid foreign key constraint violations
DELETE FROM saved_offer_searches WHERE freelance_id = 100;
DELETE FROM job_offer_states WHERE freelance_id = 100
    OR imported_project_id IN (SELECT id FROM projects WHERE freelance_id = 100);
DELETE FROM interview_steps WHERE project_id IN (SELECT id FROM projects WHERE freelance_id = 100);
DELETE FROM activity_report_days WHERE report_id IN (
    SELECT report.id FROM activity_reports AS report JOIN projects AS project ON project.id = report.project_id
    WHERE project.freelance_id = 100);
DELETE FROM activity_reports WHERE project_id IN (SELECT id FROM projects WHERE freelance_id = 100);
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
