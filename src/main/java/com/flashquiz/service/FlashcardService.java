package com.flashquiz.service;

import com.flashquiz.model.Deck;
import com.flashquiz.model.Flashcard;
import com.flashquiz.repository.FlashcardRepository;
import com.flashquiz.service.FlashcardGenerationException.Reason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class FlashcardService {

    private static final Logger log = LoggerFactory.getLogger(FlashcardService.class);

    private final FlashcardRepository repository;
    private final OpenRouterClient client;
    private final TransactionTemplate transaction;

    public FlashcardService(FlashcardRepository repository, OpenRouterClient client, TransactionTemplate transaction) {
        this.repository = repository;
        this.client = client;
        this.transaction = transaction;
    }

    /**
     * Returns the stored deck for the topic, or asks the model and stores the result.
     * With {@code refresh} the stored deck is ignored and replaced.
     */
    public Deck generateFlashcards(String topic, boolean refresh) {
        long start = System.nanoTime();
        String key = topicKey(topic);

        if (!refresh) {
            List<Flashcard> stored = findStored(key);
            if (!stored.isEmpty()) {
                logServed("cache", key, stored, start);
                return new Deck(topic, stored, true);
            }
        }

        List<Flashcard> cards = FlashcardParser.parse(client.complete(topic));
        if (cards.isEmpty()) {
            throw new FlashcardGenerationException(Reason.BAD_RESPONSE, "Model reply contained no Q:/A: pairs");
        }
        store(key, cards);
        logServed("llm", key, cards, start);
        return new Deck(topic, cards, false);
    }

    static String topicKey(String topic) {
        String normalized = topic.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // The stored deck is an optimization: if the database is unavailable the request
    // still succeeds through the model, it just is not served or saved from storage.
    private List<Flashcard> findStored(String key) {
        try {
            return repository.findByTopicKeyOrderByIdAsc(key);
        } catch (DataAccessException | TransactionException e) {
            log.warn("Stored deck lookup failed, falling back to the model", e);
            return List.of();
        }
    }

    private void store(String key, List<Flashcard> cards) {
        cards.forEach(card -> card.setTopicKey(key));
        try {
            transaction.executeWithoutResult(status -> {
                repository.deleteByTopicKey(key);
                repository.saveAll(cards);
            });
        } catch (DataAccessException | TransactionException e) {
            log.warn("Could not store deck, serving it unsaved", e);
        }
    }

    private static void logServed(String source, String key, List<Flashcard> cards, long startNanos) {
        log.info("Served deck source={} topicKey={} cards={} elapsedMs={}",
                source, key.substring(0, 12), cards.size(), (System.nanoTime() - startNanos) / 1_000_000);
    }
}
