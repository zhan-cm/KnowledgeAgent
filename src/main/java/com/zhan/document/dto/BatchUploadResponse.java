package com.zhan.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchUploadResponse {
    private int total;
    private int successCount;
    private List<BatchUploadItem> items;
}
