-- Test data for integration tests
-- This file contains minimal test data for running integration tests

-- Clear existing data (ignore errors if tables don't exist)
DELETE FROM interview_steps WHERE 1=1;
DELETE FROM projects WHERE 1=1;
DELETE FROM contacts WHERE 1=1;
DELETE FROM sources WHERE 1=1;
DELETE FROM clients WHERE 1=1;
DELETE FROM freelances WHERE 1=1;

-- Insert test freelance (using actual column names from Freelance entity)
-- Note: Not specifying ID to let auto-increment handle it
INSERT INTO freelances (first_name, last_name, email, phone, birth_date, address, city, status, notice_period_in_days, availability_date, reversion_rate, cv_file_path, password_hash, created_at, updated_at, version) VALUES
('John', 'Doe', 'john.doe@example.com', '+33 1 23 45 67 89', '1985-03-15', '123 Rue de la Paix', 'Paris', 'FREELANCE', 30, '2024-02-01', 0.15, '/cv/john-doe.pdf', '$2a$10$encrypted_password_hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Insert test client (using actual column names from Client entity)
-- Note: Using freelance_id = 1 since the freelance above will get ID = 1
INSERT INTO clients (company_name, address, city, domain, is_final, notes, freelance_id, created_at, updated_at, version) VALUES
('Test Company', '456 Avenue Test', 'Paris', 'Technology', true, 'Test client for integration tests', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Insert test contact (using actual column names from Contact entity)
-- Note: Using client_id = 1 and freelance_id = 1 since they will get ID = 1
INSERT INTO contacts (first_name, last_name, email, phone, notes, client_id, freelance_id, created_at, updated_at, version) VALUES
('Jane', 'Smith', 'jane.smith@testcompany.com', '+33 1 98 76 54 32', 'Primary contact for Test Company', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Insert test source (using actual column names from Source entity)
-- Note: Using freelance_id = 1 since the freelance above will get ID = 1
INSERT INTO sources (name, type, link, is_listing, popularity_rating, usefulness_rating, notes, freelance_id, created_at, updated_at, version) VALUES
('LinkedIn', 'JOB_BOARD', 'https://linkedin.com', false, 5, 4, 'Professional networking platform', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Insert test project (using actual column names from Project entity)
-- Note: Using client_id = 1, freelance_id = 1, source_id = 1 since they will get ID = 1
INSERT INTO projects (role, description, tech_stack, daily_rate, work_mode, remote_days_per_month, onsite_days_per_month, advantages, start_date, duration_in_months, order_renewal_in_months, days_per_year, link, personal_rating, notes, client_id, freelance_id, source_id, created_at, updated_at, version) VALUES
('Senior Full Stack Developer', 'Development of a modern web application using Angular and Spring Boot', 'Angular, Spring Boot, PostgreSQL', 650, 'HYBRID', 15, 5, 'Modern tech stack, flexible working conditions', '2024-01-15', 12, 6, 220, 'https://project.example.com', 4, 'Excellent project with great team', 1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Insert test interview step (using actual column names from InterviewStep entity)
-- Note: Using project_id = 1 since the project above will get ID = 1
INSERT INTO interview_steps (title, date, status, notes, project_id, created_at, updated_at, version) VALUES
('Phone Screening', '2024-01-10 14:30:00', 'VALIDATED', 'Candidate performed well', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
