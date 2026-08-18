package com.zhan.kb;

import com.zhan.common.ApiResponse;
import com.zhan.entity.KnowledgeBase;
import com.zhan.kb.dto.CreateKbRequest;
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
    public ApiResponse<List<KnowledgeBase>> list() {
        return ApiResponse.ok(kbService.list());
    }

    @PostMapping
    public ApiResponse<KnowledgeBase> create(@Valid @RequestBody CreateKbRequest request,
                                             @AuthenticationPrincipal AuthUser user,
                                             HttpServletRequest httpRequest) {
        return ApiResponse.ok(kbService.create(request, user.id(), httpRequest.getRemoteAddr()));
    }

    @GetMapping("/{kbId}")
    public ApiResponse<KnowledgeBase> get(@PathVariable Long kbId) {
        return ApiResponse.ok(kbService.get(kbId));
    }

    @DeleteMapping("/{kbId}")
    public ApiResponse<Void> delete(@PathVariable Long kbId,
                                    @AuthenticationPrincipal AuthUser user,
                                    HttpServletRequest httpRequest) {
        kbService.delete(kbId, user.id(), httpRequest.getRemoteAddr());
        return ApiResponse.ok();
    }
}
