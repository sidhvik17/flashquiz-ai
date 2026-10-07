package com.flashquiz.service;

import com.flashquiz.model.Deck;
import com.flashquiz.model.Flashcard;
import com.flashquiz.repository.FlashcardRepository;
import com.flashquiz.service.FlashcardGenerationException.Reason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import(FlashcardService.class)
class FlashcardServiceTest {

    private static final String TWO_CARDS = "Q: q1\nA: a1\nQ: q2\nA: a2";
    private static final String THREE_CARDS = "Q: n1\nA: m1\nQ: n2\nA: m2\nQ: n3\nA: m3";

    @Autowired
    private FlashcardService service;

    @Autowired
    private FlashcardRepository repository;

    @MockBean
    private OpenRouterClient client;

    @Test
    void firstRequestAsksTheModelAndStoresTheDeck() {
        when(client.complete("Java streams")).thenReturn(TWO_CARDS);

        Deck deck = service.generateFlashcards("Java streams", false);

        assertThat(deck.cached()).isFalse();
        assertThat(deck.cards()).extracting(Flashcard::getQuestion).containsExactly("q1", "q2");
        assertThat(repository.findByTopicKeyOrderByIdAsc(FlashcardService.topicKey("Java streams"))).hasSize(2);
    }

    @Test
    void sameTopicIsServedFromStorageRegardlessOfCaseAndSpacing() {
        when(client.complete(anyString())).thenReturn(TWO_CARDS);
        service.generateFlashcards("Java streams", false);

        Deck deck = service.generateFlashcards("  java   STREAMS\n", false);

        assertThat(deck.cached()).isTrue();
        assertThat(deck.cards()).extracting(Flashcard::getQuestion, Flashcard::getAnswer)
                .containsExactly(tuple("q1", "a1"), tuple("q2", "a2"));
        verify(client, times(1)).complete(anyString());
    }

    @Test
    void differentTopicsDoNotShareADeck() {
        when(client.complete("java")).thenReturn(TWO_CARDS);
        when(client.complete("kotlin")).thenReturn(THREE_CARDS);
        service.generateFlashcards("java", false);

        Deck deck = service.generateFlashcards("kotlin", false);

        assertThat(deck.cached()).isFalse();
        assertThat(deck.cards()).hasSize(3);
    }

    @Test
    void refreshAsksTheModelAgainAndReplacesTheStoredDeck() {
        when(client.complete("java")).thenReturn(TWO_CARDS, THREE_CARDS);
        service.generateFlashcards("java", false);

        Deck refreshed = service.generateFlashcards("java", true);

        assertThat(refreshed.cached()).isFalse();
        assertThat(refreshed.cards()).hasSize(3);
        assertThat(repository.findByTopicKeyOrderByIdAsc(FlashcardService.topicKey("java")))
                .extracting(Flashcard::getQuestion).containsExactly("n1", "n2", "n3");
        verify(client, times(2)).complete("java");
    }

    @Test
    void replyWithoutCardsFailsAndStoresNothing() {
        when(client.complete("java")).thenReturn("I'm sorry, I can't help with that.");

        FlashcardGenerationException failure = catchThrowableOfType(
                () -> service.generateFlashcards("java", false), FlashcardGenerationException.class);

        assertThat(failure.getReason()).isEqualTo(Reason.BAD_RESPONSE);
        assertThat(repository.count()).isZero();
    }

    @Test
    void failedRefreshKeepsThePreviouslyStoredDeck() {
        when(client.complete("java"))
                .thenReturn(TWO_CARDS)
                .thenThrow(new FlashcardGenerationException(Reason.TIMEOUT, "slow"));
        service.generateFlashcards("java", false);

        FlashcardGenerationException failure = catchThrowableOfType(
                () -> service.generateFlashcards("java", true), FlashcardGenerationException.class);

        assertThat(failure.getReason()).isEqualTo(Reason.TIMEOUT);
        assertThat(service.generateFlashcards("java", false).cards()).hasSize(2);
    }
}
