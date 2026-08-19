package com.zhan.repository;

import com.zhan.entity.KbMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KbMemberRepository extends JpaRepository<KbMember, Long> {

    List<KbMember> findByKbId(Long kbId);

    Optional<KbMember> findByKbIdAndUserId(Long kbId, Long userId);

    boolean existsByKbIdAndUserId(Long kbId, Long userId);

    void deleteByKbIdAndUserId(Long kbId, Long userId);
}
