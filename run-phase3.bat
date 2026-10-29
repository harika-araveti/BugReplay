@echo off
setlocal
echo ===================================================
echo Compiling and Running BugReplay Phase 3 Test Suite
echo ===================================================

if not exist "build\classes" mkdir build\classes
if not exist "build\test-classes" mkdir build\test-classes
if not exist "traces" mkdir traces

set CLASSPATH=build\classes;lib\gson-2.10.1.jar

echo [1/3] Compiling production model, runner, recorder, and storage...
javac -cp %CLASSPATH% -d build\classes src\main\java\com\bugreplay\model\*.java src\main\java\com\bugreplay\runner\*.java src\main\java\com\bugreplay\recorder\*.java src\main\java\com\bugreplay\storage\*.java
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compilation of production classes failed.
    exit /b %ERRORLEVEL%
)

echo [2/3] Compiling test suite...
javac -cp %CLASSPATH% -d build\classes src\test\java\com\bugreplay\storage\*.java
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compilation of test suite failed.
    exit /b %ERRORLEVEL%
)

echo [3/3] Running Phase 3 Tests...
java -cp %CLASSPATH% com.bugreplay.storage.Phase3TestRunner
if %ERRORLEVEL% neq 0 (
    echo [TEST FAILED] One or more tests failed.
    exit /b %ERRORLEVEL%
)

echo.
echo [SUCCESS] Phase 3 Trace Recording and JSON Storage verified!
