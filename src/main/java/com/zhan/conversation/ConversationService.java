package com.zhan.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhan.ai.AiClient;
import com.zhan.ai.Citation;
import com.zhan.ai.HistoryMessage;
import com.zhan.ai.QueryRequest;
import com.zhan.ai.QueryResponse;
import com.zhan.audit.AuditService;
import com.zhan.common.ApiResponse;
import com.zhan.common.BusinessException;
import com.zhan.conversation.dto.CreateConversationRequest;
import com.zhan.conversation.dto.MessageDto;
import com.zhan.conversation.dto.SendMessageRequest;
import com.zhan.conversation.dto.SendMessageResponse;
import com.zhan.entity.Conversation;
import com.zhan.entity.Message;
import com.zhan.entity.MessageRole;
import com.zhan.repository.ConversationRepository;
import com.zhan.repository.KnowledgeBaseRepository;
import com.zhan.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final int HISTORY_LIMIT = 10;
    private static final int HISTORY_CONTENT_MAX_LENGTH = 2000;
    private static final long STREAM_TIMEOUT_MS = 180_000L;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final KnowledgeBaseRepository kbRepository;
    private final AiClient aiClient;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public Conversation create(Long userId, CreateConversationRequest request, String ip) {
        if (!kbRepository.existsById(request.getKbId())) {
            throw BusinessException.notFound("知识库不存在");
        }
        Conversation conversation = Conversation.builder()
                .userId(userId)
                .kbId(request.getKbId())
                .title(StringUtils.hasText(request.getTitle()) ? request.getTitle().trim() : "新对话")
                .build();
        conversation = conversationRepository.save(conversation);
        auditService.log(userId, "CREATE", "CONVERSATION", conversation.getId(), "创建对话", ip);
        return conversation;
    }

    public List<Conversation> listByUser(Long userId) {
        return conversationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public SendMessageResponse sendMessage(Long conversationId, Long userId, SendMessageRequest request, String ip) {
        Conversation conversation = getOwnedConversation(conversationId, userId);
        List<HistoryMessage> history = buildHistory(conversationId);

        messageRepository.save(Message.builder()
                .conversationId(conversationId)
                .role(MessageRole.USER)
                .content(request.getQuestion().trim())
                .build());

        QueryRequest aiRequest = buildQueryRequest(conversation, request, history);
        QueryResponse aiResponse;
        try {
            aiResponse = aiClient.query(aiRequest);
        } catch (BusinessException e) {
            messageRepository.save(Message.builder()
                    .conversationId(conversationId)
                    .role(MessageRole.ASSISTANT)
                    .content("AI 服务暂时不可用，请稍后重试。")
                    .build());
            throw e;
        }

        messageRepository.save(Message.builder()
                .conversationId(conversationId)
                .role(MessageRole.ASSISTANT)
                .content(aiResponse.getAnswer())
                .citations(toJson(aiResponse.getCitations()))
                .build());

        auditService.log(userId, "ASK", "CONVERSATION", conversationId,
                "提问: " + truncate(request.getQuestion(), 100), ip);
        return SendMessageResponse.builder()
                .answer(aiResponse.getAnswer())
                .citations(aiResponse.getCitations())
                .build();
    }

    public SseEmitter streamMessage(Long conversationId, Long userId, SendMessageRequest request, String ip) {
        Conversation conversation = getOwnedConversation(conversationId, userId);
        List<HistoryMessage> history = buildHistory(conversationId);

        messageRepository.save(Message.builder()
                .conversationId(conversationId)
                .role(MessageRole.USER)
                .content(request.getQuestion().trim())
                .build());

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        QueryRequest aiRequest = buildQueryRequest(conversation, request, history);

        CompletableFuture.runAsync(() -> {
            try {
                AiClient.StreamResult result = aiClient.streamQuery(aiRequest, delta -> {
                    try {
                        emitter.send(SseEmitter.event().name("delta")
                                .data(objectMapper.writeValueAsString(delta)));
                    } catch (IOException e) {
                        throw new IllegalStateException("SSE 发送失败", e);
                    }
                });

                messageRepository.save(Message.builder()
                        .conversationId(conversationId)
                        .role(MessageRole.ASSISTANT)
                        .content(result.answer())
                        .citations(toJson(result.citations()))
                        .build());
                auditService.log(userId, "ASK", "CONVERSATION", conversationId,
                        "提问(流式): " + truncate(request.getQuestion(), 100), ip);

                SendMessageResponse done = SendMessageResponse.builder()
                        .answer(result.answer())
                        .citations(result.citations())
                        .build();
                emitter.send(SseEmitter.event().name("done")
                        .data(objectMapper.writeValueAsString(done)));
                emitter.complete();
            } catch (Exception e) {
                log.error("流式问答失败 conversationId={}", conversationId, e);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(objectMapper.writeValueAsString(
                                    ApiResponse.error(502, "AI 服务调用失败，请稍后重试"))));
                } catch (Exception ignored) {
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    public List<MessageDto> listMessages(Long conversationId, Long userId) {
        getOwnedConversation(conversationId, userId);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(this::toDto)
                .toList();
    }

    private QueryRequest buildQueryRequest(Conversation conversation, SendMessageRequest request,
                                           List<HistoryMessage> history) {
        return QueryRequest.builder()
                .question(request.getQuestion().trim())
                .kbIds(conversation.getKbId() == null ? List.of() : List.of(conversation.getKbId()))
                .allowedDocumentIds(List.of())
                .topK(5)
                .conversationHistory(history)
                .build();
    }

    private List<HistoryMessage> buildHistory(Long conversationId) {
        List<Message> contextMessages = messageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .filter(this::isContextMessage)
                .toList();
        return contextMessages.stream()
                .skip(Math.max(0, contextMessages.size() - HISTORY_LIMIT))
                .map(m -> HistoryMessage.builder()
                        .role(m.getRole() == MessageRole.USER ? "user" : "assistant")
                        .content(truncate(m.getContent(), HISTORY_CONTENT_MAX_LENGTH))
                        .build())
                .toList();
    }

    private boolean isContextMessage(Message message) {
        return message.getRole() == MessageRole.USER || StringUtils.hasText(message.getCitations());
    }

    private Conversation getOwnedConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> BusinessException.notFound("对话不存在"));
        if (!conversation.getUserId().equals(userId)) {
            throw BusinessException.forbidden("无权访问该对话");
        }
        return conversation;
    }

    private MessageDto toDto(Message message) {
        return MessageDto.builder()
                .id(message.getId())
                .role(message.getRole().name())
                .content(message.getContent())
                .citations(parseCitations(message.getCitations()))
                .createdAt(message.getCreatedAt())
                .build();
    }

    private String toJson(List<Citation> citations) {
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (JsonProcessingException e) {
            log.warn("引用序列化失败", e);
            return "[]";
        }
    }

    private List<Citation> parseCitations(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Citation>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("引用反序列化失败: {}", json);
            return List.of();
        }
    }

    private String truncate(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
