package com.pdfformfill.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阶段 1 验收：给定符合 issue-115 的 JSON，反序列化为 FieldsDefinition，断言 fields 及首条 name/type/page。
 */
class FieldsDefinitionTest {

    private static final String ISSUE_115_JSON = """
            {
              "fields": [
                {
                  "name": "field_name_here",
                  "type": "string",
                  "description": "描述（可选）",
                  "x": 100,
                  "y": 200,
                  "width": 120,
                  "height": 22,
                  "page": 1
                },
                {
                  "name": "field2_anamef_fdasfads",
                  "type": "number",
                  "description": "This is the worker's age.",
                  "x": 50,
                  "y": 250,
                  "width": 111,
                  "height": 22,
                  "page": 1
                }
              ]
            }
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserialize_issue115_json_to_FieldsDefinition() throws Exception {
        FieldsDefinition definition = objectMapper.readValue(ISSUE_115_JSON, FieldsDefinition.class);

        assertThat(definition.fields()).isNotNull();
        assertThat(definition.fields()).hasSize(2);

        FieldDefinition first = definition.fields().get(0);
        assertThat(first.name()).isEqualTo("field_name_here");
        assertThat(first.type()).isEqualTo("string");
        assertThat(first.page()).isEqualTo(1);
        assertThat(first.description()).isEqualTo("描述（可选）");
        assertThat(first.x()).isEqualTo(100);
        assertThat(first.y()).isEqualTo(200);
        assertThat(first.width()).isEqualTo(120);
        assertThat(first.height()).isEqualTo(22);

        FieldDefinition second = definition.fields().get(1);
        assertThat(second.name()).isEqualTo("field2_anamef_fdasfads");
        assertThat(second.type()).isEqualTo("number");
        assertThat(second.page()).isEqualTo(1);
    }
}
