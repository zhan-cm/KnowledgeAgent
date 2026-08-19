package com.zhan.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FeedbackRequest {

    @NotBlank(message = "评价不能为空")
    @Pattern(regexp = "UP|DOWN", message = "评价只能是 UP 或 DOWN")
    private String rating;

    @Size(max = 500, message = "备注最长 500 字符")
    private String comment;
}
