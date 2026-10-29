@echo off
setlocal
echo =======================================================================
echo         BUGREPLAY — COMPLETE AUTOMATED TEST SUITE EXECUTION
echo =======================================================================

if not exist "build\classes" mkdir build\classes
if not exist "build\test-classes" mkdir build\test-classes
if not exist "traces" mkdir traces

set CLASSPATH=build\classes;lib\gson-2.10.1.jar;lib\mysql-connector-j-8.3.0.jar

echo [1/6] Compiling production codebase...
javac -cp %CLASSPATH% -d build\classes src\main\java\com\bugreplay\model\*.java src\main\java\com\bugreplay\runner\*.java src\main\java\com\bugreplay\recorder\*.java src\main\java\com\bugreplay\storage\*.java src\main\java\com\bugreplay\analysis\*.java src\main\java\com\bugreplay\ui\*.java src\main\java\com\bugreplay\*.java
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Production compilation failed.
    exit /b %ERRORLEVEL%
)

echo [2/6] Compiling test suites...
javac -cp %CLASSPATH% -d build\classes src\test\java\com\bugreplay\runner\*.java src\test\java\com\bugreplay\storage\*.java src\test\java\com\bugreplay\ui\*.java src\test\java\com\bugreplay\analysis\*.java
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Test suite compilation failed.
    exit /b %ERRORLEVEL%
)

echo.
echo =======================================================================
echo [3/6] Running Phase 2 Tests (Execution Runner and StreamGobbler)...
java -cp %CLASSPATH% com.bugreplay.runner.Phase2TestRunner
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

echo.
echo =======================================================================
echo [4/6] Running Phase 3 Tests (Trace Models, Checkpoints and JSON)...
java -cp %CLASSPATH% com.bugreplay.storage.Phase3TestRunner
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

echo.
echo =======================================================================
echo [5/6] Running Phase 4 Tests (Swing Desktop UI and SwingWorker)...
java -cp %CLASSPATH% com.bugreplay.ui.Phase4UiTest
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

echo.
echo =======================================================================
echo [6/6] Running Phase 5 Tests (MySQL, JDBC and Offline Resilience)...
java -cp %CLASSPATH% com.bugreplay.storage.Phase5DatabaseTest
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

if exist "config.properties" (
    echo Running Live MySQL Integration Tests...
    java -cp %CLASSPATH% com.bugreplay.storage.DatabaseLiveIntegrationTest
    if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%
)

echo.
echo =======================================================================
echo Running Phase 6 Tests (Trace Comparison, Diffing and Replay)...
java -cp %CLASSPATH% com.bugreplay.analysis.TraceComparatorTest
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

echo.
echo =======================================================================
echo [ALL TESTS PASSED] Complete BugReplay test suite verified successfully!
echo =======================================================================
