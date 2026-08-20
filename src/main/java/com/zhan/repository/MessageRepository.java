package com.zhan.repository;

import com.zhan.entity.Message;
import com.zhan.entity.MessageRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    Message findTopByConversationIdAndRoleAndCreatedAtBeforeOrderByCreatedAtDesc(
            Long conversationId, MessageRole role, LocalDateTime before);
}
