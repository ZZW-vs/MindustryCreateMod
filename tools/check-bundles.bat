@echo off
rem Localization key check. Standalone tool, not wired into Gradle packaging.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0check-bundles.ps1" %*
exit /b %ERRORLEVEL%
