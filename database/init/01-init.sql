-- Indezy Database Initialization Script
-- This script runs when PostgreSQL container starts up
-- It sets up essential database extensions and configurations

-- Create extensions required by the application
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Set database metadata
COMMENT ON DATABASE indezy IS 'Indezy - Job tracking application for freelancers';

-- Note: Table creation and sample data are handled by:
-- - JPA/Hibernate for table structure (ddl-auto: update)
-- - data-dev.sql for development sample data (loaded by Spring Boot)
--
-- This script focuses on database-level setup that must happen
-- before the application starts.
