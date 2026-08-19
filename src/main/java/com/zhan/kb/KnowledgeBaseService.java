package com.zhan.kb;

import com.zhan.audit.AuditService;
import com.zhan.common.BusinessException;
import com.zhan.document.FileStorageService;
import com.zhan.entity.Document;
import com.zhan.entity.KbMember;
import com.zhan.entity.KbMemberRole;
import com.zhan.entity.KnowledgeBase;
import com.zhan.entity.User;
import com.zhan.kb.dto.AddMemberRequest;
import com.zhan.kb.dto.CreateKbRequest;
import com.zhan.kb.dto.MemberDto;
import com.zhan.repository.DocumentRepository;
import com.zhan.repository.KbMemberRepository;
import com.zhan.repository.KnowledgeBaseRepository;
import com.zhan.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository kbRepository;
    private final KbMemberRepository kbMemberRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private final KbAccessService kbAccessService;

    public List<KnowledgeBase> list(Long userId) {
        return kbRepository.findAccessibleByUser(userId);
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

    public KnowledgeBase get(Long kbId, Long userId) {
        return kbAccessService.requireView(kbId, userId);
    }

    @Transactional
    public void delete(Long kbId, Long userId, String ip) {
        KnowledgeBase kb = kbAccessService.requireOwner(kbId, userId);
        List<Document> documents = documentRepository.findByKbId(kbId);
        documents.forEach(d -> fileStorageService.delete(d.getFilePath()));
        kbRepository.delete(kb);
        auditService.log(userId, "DELETE", "KB", kbId, "删除知识库: " + kb.getName(), ip);
    }

    public List<MemberDto> listMembers(Long kbId, Long userId) {
        kbAccessService.requireOwner(kbId, userId);
        return kbMemberRepository.findByKbId(kbId).stream()
                .map(m -> MemberDto.builder()
                        .userId(m.getUserId())
                        .username(findUsername(m.getUserId()))
                        .role(m.getRole().name())
                        .build())
                .toList();
    }

    @Transactional
    public MemberDto addMember(Long kbId, Long operatorId, AddMemberRequest request, String ip) {
        KnowledgeBase kb = kbAccessService.requireOwner(kbId, operatorId);
        User user = userRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> BusinessException.notFound("用户不存在: " + request.getUsername()));
        if (user.getId().equals(kb.getCreatedBy())) {
            throw BusinessException.badRequest("创建者默认是所有者，无需添加");
        }
        KbMember member = kbMemberRepository.findByKbIdAndUserId(kbId, user.getId())
                .map(existing -> {
                    existing.setRole(KbMemberRole.valueOf(request.getRole()));
                    return existing;
                })
                .orElseGet(() -> KbMember.builder()
                        .kbId(kbId)
                        .userId(user.getId())
                        .role(KbMemberRole.valueOf(request.getRole()))
                        .build());
        member = kbMemberRepository.save(member);
        auditService.log(operatorId, "ADD_MEMBER", "KB", kbId,
                "添加成员: " + user.getUsername() + "(" + request.getRole() + ")", ip);
        return MemberDto.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .role(member.getRole().name())
                .build();
    }

    @Transactional
    public void removeMember(Long kbId, Long operatorId, Long memberUserId, String ip) {
        kbAccessService.requireOwner(kbId, operatorId);
        kbMemberRepository.deleteByKbIdAndUserId(kbId, memberUserId);
        auditService.log(operatorId, "REMOVE_MEMBER", "KB", kbId,
                "移除成员 userId=" + memberUserId, ip);
    }

    private String findUsername(Long userId) {
        return userRepository.findById(userId).map(User::getUsername).orElse("用户#" + userId);
    }
}
