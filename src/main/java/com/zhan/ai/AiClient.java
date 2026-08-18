package com.zhan.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhan.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Service
public class AiClient {

    private final RestClient restClient;
    private final HttpClient streamHttpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public AiClient(RestClient.Builder builder, @Value("${app.ai.base-url}") String baseUrl,
                    ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(120));
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        this.streamHttpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
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

    public StreamResult streamQuery(QueryRequest request, Consumer<String> onDelta) {
        HttpRequest httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/query/stream"))
                    .timeout(Duration.ofMinutes(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();
        } catch (Exception e) {
            throw new BusinessException(502, "AI 服务调用失败，请稍后重试");
        }
        try {
            HttpResponse<java.util.stream.Stream<String>> response = streamHttpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() >= 400) {
                String errorBody = response.body().reduce("", (a, b) -> a + b);
                throw new BusinessException(502, "AI 服务返回错误: HTTP " + response.statusCode()
                        + " " + (errorBody.length() > 500 ? errorBody.substring(0, 500) : errorBody));
            }
            StringBuilder answer = new StringBuilder();
            List<Citation> citations = new ArrayList<>();
            response.body().forEach(line -> handleSseLine(line, answer, citations, onDelta));
            return new StreamResult(answer.toString(), citations);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(502, "AI 服务调用失败，请稍后重试");
        }
    }

    private void handleSseLine(String line, StringBuilder answer, List<Citation> citations,
                               Consumer<String> onDelta) {
        if (!line.startsWith("data:")) {
            return;
        }
        String json = line.substring(5).trim();
        if (json.isEmpty()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            String type = node.path("type").asText("");
            switch (type) {
                case "delta" -> {
                    String content = node.path("content").asText("");
                    if (!content.isEmpty()) {
                        answer.append(content);
                        onDelta.accept(content);
                    }
                }
                case "citations" -> {
                    for (JsonNode c : node.path("citations")) {
                        citations.add(objectMapper.treeToValue(c, Citation.class));
                    }
                }
                case "error" -> throw new BusinessException(502,
                        node.path("message").asText("AI 服务返回错误"));
                default -> {
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("解析 SSE 行失败: {}", line);
        }
    }

    public record StreamResult(String answer, List<Citation> citations) {
    }
}
