package com.zhan.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownVotedAnswer {
    private Long messageId;
    private String question;
    private String answer;
    private LocalDateTime createdAt;
}
