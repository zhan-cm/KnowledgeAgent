package com.zhan.ai;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class QueryResponse {

    private String answer;
    private List<Citation> citations;
}
