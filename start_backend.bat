@echo off
title Samgyupsal System - Realtime Backend Server
echo ===================================================
echo Starting Samgyupsal Central Backend (MS SQL CE API)
echo Listening on port 5000 (accessible to all emulators)
echo ===================================================
cd /d "%~dp0backend_somee"
dotnet run --urls "http://0.0.0.0:5000"
pause
