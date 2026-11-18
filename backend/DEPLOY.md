# Deploy Wanderly API: Docker → Docker Hub → Render

```
Flutter app ──HTTPS──► Render (Docker container: Spring Boot + JWT) ──IPv4 pooler──► Supabase (Tokyo)
```

The image contains Java 21 and the app jar. The jar already bundles everything: Spring Boot, Spring Security, the JWT libraries (jjwt) and the PostgreSQL driver. Secrets are **not** baked into the image; they're passed in at runtime.

---

## 1. Install Docker Desktop (one time, needs admin + restart)

Open **PowerShell as Administrator** and run:
```bash
wsl --install --no-distribution
```
**Restart Windows**, then install Docker Desktop:
```bash
winget install -e --id Docker.DockerDesktop
```
Start **Docker Desktop** from the Start menu, accept its terms, and wait until it says *Engine running*. Check:
```bash
docker version
```

## 2. Build the image

From the `backend` folder:
```bash
docker build -t wanderly-api:1.0.0 .
```
The first build downloads Maven, the JDK and dependencies, so it takes a few minutes. Later builds are fast because of caching.

## 3. Run the container locally and verify

`backend/.env` already has the Supabase **pooler** settings (IPv4), which work inside Docker:
```bash
docker run -d --name wanderly-api -p 8080:8080 --env-file .env wanderly-api:1.0.0
```
Watch it start (about 30 s):
```bash
docker logs -f wanderly-api
```
When you see `Started WanderlyApplication`, open:
- http://localhost:8080/actuator/health → `{"status":"UP"}`
- http://localhost:8080/api/destinations → 12 destinations from Supabase
- http://localhost:8080/swagger-ui.html → try `POST /api/auth/login` with `demo@wanderly.app` / `wanderly123`

Container health (shows `healthy` after about 90 s):
```bash
docker ps
```
Stop and remove the container:
```bash
docker rm -f wanderly-api
```

## 4. Push the image to Docker Hub

Render pulls the image from a registry. You need a free [Docker Hub](https://hub.docker.com) account; replace `YOUR_DOCKERHUB_USER` below.
```bash
docker login
```
```bash
docker tag wanderly-api:1.0.0 YOUR_DOCKERHUB_USER/wanderly-api:1.0.0
```
```bash
docker tag wanderly-api:1.0.0 YOUR_DOCKERHUB_USER/wanderly-api:latest
```
```bash
docker push YOUR_DOCKERHUB_USER/wanderly-api:1.0.0
```
```bash
docker push YOUR_DOCKERHUB_USER/wanderly-api:latest
```
The repository can stay **public**: the image contains no secrets. If you make it private, add a Docker Hub access token as a *Registry Credential* in Render.

## 5. Create the service on Render

[dashboard.render.com](https://dashboard.render.com) → **New → Web Service → Existing image**

| Setting | Value |
|---|---|
| Image URL | `docker.io/YOUR_DOCKERHUB_USER/wanderly-api:latest` |
| Name | `wanderly-api` (gives `https://wanderly-api-latest.onrender.com` if the name is free) |
| Region | **Singapore** (closest to Bangladesh and to the Tokyo database) |
| Instance type | Free works for testing (it sleeps after 15 min idle; the first request then takes about 1 min) |
| Health Check Path | `/actuator/health/readiness` |

**Environment variables** (Render → *Environment*). Type them yourself; don't paste them into chat:

| Key | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `supabase` |
| `SUPABASE_DB_HOST` | `aws-1-ap-northeast-1.pooler.supabase.com` |
| `SUPABASE_DB_PORT` | `5432` |
| `SUPABASE_DB_NAME` | `postgres` |
| `SUPABASE_DB_USER` | `postgres.fhlrxfazrpfxyhgamjbu` |
| `SUPABASE_DB_PASSWORD` | *your Supabase DB password* |
| `JWT_SECRET` | a **new** random key; generate one in PowerShell: `$b=New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)` |
| `SEED_DATA` | `true` (it only seeds when the tables are empty) |
| `CORS_ORIGINS` | `*` (fine for a mobile app; restrict it if you add a website) |

Click **Deploy**. Render sets `PORT` itself and the app uses it. Verify:
```
https://wanderly-api-latest.onrender.com/actuator/health
https://wanderly-api-latest.onrender.com/api/destinations
```

> Changing `JWT_SECRET` signs everyone out (old tokens become invalid). That's expected the first time.

## 6. Ship a new version

Build, tag, push, then redeploy:
```bash
docker build -t YOUR_DOCKERHUB_USER/wanderly-api:latest .
```
```bash
docker push YOUR_DOCKERHUB_USER/wanderly-api:latest
```
Then in Render choose **Manual Deploy → Deploy latest reference**. To pin a version instead, change the service's **Image URL** to `...:1.0.1` in *Settings*.

## 7. Point the Flutter app at Render

Release builds use the Render URL (see `lib/core/network/api_client.dart`), or pass it explicitly:
```bash
flutter run --release --dart-define=API_URL=https://wanderly-api-latest.onrender.com
```

## Limits to know
- **Journal photos** are saved on the container's disk, and Render's disk is wiped on every deploy or restart. Move uploads to Supabase Storage before relying on them.
- The **Free** plan sleeps when idle, so the app's first request after a pause is slow.
