package com.zhan.document;

import com.zhan.common.ApiResponse;
import com.zhan.entity.Document;
import com.zhan.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    public ApiResponse<Document> upload(@RequestParam("file") MultipartFile file,
                                        @RequestParam("kbId") Long kbId,
                                        @AuthenticationPrincipal AuthUser user,
                                        HttpServletRequest httpRequest) {
        return ApiResponse.ok(documentService.upload(file, kbId, user.id(), httpRequest.getRemoteAddr()));
    }

    @GetMapping
    public ApiResponse<List<Document>> list(@RequestParam(required = false) Long kbId,
                                            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(kbId == null
                ? documentService.listAll(user.id())
                : documentService.listByKb(kbId, user.id()));
    }

    @GetMapping("/{docId}")
    public ApiResponse<Document> get(@PathVariable Long docId,
                                     @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(documentService.get(docId, user.id()));
    }

    @DeleteMapping("/{docId}")
    public ApiResponse<Void> delete(@PathVariable Long docId,
                                    @AuthenticationPrincipal AuthUser user,
                                    HttpServletRequest httpRequest) {
        documentService.delete(docId, user.id(), httpRequest.getRemoteAddr());
        return ApiResponse.ok();
    }

    @GetMapping("/{docId}/preview")
    public ApiResponse<String> preview(@PathVariable Long docId,
                                       @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(documentService.preview(docId, user.id()));
    }

    @PostMapping("/{docId}/reindex")
    public ApiResponse<Document> reindex(@PathVariable Long docId,
                                         @AuthenticationPrincipal AuthUser user,
                                         HttpServletRequest httpRequest) {
        return ApiResponse.ok(documentService.reindex(docId, user.id(), httpRequest.getRemoteAddr()));
    }
}
