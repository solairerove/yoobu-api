Saas for SME

___

### Env

java 21 \
maven 3.9.11

```shell
➜  yoobu-api git:(master) ✗ mvn -v
Apache Maven 3.9.11 (3e54c93a704957b63ee3494413a2b544fd3d825b)
Maven home: /Users/solairerove/.sdkman/candidates/maven/current
Java version: 21.0.10, vendor: Oracle Corporation, runtime: /Users/solairerove/.sdkman/candidates/java/21.0.10-oracle
Default locale: en_US, platform encoding: UTF-8
OS name: "mac os x", version: "26.3.1", arch: "aarch64", family: "mac"
```

### How to run

```shell
mvn clean verify
mvn clean install && java -jar target/yoobu-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
mvn spring-boot:run
```

### Railway

Set `CORS_ALLOWED_ORIGIN_PATTERNS` to your frontend origins if you want to lock CORS down more tightly than the default Railway wildcard.

Example:

```shell
CORS_ALLOWED_ORIGIN_PATTERNS=https://yoobu-web-production.up.railway.app,https://your-custom-domain.com
```

`railway.toml` configures Railway to build from the repository `Dockerfile`, wait for `GET /health`, and restart on failures.

You still need to set runtime variables manually in Railway, at minimum:

```shell
DB_URL=...
DB_USER=...
DB_PASS=...
SUPERADMIN_USER=...
SUPERADMIN_PASS=...
```

### Super Admin panel

http://localhost:8080/superadmin/panel

### Admin panel

http://localhost:8080/admin/{slug}/panel
