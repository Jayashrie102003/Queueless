# QueueLess

QueueLess is a virtual queue system for hospitals and service centres. Patients join a department queue from their phone or computer, see their token number and estimated wait, and can leave the queue. Staff call the next patient from a dashboard. A public screen shows the number being served, with no patient names.

Built as a full-stack portfolio project: **Java 21, Spring Boot 4.1.1, MySQL, plain HTML, CSS and JavaScript.**

## Features

- **Patients:** register, sign in, choose a department, join its queue, see token number, people ahead and estimated wait, leave a waiting queue.
- **Staff:** sign in, choose a department, call the next patient, mark the current patient completed or absent, mark a waiting patient absent.
- **Public display:** shows the number being served and the waiting numbers. No names.
- **Safe token numbering:** every department numbers its patients from 1 each day, with no duplicates, even under simultaneous requests.
- **Role checks on the server:** staff pages and endpoints refuse patients (403) and anonymous users (401). Hiding buttons in the browser is not treated as security.

## Screenshots

*(Add these after you take them. Save them in `docs/screenshots/` and link them here.)*

- `docs/screenshots/login.png`: sign in and register
- `docs/screenshots/patient.png`: patient token card
- `docs/screenshots/staff.png`: staff dashboard
- `docs/screenshots/display.png`: public now-serving screen

## Technology

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.1 (Spring Web MVC, Spring Data JPA) |
| Database | MySQL 8 |
| Password hashing | BCrypt (`spring-security-crypto`) |
| Frontend | HTML5, CSS3, vanilla JavaScript (`fetch`) |
| Testing | JUnit 5, AssertJ, Spring MockMvc |
| Build | Maven |
| IDE | Eclipse |

## How it works

```
Browser (HTML, CSS, JavaScript)
        |  fetch() with session cookie
        v
Spring Boot controllers   (validate input, read identity from the session)
        |
        v
Services                  (queue rules, status changes, wait estimate)
        |
        v
Spring Data JPA repositories
        |
        v
MySQL                     (unique token constraint, row lock on the queue)
```

### Joining a queue, safely

When a patient joins, the service:

1. Locks the queue's row in the database (`PESSIMISTIC_WRITE`), so other joins for that queue wait.
2. Checks inside the lock that the patient has no active token in this queue today.
3. Gives the next number: today's highest number plus one.
4. Saves the token. A unique database constraint on `(queue, date, number)` is a second safety net.

The duplicate check is inside the lock on purpose. If it ran before the lock, two simultaneous requests from the same patient could both pass it.

### Estimated wait

`people ahead × average minutes per patient`, where *people ahead* counts the waiting tokens with a lower number, plus one if someone is being served. A patient being served is told "It's your turn".

## Database

Three tables, created automatically by Hibernate on first run.

- `users`: `id`, `name`, `email` (unique), `password_hash` (BCrypt), `role` (`PATIENT` or `STAFF`)
- `queues`: `id`, `name`, `average_service_minutes`, `active`
- `queue_tokens`: `id`, `queue_id`, `user_id`, `token_date`, `token_number`, `status`, `created_at`, `called_at`, `completed_at`, with the unique constraint `uk_queue_date_number (queue_id, token_date, token_number)`

Token statuses: `WAITING → CALLED → COMPLETED`, or `ABSENT`, or `CANCELLED` (patient left).

## API

| Method | Path | Who | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | Anyone | Create a patient account (always PATIENT) |
| POST | `/api/auth/login` | Anyone | Sign in, start a session |
| POST | `/api/auth/logout` | Signed in | End the session |
| GET | `/api/auth/me` | Signed in | Current user's id and role |
| GET | `/api/queues` | Anyone | Active departments |
| POST | `/api/queues/{id}/join` | Patient | Join a queue |
| GET | `/api/tokens/me` | Patient | My active tokens |
| POST | `/api/tokens/{id}/leave` | Patient (owner) | Leave a waiting queue |
| GET | `/api/staff/queues/{id}` | Staff | Now serving and waiting list |
| POST | `/api/staff/queues/{id}/call-next` | Staff | Call the next waiting patient |
| POST | `/api/staff/tokens/{id}/complete` | Staff | Complete the patient being served |
| POST | `/api/staff/tokens/{id}/absent` | Staff | Mark a waiting or called patient absent |
| GET | `/api/display/queues/{id}` | Anyone | Numbers only, for the public screen |

Errors come back as JSON, for example `{"error":"You already have an active token in this queue"}`, with status 409.

## Running it locally

### Prerequisites
- Java 21 (JDK)
- Maven (bundled with Eclipse)
- MySQL 8 running on `localhost:3306`
- Eclipse IDE (or any IDE that runs Spring Boot)

### 1. Create the databases and a user

Run in MySQL Workbench or the MySQL command line:

```sql
CREATE DATABASE queueless CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE queueless_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'queueless_user'@'localhost' IDENTIFIED BY 'choose-a-password';
GRANT ALL PRIVILEGES ON queueless.* TO 'queueless_user'@'localhost';
GRANT ALL PRIVILEGES ON queueless_test.* TO 'queueless_user'@'localhost';
FLUSH PRIVILEGES;
```

### 2. Add your database password (not committed)

Create `src/main/resources/application-local.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/queueless?allowPublicKeyRetrieval=true&sslMode=DISABLED
spring.datasource.username=queueless_user
spring.datasource.password=choose-a-password
```

This file is listed in `.gitignore`, so your password never reaches GitHub.

For the tests, create `src/test/resources/application-test-local.properties` the same way, with the database name `queueless_test`. It is also git-ignored.

### 3. Run the app

Run `QueuelessApplication` (Run As → Java Application). When it prints `Started QueuelessApplication`, open `http://localhost:8081/login.html`.

The first run creates the tables and seeds three departments (General Medicine, Pediatrics, Dental) and one staff account.

**Seeded staff login (local development only):** `staff1@queueless.local` / `Staff@123`. Change it before any real use.

### 4. Try it

1. Register as a patient on `login.html`.
2. Join a department from the department page.
3. Sign in as staff in a second browser, then call the patient.
4. Open `http://localhost:8081/display.html?queue=1` on a third screen.

## Running the tests

In Eclipse, right-click `src/test/java` → **Run As → JUnit Test**.

The suite runs against the real MySQL `queueless_test` database. It includes:
- Registration and password hashing checks
- Role checks (401 and 403)
- Join, duplicate join, leave, wait estimate and staff actions
- Daily token numbering with a movable clock
- A concurrency test: 50 different patients joining at the same moment, and 10 joins from one patient giving exactly one token

## Project structure

```
src/main/java/com/queueless/
  config/        Clock and password-encoder beans, seed data
  controller/    REST endpoints and error handling
  model/         JPA entities and enums
  repository/    Spring Data repositories (including the locking query)
  service/       Queue rules, token allocation, staff actions, sessions
src/main/resources/
  static/        login.html, index.html, patient.html, staff.html, display.html, style.css, app.js
  application.properties
src/test/java/com/queueless/
  AuthFlowTest, ApiFlowTest, DailyNumberingTest, ConcurrencyTest
```

## Known limitations

This is a learning project and a working prototype, not a production system.

- **Sessions are kept in memory.** Restarting the app signs everyone out.
- **Tokens are per day.** A patient still waiting at midnight does not carry over.
- **No CSRF protection, no login rate limiting, no password reset, no account lockout.**
- **No HTTPS.** Run only on your own computer.
- **The wait estimate uses a fixed average** per department, not real consultation times.
- **Live updates use polling** every 5 seconds, not WebSockets.
- **Test passwords are weak** (`secret123`, `Staff@123`). They are for local testing only.

## Future work

- JWT or database-backed session authentication
- A rolling-average wait estimate from `called_at` and `completed_at`
- WebSockets or server-sent events for live updates
- SMS or email notification when a patient's turn is near
- Multiple branches and staff permissions per department
- Deployment with HTTPS and a managed MySQL database

## Licence

Learning project. Add a licence here if you publish it for others to reuse.