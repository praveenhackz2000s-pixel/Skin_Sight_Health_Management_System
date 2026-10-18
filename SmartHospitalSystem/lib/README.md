# lib/ - the MySQL JDBC driver goes here

The application talks to MySQL through **MySQL Connector/J**, a single `.jar` file.
This folder is intentionally empty in the repository: the real JAR is a binary that belongs to Oracle/MySQL,
so you download it yourself. **No Maven or Gradle is needed - it is just a file download.**

## Easiest way (automatic)

Run `run.bat` (Windows) or `bash run.sh` (Linux/macOS). If `lib/` has no `.jar`, the script downloads
`mysql-connector-j-8.4.0.jar` for you with `curl`.

## Manual download (if the automatic download is blocked, e.g. by a college network)

1. Open <https://dev.mysql.com/downloads/connector/j/>
2. In the "Select Operating System" box choose **Platform Independent**.
3. Download the **ZIP Archive** (you can skip the login: click "No thanks, just start my download").
4. Extract the ZIP. Inside is a file like `mysql-connector-j-8.4.0.jar`.
5. Copy **only that `.jar` file** into this `lib/` folder.

Direct link to the same file (Maven Central is only used as a download server):

```
https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar
```

PowerShell:

```powershell
curl.exe -L -o lib\mysql-connector-j-8.4.0.jar https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar
```

Linux / macOS:

```bash
curl -L -o lib/mysql-connector-j-8.4.0.jar https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar
```

## Check it worked

```
dir lib          (Windows)
ls lib           (Linux/macOS)
```

You should see one `mysql-connector-j-<version>.jar` of roughly 2.5 MB. Any 8.x or 9.x version works.
Keep only **one** driver JAR in this folder.

## Common problems

| Message | Meaning |
|---|---|
| `No suitable driver` / `MySQL Connector/J JAR not found` | there is no `.jar` in `lib/`, or the classpath does not include `lib/*` |
| `ClassNotFoundException: com.mysql.cj.jdbc.Driver` | same as above |
| JAR is only a few KB | the download failed and saved an error page - delete it and download again |

Licence note: Connector/J is licensed under GPLv2 with the Universal FOSS Exception. For a college project that
links to it and downloads it separately this is fine.
