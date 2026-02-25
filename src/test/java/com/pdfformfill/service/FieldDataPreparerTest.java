package com.pdfformfill.service;

import com.pdfformfill.dto.FieldDefinition;
import com.pdfformfill.dto.FieldsDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阶段 4 验收：传入含 string、number、checkbox 的 FieldsDefinition，断言返回的 map 中 key 为各 field name，value 类型与 type 对应。
 */
class FieldDataPreparerTest {

    private final FieldDataPreparer preparer = new FieldDataPreparer();

    @Test
    void prepareMockData_returns_map_with_correct_keys_and_value_types() {
        FieldsDefinition definition = new FieldsDefinition(List.of(
                new FieldDefinition("f_string", "string", null, null, null, null, null, 1),
                new FieldDefinition("f_number", "number", null, null, null, null, null, 1),
                new FieldDefinition("f_checkbox", "checkbox", null, null, null, null, null, 1),
                new FieldDefinition("f_boolean", "boolean", null, null, null, null, null, 1),
                new FieldDefinition("f_date", "date", null, null, null, null, null, 1)
        ));

        Map<String, Object> result = preparer.prepareMockData(definition);

        assertThat(result).containsKey("f_string").containsKey("f_number").containsKey("f_checkbox")
                .containsKey("f_boolean").containsKey("f_date");
        assertThat(result.get("f_string")).isEqualTo("test").isInstanceOf(String.class);
        assertThat(result.get("f_number")).isEqualTo(123).isInstanceOf(Integer.class);
        assertThat(result.get("f_checkbox")).isEqualTo(Boolean.TRUE).isInstanceOf(Boolean.class);
        assertThat(result.get("f_boolean")).isEqualTo(Boolean.TRUE).isInstanceOf(Boolean.class);
        assertThat(result.get("f_date")).isEqualTo("2025-01-01").isInstanceOf(String.class);
    }

    @Test
    void prepareMockData_null_definition_returns_empty_map() {
        Map<String, Object> result = preparer.prepareMockData(null);
        assertThat(result).isEmpty();
    }

    @Test
    void prepareMockData_empty_fields_returns_empty_map() {
        Map<String, Object> result = preparer.prepareMockData(new FieldsDefinition(List.of()));
        assertThat(result).isEmpty();
    }

    @Test
    void prepareMockData_unknown_type_defaults_to_string() {
        FieldsDefinition definition = new FieldsDefinition(List.of(
                new FieldDefinition("f_unknown", "unknown", null, null, null, null, null, 1)
        ));
        Map<String, Object> result = preparer.prepareMockData(definition);
        assertThat(result.get("f_unknown")).isEqualTo("test").isInstanceOf(String.class);
    }
}
