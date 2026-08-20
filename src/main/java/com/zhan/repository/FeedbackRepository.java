package com.zhan.repository;

import com.zhan.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    Optional<Feedback> findByMessageIdAndUserId(Long messageId, Long userId);

    void deleteByMessageIdAndUserId(Long messageId, Long userId);

    long countByRating(String rating);

    List<Feedback> findTop20ByRatingOrderByCreatedAtDesc(String rating);
}
