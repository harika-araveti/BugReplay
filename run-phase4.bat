@echo off
setlocal
echo ===================================================
echo Compiling and Running BugReplay Phase 4 UI Suite
echo ===================================================

if not exist "build\classes" mkdir build\classes
if not exist "build\test-classes" mkdir build\test-classes
if not exist "traces" mkdir traces

set CLASSPATH=build\classes;lib\gson-2.10.1.jar;lib\mysql-connector-j-8.3.0.jar

echo [1/3] Compiling all production classes (models, runner, recorder, storage, UI, Main)...
javac -cp %CLASSPATH% -d build\classes src\main\java\com\bugreplay\model\*.java src\main\java\com\bugreplay\runner\*.java src\main\java\com\bugreplay\recorder\*.java src\main\java\com\bugreplay\storage\*.java src\main\java\com\bugreplay\ui\*.java src\main\java\com\bugreplay\*.java
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compilation of production classes failed.
    exit /b %ERRORLEVEL%
)

echo [2/3] Compiling UI test suite...
javac -cp %CLASSPATH% -d build\classes src\test\java\com\bugreplay\ui\*.java
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compilation of UI test suite failed.
    exit /b %ERRORLEVEL%
)

echo [3/3] Running Phase 4 UI Tests...
java -cp %CLASSPATH% com.bugreplay.ui.Phase4UiTest
if %ERRORLEVEL% neq 0 (
    echo [TEST FAILED] One or more tests failed.
    exit /b %ERRORLEVEL%
)

echo.
echo [SUCCESS] Phase 4 Swing Desktop UI verified!
