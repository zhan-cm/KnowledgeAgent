package com.zhan.ai;

import com.zhan.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Service
public class AiClient {

    private final RestClient restClient;

    public AiClient(RestClient.Builder builder, @Value("${app.ai.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(120));
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public QueryResponse query(QueryRequest request) {
        try {
            return restClient.post()
                    .uri("/query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.value() >= 400, (req, res) ->
                            new BusinessException(502, "AI 服务返回错误: HTTP " + res.getStatusCode().value()))
                    .body(QueryResponse.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(502, "AI 服务调用失败，请稍后重试");
        }
    }
}
