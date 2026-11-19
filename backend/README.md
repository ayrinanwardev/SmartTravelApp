# Wanderly API (Kotlin + Spring Boot)

REST + WebSocket backend for the Wanderly Flutter app: group trips with live sync, live location sharing, SSLCommerz payments and push notifications.

**Stack:** Kotlin 2.3 (JVM 21) · Spring Boot 4.1 · Spring WebSocket · Firebase Admin (FCM) · SSLCommerz · Spring Security + JWT · Spring Data JPA / Hibernate · PostgreSQL on Supabase (H2 for dev and tests) · springdoc OpenAPI

## Run it

You only need JDK 21: the `mvnw` wrapper downloads Maven, and Maven downloads the Kotlin compiler.

**Option A: no database setup (in-memory H2, data resets on restart)**
```bash
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

**Option B: Supabase (hosted PostgreSQL, recommended)**

1. Create a free project at [supabase.com](https://supabase.com). The **Singapore (ap-southeast-1)** region is closest to Bangladesh. Save the database password you choose.
2. In the dashboard, click **Connect**, then open the **Session pooler** tab. Note the **host** (e.g. `aws-0-ap-southeast-1.pooler.supabase.com`) and the **user** (`postgres.<project-ref>`).
   * Use the Session pooler, not the "Direct connection": the direct one is IPv6-only on the free plan and often fails on home internet in Bangladesh.
3. Copy `.env.example` to `.env` in this folder and fill in `SUPABASE_DB_HOST`, `SUPABASE_DB_USER` and `SUPABASE_DB_PASSWORD`.
4. Start the API with the `supabase` profile:
   ```bash
   mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=supabase
   ```
   On first start Hibernate creates the `wanderly` schema and all tables, then loads the sample data and the demo account.
5. Check it worked:
   * http://localhost:8080/actuator/health should say `UP`.
   * In Supabase, open **Table Editor**, choose the schema **`wanderly`** in the dropdown, and you'll see `users`, `trips`, `destinations` and the other tables.

**About the `wanderly` schema:** Supabase automatically publishes the `public` schema through its Data API, using a key that is public by design. Wanderly's tables live in a separate `wanderly` schema, so only this backend can read or write them.
- **Don't** add `wanderly` to *Project Settings → Data API → Exposed schemas*.
- **Don't** move the tables to `public`.

**Supabase free plan limits:**
- Projects pause after about a week without activity. Press **Restore** in the dashboard if the API can't connect.
- The connection pool is kept at 5 (`DB_POOL_SIZE`) to stay within the pooler's limits.

**Option C: local PostgreSQL with Docker**
```bash
docker compose up -d
```
```bash
mvnw.cmd spring-boot:run
```
If you don't use Docker, create the database `wanderly` yourself and set the environment variables below.

Then open:
- Swagger UI: http://localhost:8080/swagger-ui.html (click **Authorize** and paste the token)
- Health check: http://localhost:8080/actuator/health

On first start the app loads 12 destinations and 10 hotels, plus a demo account:
**`demo@wanderly.app` / `wanderly123`**

**Deploy with Docker + Render:** see [DEPLOY.md](DEPLOY.md).

Run the tests:
```bash
mvnw.cmd test
```

### Configuration (environment variables)

Set these either as real environment variables or in `backend/.env` (see `.env.example`). Real environment variables win.

| Variable | Default | Purpose |
|---|---|---|
| `SUPABASE_DB_HOST` / `SUPABASE_DB_USER` / `SUPABASE_DB_PASSWORD` | — | Supabase Session pooler connection (`supabase` profile) |
| `SUPABASE_DB_PORT` / `SUPABASE_DB_NAME` | `5432` / `postgres` | Pooler port and database |
| `DB_SCHEMA` | `wanderly` | Schema that holds all Wanderly tables |
| `DB_POOL_SIZE` | `5` | Max DB connections (`supabase` profile) |
| `DB_URL` | `jdbc:postgresql://localhost:5432/wanderly` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `wanderly` / `wanderly` | DB credentials |
| `JWT_SECRET` | dev-only key | Base64 HMAC key (at least 256 bits). **Change it in production.** |
| `JWT_EXPIRATION_HOURS` | `72` | Token lifetime |
| `CORS_ORIGINS` | `*` | Allowed origins (comma-separated) |
| `UPLOAD_DIR` | `uploads` | Folder for journal photos |
| `SEED_DATA` | `true` | Load sample data when the DB is empty |
| `PUBLIC_BASE_URL` | `http://localhost:8080` | Public URL of this API. SSLCommerz sends the browser and IPN callbacks here, so on Render set it to the service URL |
| `SSLCOMMERZ_STORE_ID` / `SSLCOMMERZ_STORE_PASSWORD` | `testbox` / `qwerty` | SSLCommerz store credentials (the defaults are the public sandbox store) |
| `SSLCOMMERZ_SANDBOX` | `true` | `false` to use the live gateway |
| `FIREBASE_CREDENTIALS_BASE64` | empty | Base64 of a Firebase service-account JSON. Empty = push off (real-time WebSocket events still work) |

## Architecture

```
controller/  REST endpoints (thin: validate input, call a service)
service/     business logic + transactions, maps entities -> DTOs
repository/  Spring Data JPA interfaces
domain/      JPA entities
realtime/    WebSocket handler, JWT handshake, RealtimeHub (sessions), after-commit publisher
payment/     PaymentGateway -> SslCommerzGateway, PaymentService (validation)
dto/         request/response data classes (JSON matches the Flutter models)
security/    JwtService + JwtAuthenticationFilter
config/      SecurityConfig, WebConfig (uploads, OpenAPI), DataSeeder, properties
exception/   ApiException + GlobalExceptionHandler (RFC 9457 problem+json)
```

**Request flow:**
`Flutter (Dio)` → `JwtAuthenticationFilter` (reads `Authorization: Bearer …` and loads the user) → `Controller` → `Service` (inside a transaction; checks that the trip, expense, etc. belongs to the user) → `Repository` → PostgreSQL.

**Database tables:** `users`, `destinations` (+ gallery, seasons, highlights), `attractions`, `reviews`, `hotels` (+ images, amenities), `bookings`, `trips`, `itinerary_days`, `activities`, `wishlist_items`, `expenses`, `journal_entries` (+ photos), `notifications`, `trip_members`, `device_tokens`.

- Enums travel as lowercase JSON (`"beach"`, `"sightseeing"`), the same as the Dart enum names.
- Dates are ISO strings (`2026-12-05`).

## Endpoints

🔓 = public, 🔒 = needs `Authorization: Bearer <token>`

| Method | Path | Description |
|---|---|---|
| 🔓 POST | `/api/auth/register` | `{name, email, password}` → `{token, user}` |
| 🔓 POST | `/api/auth/login` | `{email, password}` → `{token, user}` |
| 🔓 GET | `/api/destinations` | Filters: `q`, `category`, `season`, `maxBudget`, `trending`, `popular`, `sort=rating\|price_low\|price_high` |
| 🔓 GET | `/api/destinations/{id}` | Full detail: gallery, cost, attractions, reviews |
| 🔓 GET | `/api/destinations/{id}/weather` | 5-day forecast + suggestion (simulated) |
| 🔒 POST | `/api/destinations/{id}/reviews` | `{rating, comment}` |
| 🔓 GET | `/api/hotels?destinationId=&sort=price` | Hotel list |
| 🔓 GET | `/api/hotels/{id}` | Hotel detail |
| 🔒 GET/POST | `/api/bookings` | List / create `{hotelId, checkIn, checkOut, guests, rooms}` (adds 10% tax) |
| 🔒 GET | `/api/bookings/{id}` | One booking with `paymentStatus` (`unpaid` | `paid` | `failed`) |
| 🔒 PATCH | `/api/bookings/{id}/cancel` | Cancel a booking |
| 🔒 POST | `/api/payments/bookings/{id}/session` | Start SSLCommerz checkout → `{gatewayUrl, tranId, amount, currency}` |
| 🔓 POST | `/api/payments/sslcommerz/success|fail|cancel` | Browser callbacks from SSLCommerz; validated server-side, then 303 → `/payments/result` |
| 🔓 POST | `/api/payments/sslcommerz/ipn` | Server-to-server payment notification (validated the same way) |
| 🔒 GET | `/api/trips?status=upcoming\|past` | My trips |
| 🔒 POST | `/api/trips` | `{destinationId, title?, startDate, endDate, travelers, budget?, days?}` |
| 🔒 GET/PUT/DELETE | `/api/trips/{id}` | Read / update (resizes days) / delete |
| 🔒 POST | `/api/trips/{id}/days/{day}/activities` | `{title, time:"HH:mm", type, note?, cost?}` |
| 🔒 PUT/DELETE | `/api/trips/{id}/days/{day}/activities/{activityId}` | Edit / remove an activity |
| 🔒 PUT | `/api/trips/{id}/days/{day}/activities/order` | `{activityIds:[...]}` drag-to-reorder |
| 🔒 POST | `/api/trips/{id}/share` | Owner only → `{code}` (6 characters) |
| 🔒 POST | `/api/trips/join` | `{code}` → the shared trip, as a member |
| 🔒 GET | `/api/trips/{id}/members` | Members with role and online status |
| 🔒 DELETE | `/api/trips/{id}/members/me` | Leave a shared trip |
| 🔒 GET | `/api/wishlist`, `/api/wishlist/ids` | Saved destinations |
| 🔒 PUT/DELETE | `/api/wishlist/{destinationId}` | Save / unsave |
| 🔒 GET/PUT | `/api/budget?tripId=` | Summary with by-category totals and alerts / set total |
| 🔒 GET/POST | `/api/expenses?tripId=` | List / add `{title, amount, category, date?, tripId?}` |
| 🔒 DELETE | `/api/expenses/{id}` | Remove an expense |
| 🔒 GET/POST | `/api/journal?album=` | List / create a memory |
| 🔒 GET/PUT/DELETE | `/api/journal/{id}` | Read / edit / delete |
| 🔒 GET | `/api/journal/albums` | Album names |
| 🔒 POST | `/api/journal/photos` | Multipart `file` → `{url}`, served from `/uploads/...` |
| 🔒 GET/PATCH | `/api/users/me` | Profile / update `{name?, notificationsEnabled?}` |
| 🔒 GET | `/api/users/me/stats` | Counts of trips, saved, memories, bookings and unread notifications |
| 🔒 GET | `/api/notifications` | Trip, booking, budget and welcome notifications |
| 🔒 PATCH | `/api/notifications/{id}/read`, `/api/notifications/read-all` | Mark read |
| 🔒 POST | `/api/devices`, `/api/devices/unregister` | `{token, platform}` register / remove an FCM token |
| 🔓 POST | `/api/ai/plan` | `{prompt}` → reply + plan (itinerary, cost breakdown, tips) |
| 🔓 GET | `/api/currency/rates`, `/api/currency/convert?from=&to=&amount=` | Currency tools (demo rates) |
| 🔓 GET | `/api/packing?destinationId=&days=` | Smart packing list |

Errors are returned as `application/problem+json`, for example:
`{"status":404,"detail":"Trip 'x' not found"}`. Validation errors also include an `errors` map.

### Example
```bash
curl -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"email\":\"demo@wanderly.app\",\"password\":\"wanderly123\"}"
```
```bash
curl localhost:8080/api/trips -H "Authorization: Bearer <token>"
```

## Real-time (WebSocket)

Connect to `ws://<host>/ws?token=<JWT>` (`wss://` on Render). A missing or invalid token is refused at the handshake. Every frame is JSON: `{"type": "...", "data": {...}}`.

**Server → client** (sent only after the database transaction commits):

| Type | Data |
|---|---|
| `connected` | `{userId}` |
| `notification.created` | The new notification |
| `trip.updated` / `trip.deleted` | `{tripId}`: reload it |
| `trip.member_joined` | `{tripId, userId, name}` |
| `booking.updated` | The booking, incl. `paymentStatus` |
| `location.snapshot` | `{tripId, locations:[...]}`: reply to `trip.watch` |
| `location.updated` / `location.stopped` | `{tripId, userId, name, lat, lng, accuracy, at}` / `{tripId, userId}` |
| `pong`, `error` | Reply to `ping`; `{message}` |

**Client → server:** `{"type":"ping"}`, `{"type":"trip.watch","tripId":"…"}`, `{"type":"location.update","tripId":"…","lat":…,"lng":…,"accuracy":…}`, `{"type":"location.stop","tripId":"…"}`. Location messages are accepted only from members of that trip and are relayed only to its members; positions are kept in memory, never stored.

## Payments (SSLCommerz)

1. The app calls `POST /api/payments/bookings/{id}/session`; the server opens an SSLCommerz session and returns the hosted checkout URL.
2. The app shows that page in a WebView; the user pays with bKash, Nagad, Rocket or a card.
3. SSLCommerz posts to `/api/payments/sslcommerz/success` (and the IPN endpoint). The server **does not trust these posts**: it calls SSLCommerz's validation API with the `val_id` and checks status, amount, currency and transaction id.
4. Only then is the booking marked paid; the app receives `booking.updated` and a notification, and the WebView lands on `/payments/result`.

Sandbox test card: any of SSLCommerz's published test cards, or the bKash/Nagad sandbox flows on the checkout page.

## Push notifications (FCM)

1. Create a Firebase project, add an Android app with package `com.wanderly.app`, and put `google-services.json` in `android/app/`.
2. In Firebase → Project settings → Service accounts, generate a private key, base64-encode the JSON and set it as `FIREBASE_CREDENTIALS_BASE64`.
3. The app registers its FCM token at `/api/devices`; every notification is then also pushed. Invalid tokens are removed automatically.

## Before production
- Use Flyway migrations instead of `ddl-auto: update`.
- Set a strong `JWT_SECRET` and restrict `CORS_ORIGINS`.
- Move uploads to S3, Cloudinary or Firebase Storage.
- Replace the demo exchange rates and weather with live APIs.
- Connect `AiPlannerService` to an LLM, keeping the same response shape.
- Use Redis pub/sub (or a STOMP broker relay) for WebSocket events when running more than one instance.
