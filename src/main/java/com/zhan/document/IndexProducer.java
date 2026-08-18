package com.zhan.document;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class IndexProducer {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.index-stream}")
    private String streamKey;

    public void sendIndexRequest(Long documentId, Long kbId, String filePath) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("documentId", String.valueOf(documentId));
        fields.put("kbId", String.valueOf(kbId));
        fields.put("filePath", filePath);
        stringRedisTemplate.opsForStream().add(streamKey, fields);
    }
}
