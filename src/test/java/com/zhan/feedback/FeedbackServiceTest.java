package com.zhan.feedback;

import com.zhan.audit.AuditService;
import com.zhan.common.BusinessException;
import com.zhan.entity.Conversation;
import com.zhan.entity.Message;
import com.zhan.entity.MessageRole;
import com.zhan.feedback.dto.FeedbackRequest;
import com.zhan.repository.ConversationRepository;
import com.zhan.repository.FeedbackRepository;
import com.zhan.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private AuditService auditService;

    private FeedbackService feedbackService;

    @BeforeEach
    void setUp() {
        feedbackService = new FeedbackService(
                feedbackRepository, messageRepository, conversationRepository, auditService);
    }

    private FeedbackRequest request(String rating) {
        FeedbackRequest request = new FeedbackRequest();
        request.setRating(rating);
        return request;
    }

    private Message assistantMessage() {
        return Message.builder().id(10L).conversationId(1L).role(MessageRole.ASSISTANT).build();
    }

    @Test
    void submitRejectsUserMessage() {
        when(messageRepository.findById(10L)).thenReturn(Optional.of(
                Message.builder().id(10L).conversationId(1L).role(MessageRole.USER).build()));

        assertThatThrownBy(() -> feedbackService.submit(10L, 2L, request("UP"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("助手");
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void submitRejectsOtherUsersMessage() {
        when(messageRepository.findById(10L)).thenReturn(Optional.of(assistantMessage()));
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(
                Conversation.builder().id(1L).userId(99L).build()));

        assertThatThrownBy(() -> feedbackService.submit(10L, 2L, request("UP"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权");
    }

    @Test
    void removeRejectsOtherUsersMessage() {
        when(messageRepository.findById(10L)).thenReturn(Optional.of(assistantMessage()));
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(
                Conversation.builder().id(1L).userId(99L).build()));

        assertThatThrownBy(() -> feedbackService.remove(10L, 2L, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权");
        verify(feedbackRepository, never()).deleteByMessageIdAndUserId(any(), any());
    }
}
