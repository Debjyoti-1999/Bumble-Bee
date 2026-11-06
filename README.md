# Bumble-bee

A near real-time dating backend built with Spring Boot 4. Users register, browse nearby candidates
that fit their preferences, swipe, get matched on a mutual like, and chat over WebSocket or REST.
A small browser UI for manual testing is served at <http://localhost:8080/>.

- **Stack:** Java 17+, Spring Boot 4.1.2 (Web MVC, WebSocket, Data JPA / Hibernate 7, Security,
  OAuth2 Resource Server for JWT, Validation), Flyway, H2 (dev), Jackson 3
- **Auth:** stateless JWT (HS256) bearer tokens, BCrypt password hashing
- **Real time:** raw WebSocket endpoint at `/ws/chat`, with pushes sent after the database commit
- **UI:** dependency-free HTML/CSS/JavaScript in `src/main/resources/static` (no build step)

---

## Table of contents

1. [Running the app](#running-the-app)
2. [Browser test UI](#browser-test-ui)
3. [Configuration](#configuration)
4. [Quick start with curl](#quick-start-with-curl)
5. [API reference](#api-reference)
6. [WebSocket protocol](#websocket-protocol)
7. [How it works](#how-it-works)
8. [Database schema](#database-schema)
9. [Project structure](#project-structure)
10. [Testing](#testing)
11. [Known limitations](#known-limitations)

---

## Running the app

### Prerequisites

- JDK 17 or newer (`java -version`)
- Maven 3.9+ (`mvn -v`). The project does not ship a Maven wrapper.

### Run in development mode

The `dev` profile is the default. It uses an in-memory H2 database, a built-in dev-only JWT key,
and enables the H2 console. Nothing else needs to be installed.

```bash
mvn spring-boot:run
```

The app starts on <http://localhost:8080> once the log shows `Started BumbleBeeApplication`. On
startup, Flyway creates the schema from `src/main/resources/db/migration`, and Hibernate checks
that the entities match it.

Data lives in memory and is lost when the app stops, so every start begins with no users.

`spring-boot:run` serves static files from `target/classes`, so after editing anything in
`src/main/resources/static`, restart the app (or copy the changed files into
`target/classes/static/`) and hard-refresh the browser (Cmd+Shift+R / Ctrl+Shift+R).

### Stop the app

- Started in a terminal with `mvn spring-boot:run` or `java -jar`: press **Ctrl+C** in that
  terminal.
- Running in the background (no terminal to press Ctrl+C in): kill whatever is listening on port
  8080, then confirm the port is free (no output means it stopped):

  ```bash
  lsof -ti:8080 | xargs kill
  lsof -i:8080
  ```

If startup fails with "Port 8080 was already in use", an earlier instance is still running; stop it
as above.

### Build and run the jar

```bash
mvn clean package          # compiles, runs all tests, builds target/bumble-bee-1.0-SNAPSHOT.jar
java -jar target/bumble-bee-1.0-SNAPSHOT.jar
```

Add `-DskipTests` to `mvn package` to skip tests.

### H2 console (dev only)

Open <http://localhost:8080/h2-console> and connect with:

| Field    | Value                                   |
|----------|-----------------------------------------|
| JDBC URL | `jdbc:h2:mem:datingdb`                  |
| User     | `sa`                                    |
| Password | *(empty)*                               |

### Run in production mode

The `prod` profile has no defaults for secrets. If any of these are missing, startup fails.

```bash
export SPRING_PROFILES_ACTIVE=prod
export JWT_SECRET="$(openssl rand -base64 32)"        # keep stable across restarts/instances
export WEBSOCKET_ALLOWED_ORIGINS="https://app.example.com"
export DB_URL="jdbc:postgresql://db-host:5432/datingapp"
export DB_USERNAME="datingapp"
export DB_PASSWORD="..."

java -jar target/dating-app-1.0-SNAPSHOT.jar
```

Before the first production deployment, add your database's JDBC driver and Flyway module to
`pom.xml`. For PostgreSQL:

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

The migration SQL works on H2 and PostgreSQL. On MySQL, change the identity column definitions.

---

## Browser test UI

Open <http://localhost:8080/> after starting the app. The page is plain HTML/CSS/JavaScript
(`index.html`, `app.css`, `app.js` in `src/main/resources/static`) and talks to the same REST and
WebSocket API documented below. Apart from these files, every route needs a token, which is why
the root URL returned 401 before the UI existed.

### Screens

| Screen | What you can do |
|---|---|
| **Log in / Create account** | Register (with field validation messages from the API) or log in. "📍 Use my location" fills in latitude/longitude from the browser. |
| **Discover** | One candidate card at a time (photo or initial, age, distance, bio, interests) with **Pass** and **Like**. A mutual like shows an "It's a match!" notification. |
| **Matches** | Your active matches with unread counts, and a chat pane: live messages, "Load older messages" (cursor paging), automatic read receipts and **Unmatch**. |
| **Profile** | Edit name, bio, picture URL (https only), interests, location and discovery preferences; **Delete my account**. |

The green dot in the top bar means the real-time WebSocket is connected. If it drops, the UI sends
messages over REST instead and reconnects every 3 seconds.

### Testing two users at once

Each browser tab keeps its own login (the token is stored in `sessionStorage`), so one browser is
enough:

1. Tab 1: register (or log in as) user A. Tab 2: register user B close by (e.g. both near
   `40.71, -74.00`), with matching gender preferences.
2. A likes B in **Discover**, then B likes A. Both tabs show the match notification.
3. Open **Matches** in both tabs and chat; messages appear instantly on the other side.

Restarting the app wipes all users (in-memory H2), so register again after each restart. To seed
users quickly from a terminal, use the curl commands in [Quick start with curl](#quick-start-with-curl).

### "Use my location" doesn't work

The button uses the browser Geolocation API. `localhost` counts as a secure origin, and the server
sends no header that restricts geolocation, so failures come from browser or OS permissions. The UI
shows which case occurred:

| Message | Fix |
|---|---|
| Location permission was denied | Click the icon left of the address bar → Site settings → Location → **Allow**, then reload. Browsers remember a previous "Block" and won't ask again. |
| Your position is unavailable | macOS: System Settings → Privacy & Security → **Location Services** → turn it on and enable your browser. Keep Wi-Fi on; Macs locate themselves via nearby Wi-Fi networks. |
| Finding your location timed out | Try again (15 s timeout), or type the coordinates manually. |

You can always enter latitude/longitude by hand. Discovery only shows users within both people's
`maxDistanceKm` (default 50 km, maximum 500 km), so test users must be near each other.

### How the UI is wired

- **Same origin:** the UI is served by the app itself, so REST calls need no CORS configuration,
  and the WebSocket handshake passes the origin check (Spring always accepts same-origin
  handshakes; `app.websocket.allowed-origins` only matters for UIs hosted elsewhere).
- **Public files only:** `SecurityConfig` permits anonymous `GET` on `/`, `/index.html`, `/app.js`
  and `/app.css`. Everything else, including every `/api/**` call the UI makes, still requires a
  bearer token.
- **XSS-safe rendering:** all user-supplied text (names, bios, messages) is inserted with
  `textContent`, never `innerHTML`.
- **Session handling:** a 401 from the API (expired token, deleted account) returns you to the
  login screen. Logging out clears the token, closes the WebSocket and clears on-screen data.
- **Notifications** appear top-right, at most three at a time, and never block clicks.

---

## Configuration

| Property | Env var (prod) | Default | Purpose |
|---|---|---|---|
| `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` | `dev` | `dev` or `prod` |
| `app.jwt.secret` | `JWT_SECRET` | dev key in `application-dev.properties` | Base64 HMAC key, must decode to at least 32 bytes |
| `app.jwt.ttl` | — | `PT24H` | Token lifetime (ISO-8601 duration) |
| `app.jwt.issuer` | — | `datingapp` | `iss` claim, checked on every request |
| `app.websocket.allowed-origins` | `WEBSOCKET_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` (dev) | Comma-separated browser origins allowed to open `/ws/chat` |
| `spring.datasource.*` | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | H2 in-memory (dev) | Database connection |
| `spring.h2.console.enabled` | — | `true` in dev, `false` otherwise | H2 web console |

Files:

- `src/main/resources/application.properties`: shared settings
- `src/main/resources/application-dev.properties`: local development
- `src/main/resources/application-prod.properties`: production (everything comes from env vars)

To log SQL in dev, uncomment `logging.level.org.hibernate.SQL=debug` in
`application-dev.properties`.

---

## Quick start with curl

```bash
B=http://localhost:8080

# 1. Register two users (each call returns an access token)
curl -s -X POST $B/api/auth/register -H 'Content-Type: application/json' -d '{
  "email":"alice@example.com","password":"password123","name":"Alice",
  "dateOfBirth":"1995-05-15","gender":"FEMALE",
  "latitude":40.7128,"longitude":-74.0060,
  "preferredGender":"MALE","interests":["hiking","coffee"]}'

curl -s -X POST $B/api/auth/register -H 'Content-Type: application/json' -d '{
  "email":"bob@example.com","password":"password123","name":"Bob",
  "dateOfBirth":"1993-08-20","gender":"MALE",
  "latitude":40.7580,"longitude":-73.9855,"preferredGender":"FEMALE"}'

# 2. Save the tokens (or log in again later)
TA=$(curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"password123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
TB=$(curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"bob@example.com","password":"password123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')

# 3. Alice browses candidates -> [{"id":2,"name":"Bob","age":33,...,"distanceKm":5}]
curl -s $B/api/users/me/potential-matches -H "Authorization: Bearer $TA"

# 4. Mutual like -> second call returns {"matched":true,"matchId":1}
curl -s -X POST $B/api/swipes -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"swipedId":2,"liked":true}'
curl -s -X POST $B/api/swipes -H "Authorization: Bearer $TB" -H 'Content-Type: application/json' -d '{"swipedId":1,"liked":true}'

# 5. Chat
curl -s -X POST $B/api/matches/1/messages -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"content":"Hi Bob!"}'
curl -s "$B/api/matches/1/messages?size=20" -H "Authorization: Bearer $TB"
curl -s -X POST $B/api/matches/1/messages/read -H "Authorization: Bearer $TB"

# 6. List matches
curl -s $B/api/matches -H "Authorization: Bearer $TB"
```

---

## API reference

Every endpoint except register and login needs `Authorization: Bearer <accessToken>`. The acting
user always comes from the token. Requests never carry a user id for "who am I".

All timestamps are UTC ISO-8601 strings (for example `2026-11-06T10:18:08.771941Z`).

### Auth

| Method | Path | Body | Success |
|---|---|---|---|
| POST | `/api/auth/register` | `RegisterRequest` | `201` `AuthResponse` |
| POST | `/api/auth/login` | `{"email","password"}` | `200` `AuthResponse` |

`AuthResponse`: `{"accessToken","tokenType":"Bearer","expiresInSeconds","user":UserProfile}`

`RegisterRequest` fields and validation:

| Field | Required | Rules |
|---|---|---|
| `email` | yes | valid email, ≤ 254 chars, unique (case-insensitive) |
| `password` | yes | 8–72 chars |
| `name` | yes | ≤ 100 chars |
| `dateOfBirth` | yes | `YYYY-MM-DD`, in the past, user must be **18+** |
| `gender` | yes | `MALE`, `FEMALE`, `NON_BINARY` |
| `latitude` / `longitude` | yes | −90..90 / −180..180 |
| `bio` | no | ≤ 1000 chars |
| `profilePictureUrl` | no | `https://` URL, ≤ 2048 chars |
| `interests` | no | ≤ 20 items, each ≤ 50 chars; stored trimmed and lower-cased |
| `maxDistanceKm` | no | 1–500, default 50 |
| `minAge` / `maxAge` | no | 18–100, defaults 18 / 60, `minAge ≤ maxAge` |
| `preferredGender` | no | gender enum; omit for "no preference" |

Unknown fields (such as `id`) are ignored, so clients cannot set server-managed fields.

### Users

| Method | Path | Body / params | Success |
|---|---|---|---|
| GET | `/api/users/me` | — | `200` `UserProfile` (full, private) |
| PUT | `/api/users/me` | `UpdateProfileRequest` (partial) | `200` `UserProfile` |
| DELETE | `/api/users/me` | — | `204` |
| GET | `/api/users/me/potential-matches` | `page` (≥0, default 0), `size` (1–50, default 20) | `200` `PublicProfile[]` |
| GET | `/api/users/{id}` | — | `200` `PublicProfile` |

`UpdateProfileRequest` accepts `name`, `bio`, `profilePictureUrl`, `latitude`, `longitude`,
`interests`, `maxDistanceKm`, `minAge`, `maxAge` and `preferredGender`, with the same rules as
registration. Fields you leave out or set to `null` stay unchanged. Email, password, date of birth
and gender cannot be changed here.

`PublicProfile` is what other users see:
`{id, name, age, gender, bio, profilePictureUrl, interests, distanceKm}`. It never includes email,
date of birth, exact coordinates or preferences.

### Swipes

| Method | Path | Body | Success |
|---|---|---|---|
| POST | `/api/swipes` | `{"swipedId": 2, "liked": true}` | `200` `{"matched": bool, "matchId": id \| null}` |

### Matches and messages

| Method | Path | Body / params | Success |
|---|---|---|---|
| GET | `/api/matches` | — | `200` `Match[]` (your active matches, newest first) |
| GET | `/api/matches/{matchId}` | — | `200` `Match` |
| POST | `/api/matches/{matchId}/unmatch` | — | `204` (safe to repeat) |
| GET | `/api/matches/{matchId}/messages` | `beforeId` (optional), `size` (1–100, default 50) | `200` `ChatMessage[]` (oldest → newest) |
| POST | `/api/matches/{matchId}/messages` | `{"content": "..."}` (1–2000 chars, not blank) | `201` `ChatMessage` |
| POST | `/api/matches/{matchId}/messages/read` | — | `204` |

- `Match`: `{matchId, matchedAt, active, matchedUser: PublicProfile}`
- `ChatMessage`: `{id, matchId, senderId, content, sentAt, read}`

**Message paging:** the first call returns the newest `size` messages. To load older ones, pass the
smallest `id` you currently have as `beforeId`.

### Errors

Errors come back as [RFC 9457 problem details](https://www.rfc-editor.org/rfc/rfc9457)
(`application/problem+json`):

```json
{"title":"Conflict","status":409,"detail":"Already swiped on this user","instance":"/api/swipes"}
```

Validation failures add a map of field errors:

```json
{"status":400,"detail":"Validation failed","errors":{"email":"must be a well-formed email address"}}
```

| Status | When |
|---|---|
| 400 | Validation failure, malformed JSON, self-swipe, underage, `minAge > maxAge`, blank message |
| 401 | Missing, invalid or expired token; wrong email or password |
| 404 | Resource doesn't exist, or you aren't a participant of the match |
| 409 | Duplicate email, already swiped, messaging an unmatched (inactive) match |

---

## WebSocket protocol

### Connecting

```
ws://localhost:8080/ws/chat?access_token=<JWT>
```

Clients that can set headers may send `Authorization: Bearer <JWT>` instead. A handshake without
a valid token is rejected with HTTP 401. Browser connections must come from an origin listed in
`app.websocket.allowed-origins`.

Browser example:

```js
const ws = new WebSocket(`ws://localhost:8080/ws/chat?access_token=${token}`);
ws.onmessage = (e) => console.log(JSON.parse(e.data));
ws.onopen = () => ws.send(JSON.stringify({ type: "CHAT", matchId: 1, content: "Hello!" }));
```

### Client → server

```json
{ "type": "CHAT", "matchId": 1, "content": "Hello!" }
```

If `type` is omitted, it is treated as `CHAT`.

### Server → client

| `type` | Payload | When |
|---|---|---|
| `CHAT` | `{type, messageId, matchId, senderId, content, sentAt}` | A message was saved, whether sent over WebSocket **or REST**. Delivered to the recipient and to all of the sender's sessions (as confirmation and to sync multiple devices). |
| `MATCH` | `{type, matchId, matchedUserId, matchedUserName}` | A mutual like created a match. Each user receives the other's id and name. |
| `ERROR` | `{type, message}` | The last frame you sent was rejected (malformed JSON, missing `matchId`, not a participant, inactive match, blank or too-long content). **The connection stays open.** |

---

## How it works

### Request flow and layering

```
Client ──HTTP──> SecurityFilterChain (JWT) ──> Controller ──> Service (@Transactional) ──> Repository ──> DB
                                                                │
                                                                └─ publishes domain event
                                                                        │ (after commit)
Client <──WS── WebSocketSessionRegistry <── RealtimeNotificationListener
```

- **Controllers** (`controller/`) are thin. They validate the request body (`@Valid`), resolve the
  current user with `@CurrentUserId`, and call a service.
- **Services** (`service/`) contain all business rules and own the transactions. Read-only
  services are annotated `@Transactional(readOnly = true)`, and writes opt in to a read-write
  transaction.
- **DTOs** (`dto/`) are Java records. Entities are never serialized, which prevents
  mass-assignment, lazy-loading serialization errors and data leaks. Mapping happens inside the
  transaction because `spring.jpa.open-in-view=false`.
- **Events** (`event/`) decouple the domain from WebSocket delivery.

### Authentication

1. **Register** (`AuthService.register`):
   - The email is normalized (trimmed, lower-cased) and checked for uniqueness.
   - The password is hashed with BCrypt.
   - `UserService.createAccount` enforces the 18+ rule and `minAge ≤ maxAge`, then saves the user.
   - The database's unique constraint is a backstop if two registrations race; that case maps to
     409.
2. **Login** (`AuthService.login`):
   - Looks up the user by normalized email and checks the password.
   - When the email doesn't exist, the password is still compared against a dummy BCrypt hash. This
     keeps response times the same, so attackers can't tell which emails are registered.
   - Both failure cases return the same 401 message.
3. **Tokens** (`JwtService`): HS256 JWTs with `sub` = user id, `iss` = `app.jwt.issuer`, `iat`,
   and `exp` = now + `app.jwt.ttl`. The signing key comes from `app.jwt.secret` and must decode to
   at least 32 bytes, or startup fails.
4. **REST requests:** Spring Security's OAuth2 resource server checks the signature, expiry and
   issuer. `@CurrentUserId` turns the token's `sub` into a `Long`. The session is stateless, and
   CSRF protection is disabled because no cookies are used.
5. **WebSocket handshake** (`JwtHandshakeInterceptor`): reads the token from the `Authorization`
   header or the `access_token` query parameter, decodes it with the same `JwtDecoder`, and stores
   the user id in the session attributes. If the token is missing or invalid, the upgrade is
   rejected with 401.
6. **Deleted accounts:** a token stays cryptographically valid until it expires. However, every
   service loads the user, so requests from a deleted account get a 404.

### Authorization

- The acting user always comes from the token, never from the request.
- `MatchService.requireParticipant` guards every match and message operation. A non-participant
  gets the **same 404 as a missing match**, so match ids can't be discovered by probing.
- There is no endpoint that lists all users.
- The only unauthenticated routes are `POST /api/auth/register`, `POST /api/auth/login`, the
  WebSocket handshake (authenticated by `JwtHandshakeInterceptor` instead), `/error`, the four
  static UI files, and `/h2-console/**` in the dev profile.

### Potential matches (discovery)

`UserService.getPotentialMatches` runs one paged JPQL query (`UserRepository.findCandidates`). It
returns users who:

1. are not the requester, and haven't been swiped on by the requester yet;
2. **gender, both directions:** match the requester's `preferredGender` (if set), and either have
   no preference or prefer the requester's gender;
3. **age, both directions:** fall within the requester's `minAge..maxAge`, and the requester's age
   falls within theirs. The requester's range is converted to a date-of-birth range
   (`today − (maxAge+1) years + 1 day` … `today − minAge years`) so the database can compare dates
   directly;
4. **distance, both directions:** are within `min(requester.maxDistanceKm, candidate.maxDistanceKm)`.

Distance is computed in two steps:

- **Bounding box:** `latitude BETWEEN … AND longitude BETWEEN …` lets the database use the
  `(latitude, longitude)` index to discard far-away rows cheaply.
- **Exact radius check:** an equirectangular approximation,
  `Δlat² + (Δlon·cos(lat))² ≤ (radiusKm / 111.195)²`. At ≤ 500 km the error is well under 1%,
  and it only needs arithmetic that every SQL database supports.

Results are ordered by `lastActive DESC` (most recently active first). Each result's `distanceKm`
is computed with the haversine formula (`GeoUtils`) and rounded to whole kilometres (minimum 1).
Exact coordinates are never returned, which makes it harder to pinpoint someone's location by
comparing distances.

### Swiping and matching

`SwipeService.processSwipe(swiperId, swipedId, liked)`:

1. Rejects swiping on yourself (400).
2. **Locks the user row with the lower id** (`SELECT … FOR UPDATE`) and loads the other user. If
   either doesn't exist, it returns 404.
3. Rejects a repeat swipe (409). The unique constraint on `(swiper_id, swiped_id)` is a backstop.
4. Saves the swipe and updates the swiper's `lastActive`.
5. If it's a like and the other user has already liked the swiper, it calls
   `MatchService.createMatch`. That method stores the pair in canonical order
   (`user1_id < user2_id`), is idempotent, and publishes `MatchCreatedEvent`.

**Why the lock?** Without it, if A and B like each other at the same instant, each transaction
might not see the other's uncommitted swipe, and no match is created. Alternatively, both might
create one. Both transactions for a pair lock the *same* row (the lower id), so they run one after
the other, and the second one always sees the first swipe. `ConcurrentSwipeTest` checks this:
with the lock removed, it fails on its first iteration.

The database backs this up with `UNIQUE(user1_id, user2_id)` plus `CHECK(user1_id < user2_id)`,
so `(A,B)` and `(B,A)` can never both exist.

### Chat

`ChatService.sendMessage(matchId, senderId, content)` handles messages from both REST and
WebSocket:

1. Validates that the content is not blank and is ≤ 2000 characters (400).
2. `requireParticipant` (404), then checks the match is active (409 if unmatched).
3. Saves the message and publishes `ChatMessageSentEvent`.

History (`getMessages`) uses **cursor paging on the message id**. Ids only ever increase, so
`WHERE match_id = ? [AND id < beforeId] ORDER BY id DESC LIMIT size` is served by the
`(match_id, id)` index. The page is then reversed to chronological order.

`markMessagesAsRead` runs a single bulk `UPDATE`. It marks only messages sent by the *other*
participant as read.

### Real-time delivery

- **Events after commit:** `RealtimeNotificationListener` uses
  `@TransactionalEventListener(phase = AFTER_COMMIT)`, so clients are never told about a match or
  message that was rolled back. Because sending is event-driven, REST messages are pushed exactly
  like WebSocket ones. Services also don't depend on the WebSocket layer, which avoids circular
  dependencies.
- **Session registry** (`WebSocketSessionRegistry`) maps `userId → {sessionId → session}`:
  - A user can have several devices connected at once.
  - Each session is wrapped in `ConcurrentWebSocketSessionDecorator`. Raw sessions don't allow
    concurrent sends; the wrapper queues them, with a 10 s send-time limit and a 512 KB buffer
    limit. Slow clients that exceed either limit are disconnected instead of blocking others.
  - Registering and unregistering use atomic `ConcurrentHashMap.compute*` calls, so a connect and
    a disconnect happening at the same moment can't lose a session.
- **Handler** (`ChatWebSocketHandler`):
  - On connect, it registers the session and updates `lastActive`.
  - On each frame, it parses JSON into a record and checks `type` and `matchId`, then calls
    `ChatService`.
  - Every failure is answered with an `ERROR` frame instead of closing the connection.
  - On close, it unregisters the session.

### Profiles and account deletion

- **Partial update:** fields that are present get validated and applied; then `minAge ≤ maxAge` is
  re-checked across fields, and `lastActive` is updated.
- **Delete account** (`UserService.deleteAccount`) runs in one transaction and removes, in
  foreign-key order:
  1. messages in the user's matches
  2. the user's matches
  3. swipes by or about the user
  4. the user (their interests go with it)

### Error handling

`GlobalExceptionHandler` extends Spring's `ResponseEntityExceptionHandler`, which already covers
malformed JSON, type mismatches and invalid query parameters. It adds:

- `ApiException` subclasses (`BadRequestException`, `NotFoundException`, `ConflictException`)
  → their status and message
- `BadCredentialsException` → 401
- `DataIntegrityViolationException` (a race caught by a unique constraint) → 409
- `MethodArgumentNotValidException` → 400 with a per-field `errors` map

---

## Database schema

Flyway owns the schema (`src/main/resources/db/migration/V1__init_schema.sql`). Hibernate only
checks it (`ddl-auto=validate`). To change the schema, add a new `V2__….sql` file; never edit an
applied migration.

| Table | Key columns | Constraints / indexes |
|---|---|---|
| `users` | email, password_hash, name, date_of_birth, gender, bio, profile_picture_url, latitude, longitude, max_distance_km, min_age, max_age, preferred_gender, created_at, last_active | `UNIQUE(email)`, `CHECK(min_age <= max_age)`, index `(latitude, longitude)` |
| `user_interests` | user_id, interest | PK `(user_id, interest)`, FK → users `ON DELETE CASCADE` |
| `swipes` | swiper_id, swiped_id, liked, created_at | `UNIQUE(swiper_id, swiped_id)`, `CHECK(swiper_id <> swiped_id)`, index `(swiped_id, swiper_id, liked)` |
| `matches` | user1_id, user2_id, matched_at, active | `UNIQUE(user1_id, user2_id)`, `CHECK(user1_id < user2_id)`, index `(user2_id)` |
| `chat_messages` | match_id, sender_id, content (≤ 2000), sent_at, is_read | index `(match_id, id)`, index `(match_id, is_read)` |

Timestamps are `TIMESTAMP WITH TIME ZONE`, mapped to `java.time.Instant`. The read flag is stored as
`is_read` because `READ` is a reserved word in MySQL.

---

## Project structure

```
src/main/java/com/datingapp/
├── BumbleBeeApplication.java
├── config/        SecurityConfig (filter chains, JWT encoder/decoder, BCrypt), WebSocketConfig,
│                  JwtProperties, WebSocketProperties
├── security/      JwtService, JwtHandshakeInterceptor, @CurrentUserId
├── controller/    AuthController, UserController, SwipeController, MatchController
├── service/       AuthService, UserService, SwipeService, MatchService, ChatService, GeoUtils
├── repository/    Spring Data JPA repositories (custom JPQL, locking, bulk updates/deletes)
├── entity/        User, Gender, Swipe, Match, ChatMessage
├── dto/           Request/response records
├── event/         MatchCreatedEvent, ChatMessageSentEvent
├── exception/     ApiException hierarchy, GlobalExceptionHandler
└── websocket/     ChatWebSocketHandler, WebSocketSessionRegistry,
                   RealtimeNotificationListener, WebSocketPayloads
src/main/resources/
├── application.properties, application-dev.properties, application-prod.properties
├── db/migration/V1__init_schema.sql
└── static/        Browser test UI: index.html, app.css, app.js
```

---

## Testing

```bash
mvn test                               # all tests
mvn test -Dtest=ConcurrentSwipeTest    # a single class
```

The tests are integration tests that start the full Spring context against in-memory H2. That
means real security, real SQL, Flyway and schema validation all run.

| Class | Covers |
|---|---|
| `AuthApiTest` | Register/login, BCrypt, duplicate email (case-insensitive), 18+ rule, field errors, ignoring client-supplied `id`, 401 without/with a bad token |
| `UserApiTest` | Partial update and validation, public profile hides private fields, discovery filters (gender/age/distance in both directions, prior swipes), pagination, account deletion removing related data |
| `SwipeAndChatApiTest` | Mutual like → match, dislike, 400/404/409 swipe errors, non-participant 404s, cursor-paged history, read receipts, unmatch blocks messaging |
| `ConcurrentSwipeTest` | 20 rounds of simultaneous mutual likes → exactly one match each time |
| `ChatWebSocketIntegrationTest` | Handshake authentication (no token, bad token, legacy `?userId=`), `MATCH` push to both users, WebSocket chat delivery and echo, REST message push, `ERROR` frames that keep the connection open |
| `BumbleBeeApplicationTests` | Context loads (migrations and schema validation) |

All tests share one database, so each test uses unique emails and, where location matters, its
own geographic area (`ApiTestSupport.uniqueLocation()`).

The browser UI has no automated tests in the repository. To check it by hand, follow
[Testing two users at once](#testing-two-users-at-once). The flows worth covering are: register
(including a validation error), discover, mutual like → match notification in both tabs, live chat
in both directions, history after a page reload, profile edit, unmatch (further messages are
rejected), logout/login, and account deletion.

---

## Known limitations

- **Single instance for real time:** WebSocket sessions are tracked in memory, so pushes only
  reach users connected to the same instance. To scale out, publish events to a shared broker
  (Redis pub/sub, or STOMP with a RabbitMQ broker relay) and have each instance deliver to its own
  sessions through `WebSocketSessionRegistry`.
- **Token in the WebSocket URL:** browsers can't set headers on a WebSocket upgrade, so the token
  goes in the query string. Make sure proxies and access logs don't record query strings, or switch
  to a short-lived, single-use ticket for the handshake.
- **No token revocation or refresh:** tokens are valid until they expire (24 h by default).
  Changing `JWT_SECRET` invalidates every token at once.
- **Antimeridian:** the discovery bounding box is clamped to ±180° longitude, so users within a few
  hundred km of the 180° line may not see candidates on the other side.
- **No rate limiting** on login or registration.
- **Partial updates can't clear fields:** `null` means "unchanged", so there's no way to reset
  `bio` or `preferredGender` back to empty or "no preference". (An empty string does clear `bio`,
  and the UI's profile form sends one when you empty the field.) In the UI, choosing
  "(unchanged)" for "Interested in" or leaving the picture URL empty keeps the current value.
- **Test UI, not a product UI:** it is meant for local manual testing. It has no image upload
  (only https picture URLs), no offline support, and unread counts only track messages received
  while the page is open.
