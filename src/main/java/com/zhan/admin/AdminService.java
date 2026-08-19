package com.zhan.admin;

import com.zhan.admin.dto.AskTrendPoint;
import com.zhan.admin.dto.OverviewStats;
import com.zhan.entity.AuditLog;
import com.zhan.entity.DocumentStatus;
import com.zhan.repository.AuditLogRepository;
import com.zhan.repository.ConversationRepository;
import com.zhan.repository.DocumentRepository;
import com.zhan.repository.KnowledgeBaseRepository;
import com.zhan.repository.MessageRepository;
import com.zhan.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final KnowledgeBaseRepository kbRepository;
    private final DocumentRepository documentRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AuditLogRepository auditLogRepository;

    public OverviewStats overview() {
        return OverviewStats.builder()
                .userCount(userRepository.count())
                .kbCount(kbRepository.count())
                .documentCount(documentRepository.count())
                .indexedDocumentCount(documentRepository.countByStatus(DocumentStatus.INDEXED))
                .failedDocumentCount(documentRepository.countByStatus(DocumentStatus.FAILED))
                .conversationCount(conversationRepository.count())
                .messageCount(messageRepository.count())
                .askCountToday(auditLogRepository.countByActionAndCreatedAtAfter("ASK",
                        LocalDate.now().atStartOfDay()))
                .build();
    }

    public List<AskTrendPoint> askTrend() {
        Map<String, Long> byDay = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            byDay.put(LocalDate.now().minusDays(i).toString(), 0L);
        }
        for (Object[] row : auditLogRepository.countAskByDayLast7Days()) {
            byDay.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return byDay.entrySet().stream()
                .map(e -> AskTrendPoint.builder().day(e.getKey()).count(e.getValue()).build())
                .toList();
    }

    public Page<AuditLog> auditLogs(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return auditLogRepository.findAll(PageRequest.of(
                safePage, safeSize, Sort.by(Sort.Direction.DESC, "id")));
    }
}
