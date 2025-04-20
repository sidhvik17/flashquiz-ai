package com.flashquiz.service;

import com.flashquiz.model.Flashcard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
public class FlashcardService {

    @Value("${HUGGINGFACE_API_KEY}")
    private String huggingfaceApiKey;

    public List<Flashcard> generateFlashcards(String inputText) {
        System.out.println("🔁 Called generateFlashcards()");
        System.out.println("Input Text: " + inputText);
        System.out.println("Using API key? " + (huggingfaceApiKey != null && !huggingfaceApiKey.isBlank()));

        // ✅ Update model URL
        WebClient webClient = WebClient.builder()
                .baseUrl("https://api-inference.huggingface.co/models/google/flan-t5-small")
                .defaultHeader("Authorization", "Bearer " + huggingfaceApiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        String prompt = "Generate question and answer pairs about " + inputText + ". Format each with:\nQ: ...\nA: ...";

        String requestBody = "{ \"inputs\": \"" + prompt.replace("\"", "\\\"").replace("\n", "\\n") + "\" }";

        String response = webClient.post()
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(ex -> {
                    System.err.println("❌ HuggingFace API error: " + ex.getMessage());
                    return Mono.just("[]");
                })
                .block();

        System.out.println("⬅️ HuggingFace Response: " + response);

        List<Flashcard> flashcards = new ArrayList<>();
        try {
            // fallback parsing from plain text
            String[] lines = response.split("\\\\n|\\n");
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
            System.err.println("❌ Parsing error: " + e.getMessage());
        }

        return flashcards;
    }
}
