package com.flashquiz.repository;

import com.flashquiz.model.Flashcard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {

    List<Flashcard> findByTopicKeyOrderByIdAsc(String topicKey);

    @Modifying
    @Query("delete from Flashcard f where f.topicKey = :topicKey")
    void deleteByTopicKey(@Param("topicKey") String topicKey);
}
