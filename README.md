# FlashQuiz AI

An AI-powered flashcard generator built with Spring Boot and Thymeleaf.

## Overview

This application allows users to enter a topic and generates flashcards using OpenRouter's AI API (GPT-3.5 Turbo). The flashcards are displayed with questions and answers that users can flip through.

## Tech Stack

- **Backend**: Java 17 with Spring Boot 3.1.4
- **Frontend**: Thymeleaf templates with HTML/CSS
- **Database**: PostgreSQL (Replit built-in)
- **AI Integration**: OpenRouter API

## Project Structure

```
src/
├── main/
│   ├── java/com/flashquiz/
│   │   ├── FlashquizApplication.java    # Main Spring Boot application
│   │   ├── controller/
│   │   │   └── FlashcardController.java # HTTP endpoints
│   │   ├── model/
│   │   │   └── Flashcard.java           # Data model
│   │   ├── repository/
│   │   │   └── FlashcardRepository.java # JPA repository
│   │   └── service/
│   │       └── FlashcardService.java    # AI flashcard generation logic
│   └── resources/
│       ├── application.properties       # Configuration
│       └── templates/
│           ├── index.html               # Home page
│           └── result.html              # Flashcard display
```

## Configuration

### Environment Variables

- `OPENROUTER_API_KEY` - Required for AI flashcard generation. Get one from https://openrouter.ai/

### Database

The application uses Replit's built-in PostgreSQL database. Connection is configured automatically using:
- `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`

## Running the Application

The application runs on port 5000 using Maven:
```bash
mvn spring-boot:run
```

## Deployment

Configured for autoscale deployment:
- Build: `mvn clean package -DskipTests`
- Run: `java -jar target/flashquiz-ai-0.0.1-SNAPSHOT.jar`

## Recent Changes

- 2026-01-28: Configured for Replit environment
  - Updated database connection to use Replit PostgreSQL
  - Set server port to 5000
  - Fixed OPENROUTER_API_KEY property configuration
  - Removed duplicate webflux dependency
