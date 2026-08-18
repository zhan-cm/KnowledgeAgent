package com.zhan.conversation.dto;

import com.zhan.ai.Citation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDto {

    private Long id;
    private String role;
    private String content;
    private List<Citation> citations;
    private LocalDateTime createdAt;
}
