package com.zhan.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryRequest {

    private String question;
    private List<Long> kbIds;
    private List<Long> allowedDocumentIds;
    private Integer topK;
}
