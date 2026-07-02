Saas for SME

Telegram [food ordering miniapp](@yoobu_demo_bot)

Telegram [ecommerce miniapp](@stg_dennis_tennis_bot)
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

### Local development (infra in Docker, app on host)

Create the shared Docker network once (if it doesn't exist yet):

```shell
docker network create yoobu-net
```

Start only the database:

```shell
docker compose up -d
```

Then run the app locally — all env vars have sensible defaults for this setup:

```shell
mvn spring-boot:run
# or
mvn clean install && java -jar target/yoobu-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

### Full stack in Docker

Run everything (PostgreSQL + Java app) in Docker — useful when working on a 3rd-party service that needs this API up:

```shell
docker-compose --profile full up -d
```

The first run builds the image from the `Dockerfile` (includes Maven build, takes a few minutes). Subsequent starts are instant.

Tear everything down:

```shell
docker-compose --profile full down
```

### Tests

```shell
# All tests (unit + integration via Testcontainers)
mvn clean verify

# Unit tests only
mvn test
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
