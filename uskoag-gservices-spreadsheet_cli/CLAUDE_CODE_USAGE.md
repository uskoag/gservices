# Using uskoag-sheetcli from Claude Code

## TL;DR - Keep It Simple

```bash
# Using batch file (works from Git Bash/WSL/Claude Code)
uskoag-sheetcli read SHEET_ID SheetName A1 2>/dev/null
uskoag-sheetcli write SHEET_ID SheetName A1 "value" 2>/dev/null

# OR directly via JAR (if jr is in PATH and .jar in PATHEXT)
uskoag-sheetcli.jar read SHEET_ID SheetName A1 2>nul
```

**Key Rule**: Always add `2>/dev/null` (bash) or `2>nul` (Windows) to suppress logs and get clean stdout.

## Execution Methods

### Method 1: Via Batch File (Recommended for Claude Code)
```bash
uskoag-sheetcli.bat read SHEET_ID Sheet1 A1 2>/dev/null
```
- Works in Git Bash, WSL, Claude Code bash tool
- Calls `jr` to execute the JAR

### Method 2: Direct JAR Execution (Windows only)
```cmd
uskoag-sheetcli.jar read SHEET_ID Sheet1 A1 2>nul
```
- Requires `.jar` in PATHEXT
- Requires `jr` (Java runtime) registry association
- Works in Windows CMD/PowerShell
- Supports AOT compilation for better performance

## Why `2>/dev/null`?

The tool outputs:
- **stdout (1)**: Results only (the actual data)
- **stderr (2)**: Logs like `[INFO] Loading config...`

Claude Code's Bash tool shows both streams together by default. Adding `2>/dev/null` hides the logs so you only see results.

## Examples Claude Code Can Use Directly

### Example 1: Simple Read
```bash
# Get a cell value
value=$(uskoag-sheetcli read 1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd Sheet1 A1 2>/dev/null)
echo "Value is: $value"
```

**Output**:
```
Value is: Hello World
```

### Example 2: Simple Write
```bash
# Update a cell
uskoag-sheetcli write 1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd Sheet1 B1 "Updated" 2>/dev/null
```

**Output**:
```
SUCCESS: Written to Sheet1!B1
```

### Example 3: Read Range and Parse
```bash
# Get all values from column A
uskoag-sheetcli read 1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd Sheet1 A1:A10 2>/dev/null
```

**Output**:
```
"A1": "Row 1"
"A2": "Row 2"
"A3": "Row 3"
...
```

### Example 4: Extract Just Values from Range
```bash
# Get just the values (not the addresses)
uskoag-sheetcli read 1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd Sheet1 A1:A5 2>/dev/null | cut -d'"' -f4
```

**Output**:
```
Row 1
Row 2
Row 3
Row 4
Row 5
```

## What NOT to Do

### ❌ Don't Chain Multiple Commands with &&
```bash
# Too complex - hard to debug errors
uskoag-sheetcli write ... && uskoag-sheetcli write ... && uskoag-sheetcli read ...
```

**Instead**, run separately:
```bash
# Clear and debuggable
uskoag-sheetcli write SHEET_ID Sheet1 A1 "Value1" 2>/dev/null
uskoag-sheetcli write SHEET_ID Sheet1 A2 "Value2" 2>/dev/null
uskoag-sheetcli read SHEET_ID Sheet1 A1:A2 2>/dev/null
```

### ❌ Don't Use 2>&1 (merges streams)
```bash
# Wrong - merges logs with results
uskoag-sheetcli read SHEET_ID Sheet1 A1 2>&1
```

**Instead**, suppress stderr:
```bash
# Correct - only shows results
uskoag-sheetcli read SHEET_ID Sheet1 A1 2>/dev/null
```

### ❌ Don't Try Complex grep Filtering
```bash
# Too complicated
uskoag-sheetcli read ... 2>&1 | grep -v "^\[INFO\]" | grep -v "^\[ERROR\]"
```

**Instead**, just suppress stderr:
```bash
# Simple and works
uskoag-sheetcli read ... 2>/dev/null
```

## Configuration

Before using, ensure spreadsheet is in config file:

**Location**: `%USERPROFILE%\uskoag\gservices\spreadsheet_cli\SpreadsheetCli.xml`

```xml
<SpreadsheetCli>
    <permissions>
        <allowWrite name="Test Sheet">1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd</allowWrite>
    </permissions>
</SpreadsheetCli>
```

## Test Spreadsheet

**ID**: `1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd`
**Sheet**: `Sheet1`
**Permissions**: Full read/write access configured

Try it:
```bash
uskoag-sheetcli read 1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd Sheet1 A1 2>/dev/null
```

## Integration with Claude Code Skills

This tool is available as a skill. Invoke with:
```
@sheet
```

Then Claude Code will understand how to use the tool properly with clean commands.

## Summary for Claude Code

**Simple Pattern**:
```bash
uskoag-sheetcli <command> <sheet_id> <sheet_name> <address> ["value"] 2>/dev/null
```

**Where**:
- `<command>`: `read` or `write`
- `<sheet_id>`: Long ID from Google Sheets URL
- `<sheet_name>`: Tab name (e.g., `Sheet1`)
- `<address>`: Cell (e.g., `A1`) or Range (e.g., `A1:B10`)
- `["value"]`: (write only) Value to write, quoted if contains spaces
- `2>/dev/null`: Suppress logs, show only results

**That's it!** Keep commands simple and direct.
