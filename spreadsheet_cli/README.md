# uskoag-sheetcli - Command-Line Google Sheets Tool

A command-line interface for reading and writing Google Sheets, built for testing the uskoag-gservices library and general-purpose spreadsheet automation.

## Features

- ✅ **Read single cells or ranges** from Google Sheets
- ✅ **Write single cells or ranges** to Google Sheets
- ✅ **XML-based permissions** system for access control
- ✅ **Encrypted OAuth tokens** using AES-256-GCM (from PRP-01)
- ✅ **Clean output** - results to stdout, logs to stderr
- ✅ **Portable fat JAR** - single executable with all dependencies

## Installation

The CLI is already installed if you have:
1. Built the project: `mvn clean package`
2. The batch file at: `d:\userSharabheshwara\Applications\cmdtools\uskoag-sheetcli.bat`

## Quick Start

### 1. Setup Configuration

Run the setup script to create the config file:
```batch
uskoag-gservices-spreadsheet_cli\setup-test-config.bat
```

Or manually create `%USERPROFILE%\uskoag\gservices\spreadsheet_cli\SpreadsheetCli.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<SpreadsheetCli>
    <permissions>
        <allowWrite name="Test Sheet">1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd</allowWrite>
    </permissions>
</SpreadsheetCli>
```

### 2. Authenticate

On first run, the CLI will open your browser for Google OAuth authentication. The credentials will be saved in:
```
%USERPROFILE%\uskoag\gservices\spreadsheet_cli\<your-email>\
```

### 3. Use the CLI

```batch
REM Read a single cell
uskoag-sheetcli read 1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd Sheet1 A1

REM Write to a single cell
uskoag-sheetcli write 1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd Sheet1 A1 "Hello World"

REM Read a range
uskoag-sheetcli read 1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd Sheet1 A1:B3

REM Write to a range
uskoag-sheetcli write 1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd Sheet1 A1:A3 "[Value1,Value2,Value3]"
```

## Command Reference

### Syntax
```
uskoag-sheetcli <command> <spreadsheetId> <sheetName> <address> [value]
```

### Commands

#### `read` - Read from spreadsheet
```batch
uskoag-sheetcli read <spreadsheetId> <sheetName> <address|range>
```

**Single cell output** (plain text):
```
Hello World
```

**Range output** (JSON-like format):
```
"A1": "Value1"
"B1": "Value2"
"A2": "Value3"
"B2": "Value4"
```

#### `write` - Write to spreadsheet
```batch
uskoag-sheetcli write <spreadsheetId> <sheetName> <address|range> "<value>"
```

**Single cell write**:
```batch
uskoag-sheetcli write 1Bv0Yt... Sheet1 A1 "My Value"
```

**Range write** (comma-separated):
```batch
uskoag-sheetcli write 1Bv0Yt... Sheet1 A1:B2 "[A1,B1,A2,B2]"
```

### Parameters

- **command**: `read` or `write`
- **spreadsheetId**: The ID from the Google Sheets URL
  - Example: `1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd`
- **sheetName**: Name of the sheet tab (e.g., `Sheet1`)
- **address**: Cell address (e.g., `A1`) or range (e.g., `A1:B10`)
- **value**: (write only) Value to write, quoted if contains spaces

## Configuration

### Location
```
%USERPROFILE%\uskoag\gservices\spreadsheet_cli\SpreadsheetCli.xml
```

### Format
```xml
<SpreadsheetCli>
    <permissions>
        <!-- Read-only permission -->
        <allowRead>spreadsheet_id_1</allowRead>

        <!-- Read-write permission with optional name -->
        <allowWrite name="My Work Sheet">spreadsheet_id_2</allowWrite>
    </permissions>
</SpreadsheetCli>
```

### Permission Types

- **`<allowRead>`**: Grants read-only access to a spreadsheet
- **`<allowWrite>`**: Grants read and write access to a spreadsheet
- **`name` attribute**: Optional human-readable label (for documentation)

### Auto-creation

If the config file doesn't exist, it will be automatically created with example comments on first run.

## Security

### OAuth Token Encryption
- Tokens are encrypted using **AES-256-GCM**
- Encryption key derived from app-specific key: `uskoag-spreadsheet-cli-key-2025`
- Tokens stored in: `~/uskoag/gservices/spreadsheet_cli/<email>/tokens_<hashed-key>/`
- Protects against infostealer malware (see PRP-01)

### Credentials Storage
- `credentials.json`: OAuth client credentials (plaintext, less sensitive)
- `StoredCredential`: User OAuth tokens (encrypted)

## Output Format

### stdout (Standard Output)
- **Read results only** - pipe-friendly
- **Write success messages** - for confirmation

### stderr (Standard Error)
- **All logs** - `[INFO]`, `[ERROR]`
- **Progress messages**
- **Authentication prompts**

This separation allows easy piping:
```batch
REM Pipe result to file
uskoag-sheetcli read 1Bv0Yt... Sheet1 A1 > result.txt

REM Suppress logs
uskoag-sheetcli read 1Bv0Yt... Sheet1 A1 2>nul
```

## Testing

### Test Spreadsheet
- **URL**: https://docs.google.com/spreadsheets/d/1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd/
- **Sheet**: Sheet1 (empty, ready for testing)
- **Purpose**: Testing gservices library and CLI tool

### Test Scenarios

1. **Basic read/write**
```batch
uskoag-sheetcli write 1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd Sheet1 A1 "Test"
uskoag-sheetcli read 1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd Sheet1 A1
```

2. **Range operations**
```batch
uskoag-sheetcli read 1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd Sheet1 A1:C3
```

3. **Permission errors**
```batch
REM Try accessing a spreadsheet not in config (should fail)
uskoag-sheetcli read UNAUTHORIZED_SHEET_ID Sheet1 A1
```

## Troubleshooting

### "No permission" error
- Check `SpreadsheetCli.xml` has the correct spreadsheet ID
- Ensure you're using `allowWrite` for write operations

### Browser doesn't open for OAuth
- Check firewall settings
- Default port is 8888
- Credentials must be in `~/uskoag/gservices/spreadsheet_cli/<email>/credentials.json`

### "credentials.json not found"
- Ensure you have a Google Cloud project with Sheets API enabled
- Download OAuth credentials from Google Cloud Console
- Place in: `%USERPROFILE%\uskoag\gservices\spreadsheet_cli\<your-email>\credentials.json`

### Build issues
```batch
REM Clean and rebuild
cd d:\userSharabheshwara\code\uskoag\uskoag-gservices
mvn clean package -pl uskoag-gservices-spreadsheet_cli -am
```

## Technical Details

### Dependencies (bundled in JAR)
- Google Sheets API v4
- Google OAuth Client + Jetty
- uskoag-gservices-oauth (PRP-01)
- uskoag-gservices-sheets (PRP-02)
- Apache HTTP Client
- Gson, Guava, etc.

### Build Output
- **JAR Location**: `target/uskoag-sheetcli.jar`
- **Size**: ~6.2 MB (fat JAR with all dependencies)
- **Main Class**: `uskoag.gservices.SpreadsheetCli`

### Maven Build
```bash
mvn clean package -pl uskoag-gservices-spreadsheet_cli -am
```

## Known Limitations

1. **Column support**: Single letter only (A-Z), no multi-letter (AA, AB)
2. **Range write parsing**: Basic comma-separated format, not full JSON
3. **Data types**: All values treated as strings (no number/date preservation)
4. **Formulas**: Written as plain text, not as formulas

## Future Enhancements (Not in Current Scope)

- [ ] Multi-letter column support (AA, AB, etc.)
- [ ] JSON parser for complex range writes
- [ ] Data type preservation
- [ ] Formula support
- [ ] Batch operations from CSV/JSON file
- [ ] Interactive REPL mode
- [ ] Cell formatting (colors, bold, etc.)

## Related PRPs

- **PRP-01**: Encrypted OAuth token storage (foundation)
- **PRP-02**: Individual sub-projects for Google services
- **PRP-03**: This CLI tool (for testing and automation)

## License

Part of the uskoag-gservices project.
