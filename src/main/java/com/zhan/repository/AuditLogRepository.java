package com.zhan.repository;

import com.zhan.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    long countByActionAndCreatedAtAfter(String action, LocalDateTime after);

    @Query(value = "SELECT to_char(created_at, 'YYYY-MM-DD') AS day, count(*) " +
            "FROM audit_logs WHERE action = 'ASK' AND created_at >= CURRENT_DATE - 6 " +
            "GROUP BY day ORDER BY day", nativeQuery = true)
    List<Object[]> countAskByDayLast7Days();
}

