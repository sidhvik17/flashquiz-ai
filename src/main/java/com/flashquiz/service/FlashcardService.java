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

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api-inference.huggingface.co/models/mrm8488/t5-base-finetuned-question-generation-ap")
            .defaultHeader("Authorization", "Bearer " + huggingfaceApiKey)
            .defaultHeader("Content-Type", "application/json")
            .build();

    public List<Flashcard> generateFlashcards(String inputText) {
        List<Flashcard> flashcards = new ArrayList<>();

        String prompt = "generate questions: " + inputText;
        String requestBody = "{ \"inputs\": \"" + prompt.replace("\"", "\\\"") + "\" }";

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

        try {
            String text = response;
            String[] questions = text.split("\\?");

            for (String q : questions) {
                if (!q.trim().isEmpty()) {
                    flashcards.add(new Flashcard(q.trim() + "?", "You can provide an answer")); // default answer
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Parsing error: " + e.getMessage());
        }

        return flashcards;
    }

}
