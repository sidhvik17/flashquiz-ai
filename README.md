# FlashQuiz AI

[![CI](https://github.com/sidhvik17/flashquiz-ai/actions/workflows/ci.yml/badge.svg)](https://github.com/sidhvik17/flashquiz-ai/actions/workflows/ci.yml)

An AI-powered flashcard generator built with Spring Boot and Thymeleaf.

## Overview

Enter a topic and the application asks GPT-3.5 Turbo, through OpenRouter, for 15 question and answer cards. Each deck is saved in PostgreSQL. Asking for the same topic again returns the saved deck without calling the model, and **Regenerate** replaces it with a fresh one.

## Tech Stack

- **Backend**: Java 17 with Spring Boot 3.1.4
- **Frontend**: Thymeleaf templates with HTML/CSS
- **Database**: PostgreSQL through Spring Data JPA
- **AI Integration**: OpenRouter API
- **Build and delivery**: Maven wrapper, GitHub Actions, Docker

## How a Request Is Handled

1. The topic is trimmed, lower-cased and has its whitespace collapsed, then hashed with SHA-256. "Java Streams" and "  java   streams" are the same topic.
2. If a deck is stored under that hash it is rendered straight from the database.
3. Otherwise the model is called, its reply is parsed into question and answer pairs, and the deck is stored.

The model call has a 3 second connect timeout and a 30 second read timeout. When it fails, the form is shown again with what the user typed and a message:

| Failure | HTTP status |
|---|---|
| Topic is blank or longer than 2000 characters | 400 |
| `OPENROUTER_API_KEY` is not set | 503 |
| Model did not answer within the read timeout | 504 |
| Model returned an error status or could not be reached | 502 |
| Model reply contained no question and answer pairs | 502 |

If the database is unavailable, new decks are still generated; they are just not saved.

## Project Structure

```
src/
├── main/
│   ├── java/com/flashquiz/
│   │   ├── FlashquizApplication.java         # Main Spring Boot application
│   │   ├── config/
│   │   │   └── OpenRouterProperties.java     # openrouter.* settings
│   │   ├── controller/
│   │   │   └── FlashcardController.java      # HTTP endpoints, error pages
│   │   ├── model/
│   │   │   ├── Deck.java                     # Cards for one topic
│   │   │   └── Flashcard.java                # JPA entity
│   │   ├── repository/
│   │   │   └── FlashcardRepository.java      # Stored decks
│   │   └── service/
│   │       ├── FlashcardService.java         # Stored deck lookup, generation
│   │       ├── OpenRouterClient.java         # Model call, timeouts, failure reasons
│   │       ├── FlashcardParser.java          # Model reply to cards
│   │       └── FlashcardGenerationException.java
│   └── resources/
│       ├── application.properties            # Configuration
│       └── templates/
│           ├── index.html                    # Home page
│           └── result.html                   # Flashcard display
└── test/java/com/flashquiz/                  # Unit, slice and full-context tests
scripts/
├── smoke-test.sh                             # Packaged jar against a real database
└── fake_openrouter.py                        # Model stand-in for the smoke test
```

## Configuration

Everything is read from environment variables. No secret belongs in the repository.

| Variable | Default | Purpose |
|---|---|---|
| `OPENROUTER_API_KEY` | none | Required to generate cards. Get one from https://openrouter.ai/ |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `flashquiz` | Database name |
| `DB_USER` | `postgres` | Database user |
| `DB_PASSWORD` | empty | Database password |
| `PORT` | `8080` | HTTP port |
| `OPENROUTER_MODEL` | `openai/gpt-3.5-turbo` | Model id |
| `OPENROUTER_CONNECT_TIMEOUT` | `3s` | Connect timeout for the model call |
| `OPENROUTER_READ_TIMEOUT` | `30s` | Read timeout for the model call |

On Replit, set the `DB_*` variables from the built-in database's `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER` and `PGPASSWORD`.

Tables are created on startup (`spring.jpa.hibernate.ddl-auto=update`). Cards are stored in `flashcards`.

## Running the Application

With the variables above set and PostgreSQL reachable:

```bash
./mvnw spring-boot:run
```

Then open http://localhost:8080.

## Tests

```bash
./mvnw verify
```

The suite needs no database, network or API key. It covers:

- the reply parser
- the OpenRouter client against a local HTTP server: error statuses, malformed bodies and the read timeout
- the service on an in-memory database: stored deck hits, topic normalization, regenerate, and a database outage
- the controller: every status and page
- the whole application context, checking that a repeated topic does not reach the model

`scripts/smoke-test.sh` starts the packaged jar against a real PostgreSQL and a stand-in for OpenRouter, checks the same paths over HTTP, and prints how long saved decks take to serve:

```bash
DB_PASSWORD=... scripts/smoke-test.sh target/flashquiz-ai-0.0.1-SNAPSHOT.jar
```

It writes to the database it is pointed at, so use a disposable one.

## Docker

```bash
docker build -t flashquiz-ai .
docker run -p 8080:8080 \
  -e OPENROUTER_API_KEY=... \
  -e DB_HOST=host.docker.internal -e DB_PASSWORD=... \
  flashquiz-ai
```

## CI/CD

`.github/workflows/ci.yml` runs on every pull request and every push to `main`:

1. **Build and test**: `./mvnw verify`, then uploads the jar.
2. **Smoke test on PostgreSQL**: runs that jar against a PostgreSQL service container with `scripts/smoke-test.sh`. Timings are written to the job summary.
3. **Docker image**: builds the image. On `main` it is published to `ghcr.io/sidhvik17/flashquiz-ai`, tagged `latest` and with the commit SHA.

## Deployment

Replit autoscale deployment:
- Build: `mvn clean package -DskipTests`
- Run: `java -jar target/flashquiz-ai-0.0.1-SNAPSHOT.jar`

The deployment build skips tests. They run in CI.
