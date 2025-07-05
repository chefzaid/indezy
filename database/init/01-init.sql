-- Indezy Database Initialization Script
-- This script creates the initial database structure and sample data

-- Create database if it doesn't exist (handled by Docker)
-- CREATE DATABASE IF NOT EXISTS indezy;

-- Use the indezy database
-- \c indezy;

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create indexes for better performance
-- These will be created automatically by JPA, but we can add custom ones here

-- Index on freelance email for faster authentication
-- CREATE INDEX IF NOT EXISTS idx_freelance_email ON freelances(email);

-- Index on project daily_rate for filtering
-- CREATE INDEX IF NOT EXISTS idx_project_daily_rate ON projects(daily_rate);

-- Index on project start_date for sorting
-- CREATE INDEX IF NOT EXISTS idx_project_start_date ON projects(start_date);

-- Index on client company_name for searching
-- CREATE INDEX IF NOT EXISTS idx_client_company_name ON clients(company_name);

-- Sample data will be inserted here in future versions
-- For now, the application will start with empty tables

-- Create a default admin user (optional)
-- This would be handled by the application's user registration system

COMMENT ON DATABASE indezy IS 'Indezy - Job tracking application for freelancers';
