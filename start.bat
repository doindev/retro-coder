@echo off
setlocal

echo ================================================================================
echo                         RETRO-CODER - Java Edition
echo ================================================================================
echo.

REM Check Java installation
java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java not found. Please install Java 21 or later.
    echo Download from: https://adoptium.net/
    pause
    exit /b 1
)

REM Check Maven installation
mvn -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Maven not found. Please install Maven 3.9 or later.
    echo Download from: https://maven.apache.org/download.cgi
    pause
    exit /b 1
)

REM Build if JAR doesn't exist
if not exist "target\autocoder-java.jar" (
    echo Building project...
    call mvn clean package -DskipTests -q
    if errorlevel 1 (
        echo ERROR: Build failed.
        pause
        exit /b 1
    )
    echo Build complete.
    echo.
)

REM Run CLI mode
java -jar target\autocoder-java.jar --cli

endlocal
