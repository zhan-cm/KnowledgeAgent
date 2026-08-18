package com.zhan.conversation;

import com.zhan.common.ApiResponse;
import com.zhan.conversation.dto.CreateConversationRequest;
import com.zhan.conversation.dto.MessageDto;
import com.zhan.conversation.dto.SendMessageRequest;
import com.zhan.conversation.dto.SendMessageResponse;
import com.zhan.entity.Conversation;
import com.zhan.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    public ApiResponse<Conversation> create(@Valid @RequestBody CreateConversationRequest request,
                                            @AuthenticationPrincipal AuthUser user,
                                            HttpServletRequest httpRequest) {
        return ApiResponse.ok(conversationService.create(user.id(), request, httpRequest.getRemoteAddr()));
    }

    @GetMapping
    public ApiResponse<List<Conversation>> list(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(conversationService.listByUser(user.id()));
    }

    @PostMapping("/{conversationId}/messages")
    public ApiResponse<SendMessageResponse> sendMessage(@PathVariable Long conversationId,
                                                        @Valid @RequestBody SendMessageRequest request,
                                                        @AuthenticationPrincipal AuthUser user,
                                                        HttpServletRequest httpRequest) {
        return ApiResponse.ok(conversationService.sendMessage(
                conversationId, user.id(), request, httpRequest.getRemoteAddr()));
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<List<MessageDto>> listMessages(@PathVariable Long conversationId,
                                                      @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(conversationService.listMessages(conversationId, user.id()));
    }
}
