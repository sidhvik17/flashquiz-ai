package com.flashquiz.service;

import com.flashquiz.model.Flashcard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;


@Service
public class FlashcardService {

    @Value("${OPENROUTER_API_KEY}")
    private String openRouterApiKey;

    private final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";

    public List<Flashcard> generateFlashcards(String inputText) {
        System.out.println("🔁 Called generateFlashcards()");
        System.out.println("Input Text: " + inputText);
        System.out.println("Using API key? " + (openRouterApiKey != null && !openRouterApiKey.isBlank()));

        List<Flashcard> flashcards = new ArrayList<>();

        try {
            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + openRouterApiKey);

            // Create request payload
            String requestBody = """
    {
       "model": "openai/gpt-3.5-turbo",
  "messages": [
    {"role": "system", "content": "You are a helpful flashcard generator."},
    {"role": "user", "content": "Generate 5 flashcards (Q: and A:) about the topic: %s"}
  ],
  "temperature": 0.7
}
""".formatted(inputText.replace("\"", "\\\""));



            System.out.println("📤 Request Body: " + requestBody);

            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            // Send request
            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.postForObject(OPENROUTER_URL, entity, String.class);

            System.out.println("⬅️ OpenRouter Response: " + response);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText();

            // Parse into flashcards
            String[] lines = content.split("\\n");
            String question = null;
            for (String line : lines) {
                if (line.trim().startsWith("Q:")) {
                    question = line.substring(2).trim();
                } else if (line.trim().startsWith("A:") && question != null) {
                    String answer = line.substring(2).trim();
                    flashcards.add(new Flashcard(question, answer));
                    question = null;
                }
            }

            System.out.println("✅ Parsed Flashcards: " + flashcards.size());

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
        }

        return flashcards;
    }
}
