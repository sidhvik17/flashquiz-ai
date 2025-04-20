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


@Service
public class FlashcardService {

    @Value("${OPENAI_API_KEY}")
    private String openaiApiKey;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.openai.com/v1/chat/completions")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openaiApiKey)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .build();

    public List<Flashcard> generateFlashcards(String inputText) {
        List<Flashcard> flashcards = new ArrayList<>();
        System.out.println("🔁 Called generateFlashcards()");
        System.out.println("Input Text: " + inputText);
        System.out.println("Using API key? " + (openaiApiKey != null && !openaiApiKey.isBlank()));

        String requestBody = """
{
  "model": "gpt-3.5-turbo",
  "messages": [
    {
      "role": "user",
      "content": "Generate 5 flashcard Q&A pairs for: Newton's Laws of Motion. Format: Q: ...\\nA: ..."
    }
  ],
  "temperature": 0.7
}
""";

        String response = webClient.post()
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(ex -> {
                    System.err.println("❌ OpenAI API error: " + ex.getMessage());
                    return Mono.just("{}");
                })
                .block();

        System.out.println("⬅️ OpenAI Response: " + response);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText();

            String[] lines = content.split("\\n");
            String question = null;
            for (String line : lines) {
                if (line.startsWith("Q:")) {
                    question = line.substring(2).trim();
                } else if (line.startsWith("A:") && question != null) {
                    String answer = line.substring(2).trim();
                    flashcards.add(new Flashcard(question, answer));
                    question = null;
                }
            }

            System.out.println("✅ Flashcards Parsed: " + flashcards.size());
        } catch (Exception e) {
            System.err.println("❌ JSON parsing failed: " + e.getMessage());
        }

        return flashcards;
    }
}
