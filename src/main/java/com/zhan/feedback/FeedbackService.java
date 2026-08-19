package com.zhan.feedback;

import com.zhan.audit.AuditService;
import com.zhan.common.BusinessException;
import com.zhan.entity.Feedback;
import com.zhan.entity.Message;
import com.zhan.entity.MessageRole;
import com.zhan.feedback.dto.FeedbackRequest;
import com.zhan.repository.ConversationRepository;
import com.zhan.repository.FeedbackRepository;
import com.zhan.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final AuditService auditService;

    @Transactional
    public void submit(Long messageId, Long userId, FeedbackRequest request, String ip) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> BusinessException.notFound("消息不存在"));
        if (message.getRole() != MessageRole.ASSISTANT) {
            throw BusinessException.badRequest("只能对助手回答进行评价");
        }
        conversationRepository.findById(message.getConversationId())
                .filter(c -> c.getUserId().equals(userId))
                .orElseThrow(() -> BusinessException.forbidden("无权评价该消息"));

        Feedback feedback = feedbackRepository.findByMessageIdAndUserId(messageId, userId)
                .map(existing -> {
                    existing.setRating(request.getRating());
                    existing.setComment(StringUtils.hasText(request.getComment())
                            ? request.getComment().trim() : null);
                    return existing;
                })
                .orElseGet(() -> Feedback.builder()
                        .messageId(messageId)
                        .userId(userId)
                        .rating(request.getRating())
                        .comment(StringUtils.hasText(request.getComment())
                                ? request.getComment().trim() : null)
                        .build());
        feedbackRepository.save(feedback);
        auditService.log(userId, "FEEDBACK", "MESSAGE", messageId,
                "评价: " + request.getRating(), ip);
    }

    @Transactional
    public void remove(Long messageId, Long userId, String ip) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> BusinessException.notFound("消息不存在"));
        conversationRepository.findById(message.getConversationId())
                .filter(c -> c.getUserId().equals(userId))
                .orElseThrow(() -> BusinessException.forbidden("无权操作该消息"));
        feedbackRepository.deleteByMessageIdAndUserId(messageId, userId);
        auditService.log(userId, "FEEDBACK_REMOVE", "MESSAGE", messageId, "撤销评价", ip);
    }
}
