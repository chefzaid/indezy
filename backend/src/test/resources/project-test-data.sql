-- Test data for ProjectIntegrationTest
-- This file contains only the minimal data needed for project tests
-- without pre-existing projects to avoid ID conflicts

-- Insert test freelance (using actual column names from Freelance entity) - Using ID 100 to avoid conflicts
INSERT INTO freelances (id, first_name, last_name, email, password_hash, phone, address, city, birth_date, status, created_at, updated_at, version) VALUES
(100, 'Jane', 'Smith', 'jane.smith@example.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM5lIaU0VPDmBr0.AwBG', '+1234567890', '456 Test Ave', 'Boston', '1990-08-20', 'FREELANCE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Insert test client (using actual column names from Client entity) - Using ID 100 to avoid conflicts
INSERT INTO clients (id, company_name, address, city, domain, is_final, notes, freelance_id, created_at, updated_at, version) VALUES
(100, 'Project Test Corp', '123 Test Blvd', 'Chicago', 'CONSULTING', false, 'Test company for project integration tests', 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Insert test contact (using actual column names from Contact entity) - Using ID 100 to avoid conflicts
INSERT INTO contacts (id, first_name, last_name, email, phone, notes, client_id, freelance_id, created_at, updated_at, version) VALUES
(100, 'Bob', 'Wilson', 'bob.wilson@projecttestcorp.com', '+1987654321', 'Test contact for project integration tests', 100, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Insert test source (using actual column names from Source entity) - Using ID 100 to avoid conflicts
INSERT INTO sources (id, name, type, link, is_listing, popularity_rating, usefulness_rating, notes, freelance_id, created_at, updated_at, version) VALUES
(100, 'Project Test Source', 'JOB_BOARD', 'https://projecttestsource.com', false, 5, 4, 'Test source for project integration tests', 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Note: No projects or interview steps are inserted here to avoid ID conflicts during project creation tests
