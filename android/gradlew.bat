@echo off
setlocal
set "APP_HOME=%~dp0"
set "WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"

if exist "%WRAPPER_JAR%" (
    java -Xmx2048m -jar "%WRAPPER_JAR%" %*
    exit /b %ERRORLEVEL%
)

where gradle >nul 2>&1
if %ERRORLEVEL%==0 (
    gradle --project-dir "%APP_HOME%" %*
    exit /b %ERRORLEVEL%
)

echo Error: Neither gradle\wrapper\gradle-wrapper.jar nor a system gradle installation was found.
echo Open the android\ folder in Android Studio, or run: gradle wrapper --gradle-version 8.11.1
exit /b 1
