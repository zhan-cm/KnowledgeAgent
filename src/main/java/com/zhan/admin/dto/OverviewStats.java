package com.zhan.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverviewStats {

    private long userCount;
    private long kbCount;
    private long documentCount;
    private long indexedDocumentCount;
    private long failedDocumentCount;
    private long conversationCount;
    private long messageCount;
    private long askCountToday;
}
