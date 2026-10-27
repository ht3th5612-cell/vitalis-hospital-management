@echo off
setlocal
cd /d "%~dp0"

echo Building VITALIS...
if not exist bin mkdir bin
if not exist bin\view mkdir bin\view

javac --release 21 -encoding UTF-8 -cp "lib/*" -d bin ^
    src\app\*.java ^
    src\controller\*.java ^
    src\dao\*.java ^
    src\database\*.java ^
    src\model\*.java ^
    src\util\*.java
if errorlevel 1 (
    echo.
    echo Build failed. Check that a JDK 21 or newer is installed and on PATH.
    exit /b 1
)


copy /y "src\view\*.fxml" "bin\view\" >nul
copy /y "src\view\style.css" "bin\view\" >nul
copy /y "src\db.properties" "bin\" >nul
xcopy /e /i /y "src\images" "bin\images" >nul
if exist "src\mail.properties" copy /y "src\mail.properties" "bin\" >nul

echo Build complete.
endlocal
