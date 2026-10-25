@echo off
rem check-env.bat - Windows launcher for check-env.sh (build environment self-check)
rem This file must stay pure ASCII: cmd parses it with the ANSI codepage before chcp
rem takes effect, and non-ASCII bytes corrupt line parsing (eat newlines/characters).
rem All Chinese output lives in check-env.sh; chcp 65001 makes it render correctly here.
chcp 65001 >nul

setlocal
set "BASH_EXE="
rem Locate Git Bash by its well-known install paths; do NOT use bare "bash",
rem which may resolve to WSL's bash and run the script against the wrong world.
if exist "%ProgramFiles%\Git\bin\bash.exe" set "BASH_EXE=%ProgramFiles%\Git\bin\bash.exe"
if not defined BASH_EXE if exist "%ProgramFiles(x86)%\Git\bin\bash.exe" set "BASH_EXE=%ProgramFiles(x86)%\Git\bin\bash.exe"
if not defined BASH_EXE if exist "%LOCALAPPDATA%\Programs\Git\bin\bash.exe" set "BASH_EXE=%LOCALAPPDATA%\Programs\Git\bin\bash.exe"

if not defined BASH_EXE (
	echo.
	echo [MISSING] Git Bash not found - Git for Windows is required by this check and by the build.
	echo           Install it from: https://git-scm.com/download/win
	echo           Then run this script again.
	echo.
	pause
	endlocal & exit /b 1
)

"%BASH_EXE%" "%~dp0check-env.sh"
echo.
pause
endlocal & exit /b %ERRORLEVEL%
