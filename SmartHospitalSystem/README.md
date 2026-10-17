# Smart Eye & Skin Specialist Hospital Management System

A small console-based hospital management project using **Java OOP, JDBC, SQL and MySQL**. This version does not use Maven or Gradle.

## Requirements

- JDK 17 or compatible JDK
- VS Code with Microsoft's Extension Pack for Java
- MySQL Server and MySQL Workbench
- MySQL Connector/J JAR (added manually to `lib/`)

## Project structure

- `src/main/java/com/hospital/model/` - data/model classes
- `src/main/java/com/hospital/dao/` - SQL/JDBC data access classes
- `src/main/java/com/hospital/database/` - shared JDBC connection
- `src/main/java/com/hospital/ui/` - console modules
- `src/main/java/com/hospital/Main.java` - application entry point
- `src/main/java/` is configured as the Java source root in `.vscode/settings.json`
- `lib/` - MySQL Connector/J JAR
- `schema.sql` - MySQL database and table creation script

## Setup steps

1. Open this project folder in VS Code.
2. Download the Platform Independent ZIP archive from the official MySQL Connector/J page: https://dev.mysql.com/downloads/connector/j/
3. Extract the archive and copy `mysql-connector-j-<version>.jar` into the project's `lib/` folder.
4. Open `schema.sql` in MySQL Workbench, connect to your local MySQL Server, and execute the script.
5. Copy `db.properties.example` to a new file named `db.properties` in the project root.
6. Edit `db.properties` with your local MySQL username and password. Do not commit `db.properties` to GitHub.
7. Reopen or refresh the VS Code Java project if the driver is not detected. `.vscode/settings.json` references all JAR files under `lib/`.
8. Open `src/main/java/com/hospital/Main.java` and use **Run** above `main`, or use the VS Code Run and Debug panel.
9. Choose menu option `4` to test the database connection.

## Manual terminal commands (Windows PowerShell)

Run these commands from the project root after adding the Connector/J JAR and creating `db.properties`:

```powershell
New-Item -ItemType Directory -Force bin
javac -cp "lib/*" -d bin (Get-ChildItem -Path src/main/java -Recurse -Filter *.java | ForEach-Object { $_.FullName })
java -cp "bin;lib/*" com.hospital.Main
```

For macOS/Linux, use `:` rather than `;` to separate classpath entries in the `java` command.

## Team roles

- Member 1: patient registration/search
- Member 2: visit symptoms and historical visits
- Member 3: doctors and appointments
- Member 4: database schema, shared JDBC connection, integration and testing

## Safety and data handling

Use fictional demo records while developing. Do not commit database passwords or real patient details. Similar historical cases and specialist suggestions are only decision-support features; diagnosis and treatment decisions belong to qualified clinicians.
