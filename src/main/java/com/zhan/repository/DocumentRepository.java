package com.zhan.repository;

import com.zhan.entity.Document;
import com.zhan.entity.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByKbId(Long kbId);

    List<Document> findByKbIdOrderByCreatedAtDesc(Long kbId);

    List<Document> findAllByOrderByCreatedAtDesc();

    List<Document> findByKbIdInOrderByCreatedAtDesc(List<Long> kbIds);

    long countByStatus(DocumentStatus status);
}
