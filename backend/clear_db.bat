@echo off
cd /d "C:\Users\Ujwal\Desktop\aiStudyPlanner\backend"
sqlplus -S aasa_user/aasa_password@localhost:1521/xepdb1 @clear_database.sql
pause
