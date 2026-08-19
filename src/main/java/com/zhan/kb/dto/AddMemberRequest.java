package com.zhan.kb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AddMemberRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "VIEWER|EDITOR", message = "角色只能是 VIEWER 或 EDITOR")
    private String role;
}
