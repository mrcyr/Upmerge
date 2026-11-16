@echo off
setlocal
set "APP_HOME=%~dp0"
set "GRADLE_VERSION=8.7"
if "%GRADLE_USER_HOME%"=="" set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "DIST_DIR=%GRADLE_USER_HOME%\upmerge-bootstrap\gradle-%GRADLE_VERSION%"
set "GRADLE_BIN=%DIST_DIR%\bin\gradle.bat"
if exist "%GRADLE_BIN%" goto run
set "ZIP=%TEMP%\upmerge-gradle-%GRADLE_VERSION%.zip"
echo Gradle %GRADLE_VERSION% is not cached; downloading pinned distribution...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP%'"
if errorlevel 1 exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ZIP%' '%GRADLE_USER_HOME%\upmerge-bootstrap'"
if errorlevel 1 exit /b 1
del "%ZIP%"
:run
call "%GRADLE_BIN%" -p "%APP_HOME%" %*
exit /b %ERRORLEVEL%
