package com.zhan.document;

import com.zhan.audit.AuditService;
import com.zhan.common.BusinessException;
import com.zhan.entity.Document;
import com.zhan.entity.DocumentStatus;
import com.zhan.kb.KbAccessService;
import com.zhan.repository.DocumentRepository;
import com.zhan.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final KnowledgeBaseRepository kbRepository;
    private final FileStorageService fileStorageService;
    private final IndexProducer indexProducer;
    private final AuditService auditService;
    private final KbAccessService kbAccessService;

    @Transactional
    public Document upload(MultipartFile file, Long kbId, Long userId, String ip) {
        kbAccessService.requireEdit(kbId, userId);
        if (file.isEmpty()) {
            throw BusinessException.badRequest("上传文件不能为空");
        }
        FileStorageService.StoredFile stored = fileStorageService.store(file);
        Document document = Document.builder()
                .kbId(kbId)
                .title(stored.originalName())
                .filePath(stored.relativePath())
                .fileType(stored.fileType())
                .status(DocumentStatus.PENDING)
                .version(1)
                .createdBy(userId)
                .build();
        document = documentRepository.save(document);
        indexProducer.sendIndexRequest(document.getId(), kbId,
                fileStorageService.resolve(document.getFilePath()).toString());
        auditService.log(userId, "UPLOAD", "DOCUMENT", document.getId(), "上传文档: " + document.getTitle(), ip);
        return document;
    }

    public Document get(Long docId, Long userId) {
        Document document = get(docId);
        kbAccessService.requireView(document.getKbId(), userId);
        return document;
    }

    public List<Document> listByKb(Long kbId, Long userId) {
        kbAccessService.requireView(kbId, userId);
        return documentRepository.findByKbIdOrderByCreatedAtDesc(kbId);
    }

    public List<Document> listAll(Long userId) {
        List<Long> accessibleKbIds = kbRepository.findAccessibleByUser(userId).stream()
                .map(kb -> kb.getId())
                .toList();
        if (accessibleKbIds.isEmpty()) {
            return List.of();
        }
        return documentRepository.findByKbIdInOrderByCreatedAtDesc(accessibleKbIds);
    }

    @Transactional
    public void delete(Long docId, Long userId, String ip) {
        Document document = get(docId);
        kbAccessService.requireEdit(document.getKbId(), userId);
        fileStorageService.delete(document.getFilePath());
        documentRepository.delete(document);
        auditService.log(userId, "DELETE", "DOCUMENT", docId, "删除文档: " + document.getTitle(), ip);
    }

    @Transactional
    public Document reindex(Long docId, Long userId, String ip) {
        Document document = get(docId);
        kbAccessService.requireEdit(document.getKbId(), userId);
        document.setStatus(DocumentStatus.PENDING);
        document.setVersion(document.getVersion() + 1);
        indexProducer.sendIndexRequest(document.getId(), document.getKbId(),
                fileStorageService.resolve(document.getFilePath()).toString());
        auditService.log(userId, "REINDEX", "DOCUMENT", docId, "重新索引: " + document.getTitle(), ip);
        return document;
    }

    @Transactional
    public void updateStatus(Long docId, String status, String error) {
        Document document = get(docId);
        document.setStatus(DocumentStatus.valueOf(status));
        if (error != null && !error.isBlank()) {
            log.warn("文档 {} 索引失败: {}", docId, error);
        }
    }

    public String preview(Long docId, Long userId) {
        Document document = get(docId, userId);
        if (!"txt".equalsIgnoreCase(document.getFileType())) {
            throw BusinessException.badRequest("仅支持预览 TXT 文件，PDF/Word 请下载原文件查看");
        }
        try {
            return Files.readString(fileStorageService.resolve(document.getFilePath()));
        } catch (IOException e) {
            throw new BusinessException(500, "读取文件失败: " + e.getMessage());
        }
    }

    private Document get(Long docId) {
        return documentRepository.findById(docId)
                .orElseThrow(() -> BusinessException.notFound("文档不存在"));
    }
}
