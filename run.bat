@echo off
rem Build and start the application on Windows (double-click, or run  .\run.bat  in PowerShell / cmd).
setlocal
cd /d "%~dp0"

set VERSION=8.4.0

rem 1. MySQL Connector/J (a plain download - no Maven involved)
if not exist lib\*.jar (
  echo Downloading MySQL Connector/J %VERSION% into lib\ ...
  curl -fL -o "lib\mysql-connector-j-%VERSION%.jar" "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/%VERSION%/mysql-connector-j-%VERSION%.jar"
  if errorlevel 1 (
    del "lib\mysql-connector-j-%VERSION%.jar" 2>nul
    echo.
    echo Download failed. Download the driver by hand - see lib\README.md
    pause
    exit /b 1
  )
)

rem 2. Compile with javac (compiles every class that Main uses)
if not exist out mkdir out
javac -encoding UTF-8 -d out -cp "lib/*" -sourcepath src/main/java src/main/java/com/hospital/Main.java
if errorlevel 1 (
  echo.
  echo Compilation failed. Is JDK 17 installed? Check:  javac -version
  pause
  exit /b 1
)

rem 3. Run (src/main/resources is on the classpath so style.css is found)
java -cp "out;lib/*;src/main/resources" com.hospital.Main
pause
