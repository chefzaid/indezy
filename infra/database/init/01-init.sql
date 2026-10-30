-- Runs once when the local PostgreSQL container creates its database. Hibernate creates the tables, and
-- `mask db-reset` loads the sample data.

-- Trigram index of the offers' search text (built at startup by OfferSearchIndex)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- As the cluster's database setup hook does for the application's role: no JIT compilation, SSD page costs
ALTER ROLE CURRENT_USER SET jit = off;
ALTER ROLE CURRENT_USER SET random_page_cost = 1.1;
