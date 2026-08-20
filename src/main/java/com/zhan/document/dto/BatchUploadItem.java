package com.zhan.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchUploadItem {
    private String filename;
    private Long documentId;
    private boolean success;
    private String error;
}
