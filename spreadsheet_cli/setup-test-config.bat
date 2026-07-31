@echo off
REM ---------------------------------------------------------------------------------------------
REM This script no longer does anything, and it is kept rather than deleted so that running it
REM says so instead of failing with "not recognised".
REM
REM It used to write SpreadsheetCli.xml, this tool's own allowlist of spreadsheet ids. That file is
REM no longer read by anything. Permissions moved to the USK OAG GServices Wallet, which decides for
REM every tool rather than one XML per tool, gives every permission an expiry, and keeps an audit.
REM
REM Writing the old file today would be worse than doing nothing: it would look like access had been
REM configured while changing nothing at all.
REM ---------------------------------------------------------------------------------------------

echo.
echo   setup-test-config is obsolete. SpreadsheetCli.xml is not read by anything any more.
echo.
echo   There is nothing to set up. Permissions form by themselves: the first time a tool touches a
echo   document the wallet asks once, naming the document, and remembers the answer until it expires.
echo.
echo   To get started:
echo     uskoag-walletcli status                  is a wallet running, is it unlocked
echo     uskoag-walletcli login ^<email^>           consent once for this account
echo     uskoag-sheetcli --email ^<email^> read ^<spreadsheetId^> Sheet1 A1
echo.
echo   To see or drop what stands right now:
echo     uskoag-walletcli policy list
echo     uskoag-walletcli policy revoke ^<ruleId^>
echo.
echo   --email is required whenever the wallet holds more than one account. There is no default:
echo   acting as the wrong account can expose the wrong organisation's files, and nothing in the
echo   output would say so.
echo.
pause
