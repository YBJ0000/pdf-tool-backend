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
        assertThat(result.get("f_checkbox")).isInstanceOf(Boolean.class);
        assertThat(result.get("f_boolean")).isInstanceOf(Boolean.class);
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

    @Test
    void prepareMockData_uses_human_readable_values_based_on_name_for_person_info() {
        FieldsDefinition definition = new FieldsDefinition(List.of(
                new FieldDefinition("Rail worker’s name", "string", null, null, null, null, null, 1),
                new FieldDefinition("Worker family name", "string", null, null, null, null, null, 1),
                new FieldDefinition("Work first name", "string", null, null, null, null, null, 1),
                new FieldDefinition("Worker DOB", "date", null, null, null, null, null, 1),
                new FieldDefinition("Email", "string", null, null, null, null, null, 1),
                new FieldDefinition("Phone", "string", null, null, null, null, null, 1),
                new FieldDefinition("Address", "string", null, null, null, null, null, 1)
        ));

        Map<String, Object> result = preparer.prepareMockData(definition);

        assertThat(result.get("Rail worker’s name")).isEqualTo("Alex Railworker");
        assertThat(result.get("Worker family name")).isEqualTo("Smith");
        assertThat(result.get("Work first name")).isEqualTo("John");
        assertThat(result.get("Worker DOB")).isEqualTo("1990-01-01");
        assertThat(result.get("Email")).isEqualTo("worker@example.com");
        assertThat(result.get("Phone")).isEqualTo("+61 400 123 456");
        assertThat(result.get("Address")).isEqualTo("123 Sample Street, Sydney NSW 2000");
    }

    @Test
    void prepareMockData_checkbox_and_boolean_alternate_true_and_false_within_definition() {
        FieldsDefinition definition = new FieldsDefinition(List.of(
                new FieldDefinition("cb1", "checkbox", null, null, null, null, null, 1),
                new FieldDefinition("cb2", "checkbox", null, null, null, null, null, 1),
                new FieldDefinition("cb3", "checkbox", null, null, null, null, null, 1),
                new FieldDefinition("flag1", "boolean", null, null, null, null, null, 1),
                new FieldDefinition("text1", "string", null, null, null, null, null, 1)
        ));

        Map<String, Object> result = preparer.prepareMockData(definition);

        // 1st checkbox → true, 2nd → false, 3rd → true, then boolean → false, 互相交替
        assertThat(result.get("cb1")).isEqualTo(Boolean.TRUE);
        assertThat(result.get("cb2")).isEqualTo(Boolean.FALSE);
        assertThat(result.get("cb3")).isEqualTo(Boolean.TRUE);
        assertThat(result.get("flag1")).isEqualTo(Boolean.FALSE);
        assertThat(result.get("text1")).isEqualTo("test");
    }
}
