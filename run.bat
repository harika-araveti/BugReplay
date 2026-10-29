@echo off
setlocal
cd /d "%~dp0"
echo ===================================================
echo Launching BugReplay Desktop Application...
echo ===================================================

if not exist "build\classes" mkdir build\classes
if not exist "traces" mkdir traces

set CLASSPATH=build\classes;lib\gson-2.10.1.jar;lib\mysql-connector-j-8.3.0.jar

echo Compiling any modified sources...
javac -cp %CLASSPATH% -d build\classes src\main\java\com\bugreplay\model\*.java src\main\java\com\bugreplay\runner\*.java src\main\java\com\bugreplay\recorder\*.java src\main\java\com\bugreplay\storage\*.java src\main\java\com\bugreplay\analysis\*.java src\main\java\com\bugreplay\ui\*.java src\main\java\com\bugreplay\*.java
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compilation failed.
    pause
    exit /b %ERRORLEVEL%
)

echo Starting BugReplay GUI...
start "BugReplay" java -cp "%CLASSPATH%" com.bugreplay.Main
