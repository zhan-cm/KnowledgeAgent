package com.zhan.internal;

import com.zhan.common.ApiResponse;
import com.zhan.common.BusinessException;
import com.zhan.document.DocumentService;
import com.zhan.internal.dto.UpdateStatusRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    private final DocumentService documentService;

    @Value("${app.internal-token}")
    private String internalToken;

    @PutMapping("/documents/{docId}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long docId,
                                          @RequestHeader(value = "X-Internal-Token", required = false) String token,
                                          @Valid @RequestBody UpdateStatusRequest request) {
        if (!internalToken.equals(token)) {
            throw BusinessException.unauthorized("内部 token 无效");
        }
        documentService.updateStatus(docId, request.getStatus(), request.getError());
        return ApiResponse.ok();
    }
}
