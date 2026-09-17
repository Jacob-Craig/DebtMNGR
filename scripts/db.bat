@echo off
setlocal

set CMD=%1
set FILE=%2
if "%FILE%"=="" set FILE=backup.sql

if "%CMD%"=="dump" goto do_dump
if "%CMD%"=="restore" goto do_restore
if "%CMD%"=="clear" goto do_clear
if "%CMD%"=="reset" goto do_clear
goto show_help

:do_dump
echo Dumping database state to %FILE%...
docker compose exec -T db pg_dump -U postgres -d debtmngr_db > "%FILE%"
if %ERRORLEVEL% equ 0 (
    echo Done! Backup saved to %FILE%.
) else (
    echo Error dumping database.
)
goto end

:do_restore
if not exist "%FILE%" (
    echo Error: Backup file %FILE% not found!
    exit /b 1
)
echo Resetting schema...
docker compose exec -T db psql -U postgres -d debtmngr_db -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
echo Restoring database from %FILE%...
docker compose exec -T db psql -U postgres -d debtmngr_db < "%FILE%"
if %ERRORLEVEL% equ 0 (
    echo Done! Database restored from %FILE%.
) else (
    echo Error restoring database.
)
goto end

:do_clear
echo Clearing database schema...
docker compose exec -T db psql -U postgres -d debtmngr_db -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
if %ERRORLEVEL% equ 0 (
    echo Done! Database schema cleared.
) else (
    echo Error clearing database.
)
goto end

:show_help
echo Usage: scripts\db.bat {dump^|restore^|clear} [file]
echo.
echo Commands:
echo   dump [file]     Dump database to SQL file (default: backup.sql)
echo   restore [file]  Restore database from SQL file (default: backup.sql)
echo   clear           Clear database schema
goto end

:end
endlocal
