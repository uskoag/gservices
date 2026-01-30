@echo off
REM This script sets up the configuration for the test spreadsheet
REM Test spreadsheet: https://docs.google.com/spreadsheets/d/1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd/

set CONFIG_DIR=%USERPROFILE%\uskoag\gservices\spreadsheet_cli
set CONFIG_FILE=%CONFIG_DIR%\SpreadsheetCli.xml

echo Creating config directory: %CONFIG_DIR%
if not exist "%CONFIG_DIR%" mkdir "%CONFIG_DIR%"

echo Creating SpreadsheetCli.xml with test spreadsheet permissions...

(
echo ^<?xml version="1.0" encoding="UTF-8"?^>
echo ^<SpreadsheetCli^>
echo     ^<permissions^>
echo         ^<allowWrite name="Test Sheet for Claude Code"^>1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd^</allowWrite^>
echo     ^</permissions^>
echo ^</SpreadsheetCli^>
) > "%CONFIG_FILE%"

echo.
echo Configuration file created successfully: %CONFIG_FILE%
echo.
echo You can now test the CLI with:
echo   uskoag-sheetcli read 1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd Sheet1 A1
echo   uskoag-sheetcli write 1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd Sheet1 A1 "Hello from CLI"
echo.
pause
