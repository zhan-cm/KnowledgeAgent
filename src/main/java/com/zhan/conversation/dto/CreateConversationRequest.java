package com.zhan.conversation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateConversationRequest {

    @NotNull(message = "知识库 ID 不能为空")
    private Long kbId;

    @Size(max = 255, message = "标题最长 255 字符")
    private String title;
}
