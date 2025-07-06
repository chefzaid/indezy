#!/bin/bash

# Database utility script for Indezy development container

set -e

DB_HOST="postgres"
DB_NAME="indezy"
DB_USER="indezy_user"
DB_PASSWORD="indezy_password"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_header() {
    echo -e "${BLUE}=== $1 ===${NC}"
}

# Function to check if database is ready
check_db_ready() {
    print_status "Checking database connection..."
    if pg_isready -h $DB_HOST -p 5432 -U $DB_USER -d $DB_NAME; then
        print_status "Database is ready!"
        return 0
    else
        print_error "Database is not ready!"
        return 1
    fi
}

# Function to connect to database
connect_db() {
    print_status "Connecting to database..."
    PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -U $DB_USER -d $DB_NAME
}

# Function to show database info
show_db_info() {
    print_header "Database Information"
    PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -U $DB_USER -d $DB_NAME -c "
        SELECT 
            current_database() as database,
            current_user as user,
            version() as version;
    "
}

# Function to list tables
list_tables() {
    print_header "Database Tables"
    PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -U $DB_USER -d $DB_NAME -c "
        SELECT 
            schemaname,
            tablename,
            tableowner
        FROM pg_tables 
        WHERE schemaname = 'public'
        ORDER BY tablename;
    "
}

# Function to show table sizes
show_table_sizes() {
    print_header "Table Sizes"
    PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -U $DB_USER -d $DB_NAME -c "
        SELECT 
            schemaname,
            tablename,
            pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size
        FROM pg_tables 
        WHERE schemaname = 'public'
        ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
    "
}

# Function to backup database
backup_db() {
    local backup_file="indezy_backup_$(date +%Y%m%d_%H%M%S).sql"
    print_status "Creating backup: $backup_file"
    PGPASSWORD=$DB_PASSWORD pg_dump -h $DB_HOST -U $DB_USER -d $DB_NAME > "/workspace/$backup_file"
    print_status "Backup created: /workspace/$backup_file"
}

# Function to restore database
restore_db() {
    local backup_file=$1
    if [ -z "$backup_file" ]; then
        print_error "Please provide backup file path"
        return 1
    fi
    
    if [ ! -f "$backup_file" ]; then
        print_error "Backup file not found: $backup_file"
        return 1
    fi
    
    print_warning "This will restore the database from: $backup_file"
    read -p "Are you sure? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        print_status "Restoring database..."
        PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -U $DB_USER -d $DB_NAME < "$backup_file"
        print_status "Database restored successfully!"
    else
        print_status "Restore cancelled"
    fi
}

# Function to reset database
reset_db() {
    print_warning "This will drop all tables and recreate the database schema!"
    read -p "Are you sure? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        print_status "Resetting database..."
        PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -U $DB_USER -d $DB_NAME -c "
            DROP SCHEMA public CASCADE;
            CREATE SCHEMA public;
            GRANT ALL ON SCHEMA public TO $DB_USER;
            GRANT ALL ON SCHEMA public TO public;
            CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";
        "
        print_status "Database reset complete! Restart the backend to recreate tables."
    else
        print_status "Reset cancelled"
    fi
}

# Main script logic
case "$1" in
    "check")
        check_db_ready
        ;;
    "connect")
        connect_db
        ;;
    "info")
        show_db_info
        ;;
    "tables")
        list_tables
        ;;
    "sizes")
        show_table_sizes
        ;;
    "backup")
        backup_db
        ;;
    "restore")
        restore_db "$2"
        ;;
    "reset")
        reset_db
        ;;
    *)
        print_header "Indezy Database Utilities"
        echo "Usage: $0 {check|connect|info|tables|sizes|backup|restore|reset}"
        echo ""
        echo "Commands:"
        echo "  check    - Check if database is ready"
        echo "  connect  - Connect to database (psql)"
        echo "  info     - Show database information"
        echo "  tables   - List all tables"
        echo "  sizes    - Show table sizes"
        echo "  backup   - Create database backup"
        echo "  restore  - Restore from backup file"
        echo "  reset    - Reset database (WARNING: destructive)"
        echo ""
        echo "Examples:"
        echo "  $0 check"
        echo "  $0 backup"
        echo "  $0 restore /workspace/backup.sql"
        ;;
esac
