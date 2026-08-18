package com.zhan.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhan.ai.AiClient;
import com.zhan.ai.Citation;
import com.zhan.ai.QueryRequest;
import com.zhan.ai.QueryResponse;
import com.zhan.audit.AuditService;
import com.zhan.common.BusinessException;
import com.zhan.conversation.dto.SendMessageRequest;
import com.zhan.entity.Conversation;
import com.zhan.entity.Message;
import com.zhan.entity.MessageRole;
import com.zhan.repository.ConversationRepository;
import com.zhan.repository.KnowledgeBaseRepository;
import com.zhan.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private KnowledgeBaseRepository kbRepository;
    @Mock
    private AiClient aiClient;
    @Mock
    private AuditService auditService;

    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(
                conversationRepository, messageRepository, kbRepository,
                aiClient, auditService, new ObjectMapper());
    }

    private Conversation conversation(Long userId) {
        return Conversation.builder().id(1L).userId(userId).kbId(1L).build();
    }

    private SendMessageRequest request() {
        SendMessageRequest request = new SendMessageRequest();
        request.setQuestion("差旅报销的住宿标准是多少？");
        return request;
    }

    @Test
    void sendMessageSavesQuestionAnswerAndHistory() {
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation(2L)));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(
                Message.builder().id(1L).conversationId(1L).role(MessageRole.USER).content("上一问").build(),
                Message.builder().id(2L).conversationId(1L).role(MessageRole.ASSISTANT).content("错误回复").build(),
                Message.builder().id(3L).conversationId(1L).role(MessageRole.ASSISTANT)
                        .content("上一答").citations("[{\"documentId\":2}]").build()));
        when(aiClient.query(any(QueryRequest.class))).thenAnswer(inv -> {
            QueryResponse response = new QueryResponse();
            response.setAnswer("答案");
            response.setCitations(List.of(Citation.builder().documentId(2L).title("t").build()));
            return response;
        });
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        conversationService.sendMessage(1L, 2L, request(), "127.0.0.1");

        ArgumentCaptor<QueryRequest> requestCaptor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(aiClient).query(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getConversationHistory())
                .extracting(h -> h.getRole() + ":" + h.getContent())
                .containsExactly("user:上一问", "assistant:上一答");

        verify(messageRepository, times(2)).save(any(Message.class));
        verify(auditService).log(eq(2L), eq("ASK"), eq("CONVERSATION"), eq(1L), any(), any());
    }

    @Test
    void sendMessageSavesErrorReplyWhenAiFails() {
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation(2L)));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(aiClient.query(any(QueryRequest.class))).thenThrow(new BusinessException(502, "AI 服务调用失败"));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> conversationService.sendMessage(1L, 2L, request(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class);

        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(m -> m.getRole() + ":" + m.getContent())
                .contains("USER:差旅报销的住宿标准是多少？", "ASSISTANT:AI 服务暂时不可用，请稍后重试。");
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void sendMessageRejectsOtherUsersConversation() {
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation(99L)));

        assertThatThrownBy(() -> conversationService.sendMessage(1L, 2L, request(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问");
    }
}
