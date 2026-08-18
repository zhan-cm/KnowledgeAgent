package com.zhan.audit;

import com.zhan.entity.AuditLog;
import com.zhan.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void log(Long userId, String action, String resourceType, Long resourceId, String detail, String ip) {
        auditLogRepository.save(AuditLog.builder()
                .userId(userId)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .detail(detail)
                .ip(ip)
                .build());
    }
}
