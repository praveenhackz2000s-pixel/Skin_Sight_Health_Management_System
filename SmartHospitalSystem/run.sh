#!/usr/bin/env bash
# Build and start the application (Linux / macOS / Git Bash).  Usage:  bash run.sh
set -e
cd "$(dirname "$0")"

VERSION=8.4.0
URL="https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/$VERSION/mysql-connector-j-$VERSION.jar"

# 1. MySQL Connector/J (a plain download - no Maven involved)
if ! ls lib/*.jar >/dev/null 2>&1; then
  echo "Downloading MySQL Connector/J $VERSION into lib/ ..."
  if ! curl -fL -o "lib/mysql-connector-j-$VERSION.jar" "$URL"; then
    rm -f "lib/mysql-connector-j-$VERSION.jar"
    echo "Download failed. Download the driver by hand - see lib/README.md" >&2
    exit 1
  fi
fi

# 2. Compile with javac (compiles every class that Main uses)
mkdir -p out
javac -encoding UTF-8 -d out -cp "lib/*" -sourcepath src/main/java src/main/java/com/hospital/Main.java

# 3. Run (src/main/resources is on the classpath so style.css is found)
exec java -cp "out:lib/*:src/main/resources" com.hospital.Main
