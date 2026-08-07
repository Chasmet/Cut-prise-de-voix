@echo off
setlocal
set GRADLE_VERSION=8.7
where gradle >nul 2>&1
if %ERRORLEVEL% EQU 0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
set CACHE_ROOT=%USERPROFILE%\.gradle\voicecut-bootstrap
set DIST_DIR=%CACHE_ROOT%\gradle-%GRADLE_VERSION%
set ZIP_FILE=%CACHE_ROOT%\gradle-%GRADLE_VERSION%-bin.zip
if not exist "%DIST_DIR%\bin\gradle.bat" (
  if not exist "%CACHE_ROOT%" mkdir "%CACHE_ROOT%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP_FILE%'; Expand-Archive -Force '%ZIP_FILE%' '%CACHE_ROOT%'"
)
call "%DIST_DIR%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
