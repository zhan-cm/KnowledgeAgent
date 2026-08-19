package com.zhan.repository;

import com.zhan.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    List<KnowledgeBase> findAllByOrderByCreatedAtDesc();

    @Query("""
            SELECT DISTINCT kb FROM KnowledgeBase kb
            LEFT JOIN KbMember m ON m.kbId = kb.id
            WHERE kb.createdBy = :userId OR m.userId = :userId
            ORDER BY kb.createdAt DESC
            """)
    List<KnowledgeBase> findAccessibleByUser(@Param("userId") Long userId);
}
