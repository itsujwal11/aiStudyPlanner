@echo off
cd /d "C:\Users\Ujwal\Desktop\aiStudyPlanner\backend"
psql -U aasa_user -d aasa -f clear_database.sql
pause
