-- Test data for integration tests

-- Insert test freelance
INSERT INTO freelances (id, first_name, last_name, email, phone, status, created_at, updated_at, version)
VALUES (1, 'John', 'Doe', 'john.doe@example.com', '123-456-7890', 'FREELANCE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Insert test client
INSERT INTO clients (id, company_name, address, city, is_final, freelance_id, created_at, updated_at, version)
VALUES (1, 'Test Company', '123 Test St', 'Test City', true, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Insert test source
INSERT INTO sources (id, name, type, freelance_id, is_listing, created_at, updated_at, version)
VALUES (1, 'Test Source', 'JOB_BOARD', 1, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
