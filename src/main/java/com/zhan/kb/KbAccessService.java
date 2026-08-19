package com.zhan.kb;

import com.zhan.common.BusinessException;
import com.zhan.entity.KbMember;
import com.zhan.entity.KbMemberRole;
import com.zhan.entity.KnowledgeBase;
import com.zhan.repository.KbMemberRepository;
import com.zhan.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KbAccessService {

    private final KnowledgeBaseRepository kbRepository;
    private final KbMemberRepository kbMemberRepository;

    public boolean isOwner(KnowledgeBase kb, Long userId) {
        return kb.getCreatedBy() != null && kb.getCreatedBy().equals(userId);
    }

    public boolean hasAccess(Long kbId, Long userId) {
        KnowledgeBase kb = getKb(kbId);
        return isOwner(kb, userId) || kbMemberRepository.existsByKbIdAndUserId(kbId, userId);
    }

    public boolean hasEditAccess(Long kbId, Long userId) {
        KnowledgeBase kb = getKb(kbId);
        if (isOwner(kb, userId)) {
            return true;
        }
        return kbMemberRepository.findByKbIdAndUserId(kbId, userId)
                .map(m -> m.getRole() == KbMemberRole.EDITOR)
                .orElse(false);
    }

    public KnowledgeBase requireView(Long kbId, Long userId) {
        if (!hasAccess(kbId, userId)) {
            throw BusinessException.forbidden("无权访问该知识库");
        }
        return getKb(kbId);
    }

    public KnowledgeBase requireEdit(Long kbId, Long userId) {
        if (!hasEditAccess(kbId, userId)) {
            throw BusinessException.forbidden("无权修改该知识库（需要所有者或编辑权限）");
        }
        return getKb(kbId);
    }

    public KnowledgeBase requireOwner(Long kbId, Long userId) {
        KnowledgeBase kb = getKb(kbId);
        if (!isOwner(kb, userId)) {
            throw BusinessException.forbidden("仅知识库所有者可执行该操作");
        }
        return kb;
    }

    public KnowledgeBase getKb(Long kbId) {
        return kbRepository.findById(kbId)
                .orElseThrow(() -> BusinessException.notFound("知识库不存在"));
    }
}
