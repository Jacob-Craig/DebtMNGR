#!/usr/bin/env bash
set -e

COMMAND="${1:-help}"
FILE="${2:-backup.sql}"

case "$COMMAND" in
  dump)
    echo "Dumping database state to $FILE..."
    docker compose exec -T db pg_dump -U postgres -d debtmngr_db > "$FILE"
    echo "Done! Backup saved to $FILE."
    ;;
  restore)
    if [ ! -f "$FILE" ]; then
      echo "Error: Backup file $FILE not found!"
      exit 1
    fi
    echo "Resetting schema..."
    docker compose exec -T db psql -U postgres -d debtmngr_db -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
    echo "Restoring database from $FILE..."
    docker compose exec -T db psql -U postgres -d debtmngr_db < "$FILE"
    echo "Done! Database restored from $FILE."
    ;;
  clear|reset)
    echo "Clearing database schema..."
    docker compose exec -T db psql -U postgres -d debtmngr_db -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
    echo "Done! Database schema cleared."
    ;;
  *)
    echo "Usage: $0 {dump|restore|clear} [file]"
    echo ""
    echo "Commands:"
    echo "  dump [file]     Dump database to SQL file (default: backup.sql)"
    echo "  restore [file]  Restore database from SQL file (default: backup.sql)"
    echo "  clear           Clear database schema"
    exit 1
    ;;
esac
