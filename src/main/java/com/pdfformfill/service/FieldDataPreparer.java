package com.pdfformfill.service;

import com.pdfformfill.dto.FieldDefinition;
import com.pdfformfill.dto.FieldsDefinition;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 根据字段定义生成 mock 数据，用于填表测试。
 * 优先按 field name 做语义化 mock，例如：
 * - name / first name / surname / family name → 看起来像人名
 * - phone / facsimile / fax → 看起来像电话号码
 * - email → 看起来像邮箱
 * - address → 看起来像地址
 * - dob / date of birth → 看起来像出生日期
 * - 其他字段则按 type 生成：string→"test", number→123, date→"2025-01-01", boolean/checkbox→true。
 */
@Component
public class FieldDataPreparer {

    private static final String MOCK_STRING = "test";
    private static final int MOCK_NUMBER = 123;
    private static final String MOCK_DATE = "2025-01-01";
    private static final Boolean MOCK_BOOLEAN = Boolean.TRUE;

    /**
     * 为定义中的每个字段生成 mock 值，key 为 field name，value 与 type 对应。
     * checkbox/boolean 字段会在同一个 definition 内交替生成 true / false，
     * 避免所有勾选框都为选中状态；只有 1 个时仍为 true。
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
        int checkboxIndex = 0;
        for (FieldDefinition field : fields) {
            String name = field.name();
            if (name == null) {
                continue;
            }
            if (isCheckboxOrBoolean(field.type())) {
                // 在当前 definition 内按顺序交替 true/false：第 1 个 true，第 2 个 false，依此类推
                boolean value = checkboxIndex % 2 == 0;
                checkboxIndex++;
                result.put(name, value);
                continue;
            }
            result.put(name, mockValueFor(field));
        }
        return result;
    }

    /** Long text for verticalAlign top / multi-line style fields to exercise font shrink. */
    private static final String MOCK_LONG_TEXT_TOP =
            "123 Sample Street, Sydney NSW 2000, Australia. Unit 5, Building B. Contact: reception. 123 Sample Street, Sydney NSW 2000, Australia. Unit 5, Building B. Contact: reception. 123 Sample Street, Sydney NSW 2000, Australia. Unit 5, Building B. Contact: reception. 123 Sample Street, Sydney NSW 2000, Australia. Unit 5, Building B. Contact: reception. 123 Sample Street, Sydney NSW 2000, Australia. Unit 5, Building B. Contact: reception. 123 Sample Street, Sydney NSW 2000, Australia. Unit 5, Building B. Contact: reception.";

    private Object mockValueFor(FieldDefinition field) {
        String name = field.name();
        String type = field.type();
        String safeName = name == null ? "" : name.toLowerCase();
        boolean isTopAligned = field.verticalAlign() != null && field.verticalAlign().equalsIgnoreCase("top");

        // 先根据字段名做更“像真的” mock
        // 人名相关
        if (safeName.contains("first name")) {
            return "John John John John John John John John";
        }
        if (safeName.contains("family name") || safeName.contains("surname")) {
            return "Smith";
        }
        if (safeName.contains("worker") && safeName.contains("name")) {
            return "Alex Railworker Alex Railworker Alex Railworker Alex Railworker Alex Railworker";
        }
        if (safeName.contains("doctor") && safeName.contains("appointment")) {
            return "Dr Taylor";
        }
        if (safeName.contains("doctor")) {
            return "Dr Smith";
        }
        if (safeName.contains("operator") && safeName.contains("rail")) {
            return "ACME Rail Pty Ltd";
        }

        // 联系方式
        if (safeName.contains("email")) {
            return "worker@example.com";
        }
        if (safeName.contains("phone") || safeName.contains("facsimile") || safeName.contains("fax")) {
            return "+61 400 123 456";
        }

        // 地址：top 对齐的框用长文本以便测试动态缩小字号
        if (safeName.contains("address")) {
            return isTopAligned ? MOCK_LONG_TEXT_TOP : "123 Sample Street, Sydney NSW 2000";
        }

        // 日期类：根据 name 做一点区分
        if (typeEquals(type, "date")) {
            if (safeName.contains("dob") || safeName.contains("date of birth") || safeName.endsWith(" dob")) {
                return "1990-01-01";
            }
            if (safeName.contains("next review")) {
                return "2025-06-01";
            }
            if (safeName.contains("appointment")) {
                return "2025-03-15";
            }
            if (safeName.contains("drug test") || safeName.contains("test date")) {
                return "2025-02-01";
            }
            // 其他日期维持原来的固定值
            return MOCK_DATE;
        }

        // 对于 string、number 仍然保留原来的 type 逻辑，保证兼容性
        if (type == null) {
            return MOCK_STRING;
        }
        if (typeEquals(type, "number")) {
            return MOCK_NUMBER;
        }
        // verticalAlign 为 top 的 string 用较长文本，便于测试单行内缩小字号
        if (typeEquals(type, "string") && isTopAligned) {
            return MOCK_LONG_TEXT_TOP;
        }
        return MOCK_STRING; // "string" 及未知类型
    }

    private boolean typeEquals(String type, String expected) {
        return type != null && type.equalsIgnoreCase(expected);
    }

    private boolean isCheckboxOrBoolean(String type) {
        return typeEquals(type, "checkbox") || typeEquals(type, "boolean");
    }
}
