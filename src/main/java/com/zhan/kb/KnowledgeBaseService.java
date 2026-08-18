package com.zhan.kb;

import com.zhan.audit.AuditService;
import com.zhan.common.BusinessException;
import com.zhan.document.FileStorageService;
import com.zhan.entity.Document;
import com.zhan.entity.KnowledgeBase;
import com.zhan.kb.dto.CreateKbRequest;
import com.zhan.repository.DocumentRepository;
import com.zhan.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository kbRepository;
    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    public List<KnowledgeBase> list() {
        return kbRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public KnowledgeBase create(CreateKbRequest request, Long userId, String ip) {
        KnowledgeBase kb = KnowledgeBase.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .createdBy(userId)
                .build();
        kb = kbRepository.save(kb);
        auditService.log(userId, "CREATE", "KB", kb.getId(), "创建知识库: " + kb.getName(), ip);
        return kb;
    }

    public KnowledgeBase get(Long kbId) {
        return kbRepository.findById(kbId)
                .orElseThrow(() -> BusinessException.notFound("知识库不存在"));
    }

    @Transactional
    public void delete(Long kbId, Long userId, String ip) {
        KnowledgeBase kb = get(kbId);
        List<Document> documents = documentRepository.findByKbId(kbId);
        documents.forEach(d -> fileStorageService.delete(d.getFilePath()));
        kbRepository.delete(kb);
        auditService.log(userId, "DELETE", "KB", kbId, "删除知识库: " + kb.getName(), ip);
    }
}
