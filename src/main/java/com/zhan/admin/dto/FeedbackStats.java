package com.zhan.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackStats {
    private long totalFeedback;
    private long upCount;
    private long downCount;
    private double downRate;
    private List<DownVotedAnswer> downVoted;
}
