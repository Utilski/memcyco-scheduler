# Standalone service images

The root `compose.yaml` remains the development setup (bind mounts, Vite and
Gradle bootRun). These Dockerfiles provide separately buildable runnable images;
no source mounts, host Node installation or host JDK are required at runtime.
Docker with BuildKit and internet access is needed to build them.

| Service | Dockerfile / build context | Container port | Runtime |
| --- | --- | --- | --- |
| Backend | `backend/scheduler/Dockerfile` / `backend/scheduler` | 8080 | Java 27 JRE, non-root |
| Frontend | `frontend/Dockerfile` / `frontend` | 8080 | Static production build, unprivileged Nginx |
| MySQL | `docker/mysql/Dockerfile` / `docker/mysql` | 3306 | Official MySQL 8.4 |
| RabbitMQ | `docker/rabbitmq/Dockerfile` / `docker/rabbitmq` | 5672 | Official RabbitMQ 4 |

Run all commands below from the repository root. If needed, enter a shell with
`sg docker` or wrap commands in `sg docker -c '...'` to use existing group membership.
The examples use host ports 18080 and 15173 to coexist with development Compose;
do not run the E2E stack on those same ports simultaneously.

## Build

```sh
docker build -t memcyco-backend:local backend/scheduler
docker build --build-arg VITE_API_BASE_URL=http://localhost:18080 -t memcyco-frontend:local frontend
docker build -t memcyco-mysql:local docker/mysql
docker build -t memcyco-rabbitmq:local docker/rabbitmq
```

The backend runs `bootJar -x test`; the frontend runs `npm ci` and `npm run build`.
Neither image build executes unit/integration/E2E tests. Build contexts exclude
local caches, build output and environment files. The Dockerfiles do not set database/broker credentials; pass deployment values
at runtime. The backend retains its existing local defaults in application.properties.

`VITE_API_BASE_URL` is a **build-time public URL used by the browser**, not an
internal Docker hostname. Its default is `http://localhost:8080`. Rebuild the
frontend when changing it; setting it on `docker run` cannot modify built assets.
For remote deployments use the backend URL reachable from the user's browser.

## Start independently

Create a private service network and persistent dependency volumes:

```sh
docker network create memcyco-standalone
docker volume create memcyco-standalone-mysql
docker volume create memcyco-standalone-rabbitmq

docker run -d --name memcyco-standalone-mysql --network memcyco-standalone \
  --mount source=memcyco-standalone-mysql,target=/var/lib/mysql \
  -e MYSQL_DATABASE=mydatabase -e MYSQL_USER=myuser \
  -e MYSQL_PASSWORD=secret -e MYSQL_ROOT_PASSWORD=verysecret \
  memcyco-mysql:local

docker run -d --name memcyco-standalone-rabbitmq --network memcyco-standalone \
  --mount source=memcyco-standalone-rabbitmq,target=/var/lib/rabbitmq \
  -e RABBITMQ_DEFAULT_USER=myuser -e RABBITMQ_DEFAULT_PASS=secret \
  memcyco-rabbitmq:local
```

These are local example passwords; supply your own deployment credentials through
your deployment environment. MySQL/RabbitMQ are reachable on the private network;
publish their ports only if host clients need access. Initialization variables
apply to new data volumes, not existing database/broker users.

Wait until both dependencies report `healthy` with `docker ps`, then start the
backend and frontend:

```sh
docker run -d --name memcyco-standalone-backend --network memcyco-standalone \
  -p 18080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://memcyco-standalone-mysql:3306/mydatabase \
  -e SPRING_DATASOURCE_USERNAME=myuser -e SPRING_DATASOURCE_PASSWORD=secret \
  -e SPRING_RABBITMQ_HOST=memcyco-standalone-rabbitmq \
  -e SPRING_RABBITMQ_USERNAME=myuser -e SPRING_RABBITMQ_PASSWORD=secret \
  -e CORS_ALLOWED_ORIGINS=http://localhost:15173,http://127.0.0.1:15173 \
  memcyco-backend:local

docker run -d --name memcyco-standalone-frontend --network memcyco-standalone \
  -p 15173:8080 memcyco-frontend:local
```

Open http://localhost:15173. The backend applies Flyway migrations automatically.
Scheduler and worker roles default to enabled; override `SCHEDULER_ENABLED` and
`WORKER_ENABLED` independently when splitting backend roles across containers.
Every image has a health check. Nginx supports SPA route fallback and immutable
hashed assets; index.html is not cached. Java receives stop signals directly and
uses the existing graceful Quartz shutdown configuration.

Each service may be launched against existing reachable dependencies instead of
these example containers. Network aliases/internal container names belong only
in server connection settings; frontend API URLs must resolve in the browser.

## Stop

```sh
docker stop memcyco-standalone-frontend memcyco-standalone-backend memcyco-standalone-rabbitmq memcyco-standalone-mysql
docker rm memcyco-standalone-frontend memcyco-standalone-backend memcyco-standalone-rabbitmq memcyco-standalone-mysql
docker network rm memcyco-standalone
```

Data volumes remain for the next launch. Remove only these explicitly named
standalone volumes if you intentionally want to discard their data. Development
Compose and E2E volumes are separate.

## Verification

Verified on 2026-11-16: all four Dockerfiles built successfully and all four
containers became healthy on a disposable standalone network. Targeted checks
passed for backend JAR startup/Flyway, non-root app users, CORS, basic API CRUD,
frontend static assets, a custom API build argument, SPA fallback and cache
headers. Smoke containers, volumes and network were removed afterward.
No full unit, integration or E2E test suite was run for this Dockerfile change.
Build log: `/tmp/memcyco-docker-builds.log`; smoke log:
`/tmp/memcyco-image-smoke.log`.
