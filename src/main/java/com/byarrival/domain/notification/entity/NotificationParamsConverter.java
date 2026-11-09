package com.byarrival.domain.notification.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 알림의 화면 이동 값(문자열 맵)을 JSON 문자열로 저장한다.
 */
@Converter
public class NotificationParamsConverter implements AttributeConverter<Map<String, String>, String> {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final TypeReference<Map<String, String>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        return JSON_MAPPER.writeValueAsString(attribute == null ? Map.of() : attribute);
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Map.of();
        }
        return JSON_MAPPER.readValue(dbData, TYPE);
    }
}
