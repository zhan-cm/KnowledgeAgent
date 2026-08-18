package com.zhan.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhan.ai.AiClient;
import com.zhan.ai.Citation;
import com.zhan.ai.QueryRequest;
import com.zhan.ai.QueryResponse;
import com.zhan.audit.AuditService;
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

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

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

        messageRepository.save(Message.builder()
                .conversationId(conversationId)
                .role(MessageRole.USER)
                .content(request.getQuestion().trim())
                .build());

        QueryRequest aiRequest = QueryRequest.builder()
                .question(request.getQuestion().trim())
                .kbIds(conversation.getKbId() == null ? List.of() : List.of(conversation.getKbId()))
                .allowedDocumentIds(List.of())
                .topK(5)
                .build();
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

    public List<MessageDto> listMessages(Long conversationId, Long userId) {
        getOwnedConversation(conversationId, userId);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(this::toDto)
                .toList();
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
