package com.pdfformfill.service;

import com.pdfformfill.dto.FieldDefinition;
import com.pdfformfill.dto.FieldsDefinition;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 根据字段定义生成 mock 数据，用于填表测试。
 * 按 type 生成：string→"test", number→123, date→"2025-01-01", boolean/checkbox→true。
 */
@Component
public class FieldDataPreparer {

    private static final String MOCK_STRING = "test";
    private static final int MOCK_NUMBER = 123;
    private static final String MOCK_DATE = "2025-01-01";
    private static final Boolean MOCK_BOOLEAN = Boolean.TRUE;

    /**
     * 为定义中的每个字段生成 mock 值，key 为 field name，value 与 type 对应。
     *
     * @param definition 表单字段定义，可为 null 或 fields 为 null，此时返回空 map
     * @return name → mock value，不修改调用方
     */
    public Map<String, Object> prepareMockData(FieldsDefinition definition) {
        Map<String, Object> result = new HashMap<>();
        if (definition == null || definition.fields() == null) {
            return result;
        }
        List<FieldDefinition> fields = definition.fields();
        for (FieldDefinition field : fields) {
            String name = field.name();
            if (name == null) {
                continue;
            }
            result.put(name, mockValueFor(field.type()));
        }
        return result;
    }

    private Object mockValueFor(String type) {
        if (type == null) {
            return MOCK_STRING;
        }
        return switch (type.toLowerCase()) {
            case "number" -> MOCK_NUMBER;
            case "date" -> MOCK_DATE;
            case "boolean", "checkbox" -> MOCK_BOOLEAN;
            default -> MOCK_STRING; // "string" 及未知类型
        };
    }
}
