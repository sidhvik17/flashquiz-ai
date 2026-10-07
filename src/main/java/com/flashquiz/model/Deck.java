package com.flashquiz.model;

import java.util.List;

public record Deck(String topic, List<Flashcard> cards, boolean cached) {
}
