package com.zhan.kb;

import com.zhan.common.BusinessException;
import com.zhan.entity.KbMember;
import com.zhan.entity.KbMemberRole;
import com.zhan.entity.KnowledgeBase;
import com.zhan.repository.KbMemberRepository;
import com.zhan.repository.KnowledgeBaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KbAccessServiceTest {

    @Mock
    private KnowledgeBaseRepository kbRepository;
    @Mock
    private KbMemberRepository kbMemberRepository;

    @InjectMocks
    private KbAccessService accessService;

    @Test
    void ownerHasAccessAndEdit() {
        KnowledgeBase kb = KnowledgeBase.builder().id(1L).createdBy(2L).build();
        when(kbRepository.findById(1L)).thenReturn(Optional.of(kb));

        assertThat(accessService.hasAccess(1L, 2L)).isTrue();
        assertThat(accessService.hasEditAccess(1L, 2L)).isTrue();
    }

    @Test
    void viewerHasAccessButNoEdit() {
        KnowledgeBase kb = KnowledgeBase.builder().id(1L).createdBy(99L).build();
        when(kbRepository.findById(1L)).thenReturn(Optional.of(kb));
        when(kbMemberRepository.existsByKbIdAndUserId(1L, 2L)).thenReturn(true);
        when(kbMemberRepository.findByKbIdAndUserId(1L, 2L)).thenReturn(Optional.of(
                KbMember.builder().kbId(1L).userId(2L).role(KbMemberRole.VIEWER).build()));

        assertThat(accessService.hasAccess(1L, 2L)).isTrue();
        assertThat(accessService.hasEditAccess(1L, 2L)).isFalse();
    }

    @Test
    void editorHasEditAccess() {
        KnowledgeBase kb = KnowledgeBase.builder().id(1L).createdBy(99L).build();
        when(kbRepository.findById(1L)).thenReturn(Optional.of(kb));
        when(kbMemberRepository.findByKbIdAndUserId(1L, 2L)).thenReturn(Optional.of(
                KbMember.builder().kbId(1L).userId(2L).role(KbMemberRole.EDITOR).build()));

        assertThat(accessService.hasEditAccess(1L, 2L)).isTrue();
    }

    @Test
    void strangerIsDenied() {
        KnowledgeBase kb = KnowledgeBase.builder().id(1L).createdBy(99L).build();
        when(kbRepository.findById(1L)).thenReturn(Optional.of(kb));
        when(kbMemberRepository.existsByKbIdAndUserId(1L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> accessService.requireView(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权");
    }
}
