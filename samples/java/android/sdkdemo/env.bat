:: Assigns the env var to the value
setx SPEECH__SUBSCRIPTION__KEY "5cbb2b6a9e97434daa971cbd769e5175"
setx SPEECH__SERVICE__REGION "koreacentral"

@echo off
:: Prints the env var value
echo SPEECH__SUBSCRIPTION__KEY: %SPEECH__SUBSCRIPTION__KEY%
echo SPEECH__SUBSCRIPTION__KEY: %SPEECH__SERVICE__REGION%

echo.
pause > nul | echo Press any key to exit...
exit