@echo off
rem TonenAI launcher for Windows: double-click this file (run setup once first, see README).
cd /d "%~dp0"
if not exist ".venv\Scripts\pythonw.exe" (
  echo TonenAI is not set up yet. Open README.md, section "Quick start", and follow the Windows steps.
  pause
  exit /b 1
)
set PYTHONUTF8=1
start "" ".venv\Scripts\pythonw.exe" app\main.py
