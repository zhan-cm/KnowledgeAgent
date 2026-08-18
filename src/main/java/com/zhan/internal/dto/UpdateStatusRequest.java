package com.zhan.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateStatusRequest {

    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "INDEXED|FAILED", message = "状态只能是 INDEXED 或 FAILED")
    private String status;

    @Size(max = 1000, message = "错误信息过长")
    private String error;
}
