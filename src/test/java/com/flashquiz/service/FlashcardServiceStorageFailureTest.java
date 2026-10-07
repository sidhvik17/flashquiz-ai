package com.flashquiz.service;

import com.flashquiz.model.Deck;
import com.flashquiz.repository.FlashcardRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The stored deck is an optimization; losing the database must not fail a request the model can answer. */
class FlashcardServiceStorageFailureTest {

    private final FlashcardRepository repository = mock(FlashcardRepository.class);
    private final OpenRouterClient client = mock(OpenRouterClient.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final FlashcardService service =
            new FlashcardService(repository, client, new TransactionTemplate(transactionManager));

    @Test
    void servesFromTheModelWhenTheDatabaseIsDown() {
        when(repository.findByTopicKeyOrderByIdAsc(anyString()))
                .thenThrow(new DataAccessResourceFailureException("connection refused"));
        when(transactionManager.getTransaction(any()))
                .thenThrow(new CannotCreateTransactionException("connection refused"));
        when(client.complete("java")).thenReturn("Q: q1\nA: a1");

        Deck deck = service.generateFlashcards("java", false);

        assertThat(deck.cached()).isFalse();
        assertThat(deck.cards()).hasSize(1);
    }
}
