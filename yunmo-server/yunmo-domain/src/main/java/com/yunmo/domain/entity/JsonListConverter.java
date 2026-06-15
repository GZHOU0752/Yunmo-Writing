package com.yunmo.domain.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.List;

@Converter
public class JsonListConverter implements AttributeConverter<List<Object>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<Object> attribute) {
        if (attribute == null) return "[]";
        try { return objectMapper.writeValueAsString(attribute); }
        catch (JsonProcessingException e) { throw new RuntimeException("JSON 序列化失败", e); }
    }

    @Override
    public List<Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return Collections.emptyList();
        try { return objectMapper.readValue(dbData, new TypeReference<List<Object>>() {}); }
        catch (JsonProcessingException e) { throw new RuntimeException("JSON 反序列化失败", e); }
    }
}
