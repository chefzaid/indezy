-- Indezy Database Initialization Script
-- This script runs when PostgreSQL container starts up
-- It sets up essential database extensions and configurations

-- Create extensions required by the application
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- Trigram index of the offers' search text (built at startup by OfferSearchIndex)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- As the cluster's database setup hook does for the application's role: no JIT compilation, SSD page costs
ALTER ROLE CURRENT_USER SET jit = off;
ALTER ROLE CURRENT_USER SET random_page_cost = 1.1;

-- Set database metadata
COMMENT ON DATABASE indezy IS 'Indezy - Job tracking application for freelancers';

-- Note: Table creation and sample data are handled by:
-- - JPA/Hibernate for table structure (ddl-auto: update)
-- - the explicit `mask db-reset` command for development sample data
--
-- This script focuses on database-level setup that must happen
-- before the application starts.
