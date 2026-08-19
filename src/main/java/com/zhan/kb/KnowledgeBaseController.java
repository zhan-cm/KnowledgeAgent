package com.zhan.kb;

import com.zhan.common.ApiResponse;
import com.zhan.entity.KnowledgeBase;
import com.zhan.kb.dto.AddMemberRequest;
import com.zhan.kb.dto.CreateKbRequest;
import com.zhan.kb.dto.MemberDto;
import com.zhan.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kbs")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;

    @GetMapping
    public ApiResponse<List<KnowledgeBase>> list(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(kbService.list(user.id()));
    }

    @PostMapping
    public ApiResponse<KnowledgeBase> create(@Valid @RequestBody CreateKbRequest request,
                                             @AuthenticationPrincipal AuthUser user,
                                             HttpServletRequest httpRequest) {
        return ApiResponse.ok(kbService.create(request, user.id(), httpRequest.getRemoteAddr()));
    }

    @GetMapping("/{kbId}")
    public ApiResponse<KnowledgeBase> get(@PathVariable Long kbId,
                                          @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(kbService.get(kbId, user.id()));
    }

    @DeleteMapping("/{kbId}")
    public ApiResponse<Void> delete(@PathVariable Long kbId,
                                    @AuthenticationPrincipal AuthUser user,
                                    HttpServletRequest httpRequest) {
        kbService.delete(kbId, user.id(), httpRequest.getRemoteAddr());
        return ApiResponse.ok();
    }

    @GetMapping("/{kbId}/members")
    public ApiResponse<List<MemberDto>> listMembers(@PathVariable Long kbId,
                                                    @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(kbService.listMembers(kbId, user.id()));
    }

    @PostMapping("/{kbId}/members")
    public ApiResponse<MemberDto> addMember(@PathVariable Long kbId,
                                            @Valid @RequestBody AddMemberRequest request,
                                            @AuthenticationPrincipal AuthUser user,
                                            HttpServletRequest httpRequest) {
        return ApiResponse.ok(kbService.addMember(kbId, user.id(), request, httpRequest.getRemoteAddr()));
    }

    @DeleteMapping("/{kbId}/members/{memberUserId}")
    public ApiResponse<Void> removeMember(@PathVariable Long kbId,
                                          @PathVariable Long memberUserId,
                                          @AuthenticationPrincipal AuthUser user,
                                          HttpServletRequest httpRequest) {
        kbService.removeMember(kbId, user.id(), memberUserId, httpRequest.getRemoteAddr());
        return ApiResponse.ok();
    }
}
