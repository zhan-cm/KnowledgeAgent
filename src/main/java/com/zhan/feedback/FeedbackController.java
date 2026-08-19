package com.zhan.feedback;

import com.zhan.common.ApiResponse;
import com.zhan.feedback.dto.FeedbackRequest;
import com.zhan.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/{messageId}/feedback")
    public ApiResponse<Void> submit(@PathVariable Long messageId,
                                    @Valid @RequestBody FeedbackRequest request,
                                    @AuthenticationPrincipal AuthUser user,
                                    HttpServletRequest httpRequest) {
        feedbackService.submit(messageId, user.id(), request, httpRequest.getRemoteAddr());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{messageId}/feedback")
    public ApiResponse<Void> remove(@PathVariable Long messageId,
                                    @AuthenticationPrincipal AuthUser user,
                                    HttpServletRequest httpRequest) {
        feedbackService.remove(messageId, user.id(), httpRequest.getRemoteAddr());
        return ApiResponse.ok();
    }
}
