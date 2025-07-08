-- Test data for integration tests
-- This file contains minimal test data for running integration tests

-- Clear existing data (ignore errors if tables don't exist)
DELETE FROM interview_steps WHERE 1=1;
DELETE FROM projects WHERE 1=1;
DELETE FROM contacts WHERE 1=1;
DELETE FROM sources WHERE 1=1;
DELETE FROM clients WHERE 1=1;
DELETE FROM freelances WHERE 1=1;
DELETE FROM user_sessions WHERE 1=1;
DELETE FROM user_security_questions WHERE 1=1;
DELETE FROM user_skills WHERE 1=1;
DELETE FROM user_languages WHERE 1=1;
DELETE FROM users WHERE 1=1;

-- Insert test user (using actual column names from User entity)
-- Password is 'password123' hashed with BCrypt
INSERT INTO users (id, first_name, last_name, email, phone, birth_date, address, city, avatar, bio, company, position, website, linkedin, github, timezone, currency, password_hash, last_password_change, theme, language_preference, date_format, time_format, default_view, items_per_page, auto_save, email_notifications, push_notifications, project_updates, client_messages, system_alerts, weekly_reports, marketing_emails, two_factor_enabled, created_at, updated_at, version) VALUES
(1, 'John', 'Doe', 'john.doe@example.com', '+1234567890', '1990-01-01', '123 Main St', 'New York', 'avatar.jpg', 'Software developer', 'Tech Corp', 'Senior Developer', 'https://johndoe.com', 'https://linkedin.com/in/johndoe', 'https://github.com/johndoe', 'America/New_York', 'USD', '$2a$10$XgbOojgg.CTmnSP8gwpOT.aikY7bnfM4cgCrQhgJOh5UAY1lOpC9S', '2024-01-01 00:00:00', 'dark', 'en', 'MM/dd/yyyy', '12h', 'dashboard', 10, true, true, false, true, true, true, false, false, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Insert test user skills
INSERT INTO user_skills (user_id, skill) VALUES
(1, 'JavaScript'),
(1, 'TypeScript'),
(1, 'Angular');

-- Insert test user languages
INSERT INTO user_languages (user_id, language) VALUES
(1, 'English'),
(1, 'French');

-- Insert test user session
INSERT INTO user_sessions (session_id, device, browser, location, ip_address, last_active, is_current, user_id, created_at, updated_at, version) VALUES
('session1', 'Chrome on Windows', 'Chrome', 'New York, NY', '192.168.1.1', CURRENT_TIMESTAMP, true, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Insert test security question
INSERT INTO user_security_questions (question, answer_hash, user_id, created_at, updated_at, version) VALUES
('What is your mother''s maiden name?', '$2a$10$encrypted_answer_hash', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Insert test freelances (using actual column names from Freelance entity)
INSERT INTO freelances (id, first_name, last_name, email, phone, birth_date, address, city, status, notice_period_in_days, availability_date, reversion_rate, cv_file_path, password_hash, created_at, updated_at, version) VALUES
(1, 'John', 'Doe', 'john.doe@example.com', '+33 1 23 45 67 89', '1985-03-15', '123 Rue de la Paix', 'Paris', 'FREELANCE', 30, '2024-02-01', 0.15, '/cv/john-doe.pdf', '$2a$10$encrypted_password_hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(2, 'Jane', 'Smith', 'jane.smith@example.com', '+33 1 98 76 54 32', '1990-07-22', '456 Avenue des Champs', 'Lyon', 'CDI', 60, '2024-03-01', 0.20, '/cv/jane-smith.pdf', '$2a$10$encrypted_password_hash2', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Reset the auto-increment sequence for freelances
ALTER TABLE freelances ALTER COLUMN id RESTART WITH 3;

-- Insert test clients (using actual column names from Client entity)
INSERT INTO clients (id, company_name, address, city, domain, is_final, notes, freelance_id, created_at, updated_at, version) VALUES
(1, 'Test Company', '456 Avenue Test', 'Paris', 'Technology', true, 'Test client for integration tests', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(2, 'Another Corp', '789 Boulevard Test', 'Lyon', 'Finance', false, 'Another test client', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Reset the auto-increment sequence for clients
ALTER TABLE clients ALTER COLUMN id RESTART WITH 3;

-- Insert test contacts (using actual column names from Contact entity)
INSERT INTO contacts (id, first_name, last_name, email, phone, notes, client_id, freelance_id, created_at, updated_at, version) VALUES
(1, 'Jane', 'Smith', 'jane.smith@testcompany.com', '+33 1 98 76 54 32', 'Primary contact for Test Company', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(2, 'Bob', 'Johnson', 'bob.johnson@anothercorp.com', '+33 1 11 22 33 44', 'Contact for Another Corp', 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Reset the auto-increment sequence for contacts
ALTER TABLE contacts ALTER COLUMN id RESTART WITH 3;

-- Insert test sources (using actual column names from Source entity)
INSERT INTO sources (id, name, type, link, is_listing, popularity_rating, usefulness_rating, notes, freelance_id, created_at, updated_at, version) VALUES
(1, 'LinkedIn', 'JOB_BOARD', 'https://linkedin.com', false, 5, 4, 'Professional networking platform', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(2, 'Indeed', 'JOB_BOARD', 'https://indeed.com', true, 4, 3, 'Job search platform', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Reset the auto-increment sequence for sources
ALTER TABLE sources ALTER COLUMN id RESTART WITH 3;

-- Insert test projects (using actual column names from Project entity)
INSERT INTO projects (id, role, description, tech_stack, daily_rate, work_mode, remote_days_per_month, onsite_days_per_month, advantages, start_date, duration_in_months, order_renewal_in_months, days_per_year, link, personal_rating, notes, client_id, freelance_id, source_id, created_at, updated_at, version) VALUES
(1, 'Senior Full Stack Developer', 'Development of a modern web application using Angular and Spring Boot', 'Angular, Spring Boot, PostgreSQL', 650, 'HYBRID', 15, 5, 'Modern tech stack, flexible working conditions', '2024-01-15', 12, 6, 220, 'https://project.example.com', 4, 'Excellent project with great team', 1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(2, 'Frontend Developer', 'React application development', 'React, TypeScript, Node.js', 550, 'REMOTE', 20, 0, 'Full remote work', '2024-02-01', 8, 4, 200, 'https://project2.example.com', 3, 'Good project but challenging deadlines', 2, 1, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Reset the auto-increment sequence for projects
ALTER TABLE projects ALTER COLUMN id RESTART WITH 3;

-- Insert test interview steps (using actual column names from InterviewStep entity)
INSERT INTO interview_steps (id, title, date, status, notes, project_id, created_at, updated_at, version) VALUES
(1, 'Phone Screening', '2024-01-10 14:30:00', 'VALIDATED', 'Candidate performed well', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(2, 'Technical Interview', '2024-01-12 10:00:00', 'VALIDATED', 'Strong technical skills demonstrated', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(3, 'Initial Contact', '2024-01-25 16:00:00', 'PLANNED', 'Waiting for response', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Reset the auto-increment sequence for interview_steps
ALTER TABLE interview_steps ALTER COLUMN id RESTART WITH 4;
