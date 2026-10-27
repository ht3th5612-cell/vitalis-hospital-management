@echo off
setlocal
cd /d "%~dp0"

echo Compiling the program and the tests...
if exist test-out rmdir /s /q test-out
mkdir test-out

javac --release 21 -encoding UTF-8 -cp "lib/*" -d test-out ^
    src\app\*.java src\controller\*.java src\dao\*.java src\database\*.java src\model\*.java src\util\*.java ^
    test\AllTests.java
if errorlevel 1 (
    echo Compile failed.
    exit /b 1
)

echo.
echo Running the tests on a throw-away database (hospital_db_test). MySQL must be running in XAMPP.
java -cp "test-out;lib/*" AllTests
set RESULT=%errorlevel%

rmdir /s /q test-out
exit /b %RESULT%
