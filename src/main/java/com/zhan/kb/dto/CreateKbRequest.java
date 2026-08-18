package com.zhan.kb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateKbRequest {

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 100, message = "名称最长 100 字符")
    private String name;

    private String description;
}
