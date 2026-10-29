@echo off
setlocal
echo ===================================================
echo Compiling and Running BugReplay Phase 2 Test Suite
echo ===================================================

if not exist "build\classes" mkdir build\classes
if not exist "build\test-classes" mkdir build\test-classes

echo [1/2] Compiling production runner classes...
javac -d build\classes src\main\java\com\bugreplay\runner\*.java
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compilation of runner classes failed.
    exit /b %ERRORLEVEL%
)

echo [2/2] Compiling test runner...
javac -cp build\classes -d build\classes src\test\java\com\bugreplay\runner\*.java
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compilation of test runner failed.
    exit /b %ERRORLEVEL%
)

echo.
echo Running Phase 2 Tests...
java -cp build\classes com.bugreplay.runner.Phase2TestRunner
if %ERRORLEVEL% neq 0 (
    echo [TEST FAILED] One or more tests failed.
    exit /b %ERRORLEVEL%
)

echo.
echo [SUCCESS] Phase 2 Execution Runner verified!
