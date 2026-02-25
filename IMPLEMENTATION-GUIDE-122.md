# Implementation Guide: Issue #122 — PDF Form Fill Backend

本文档是给 Agent 的实现指导，用于实现 **issue-122**：后端接收 PDF 模板 + 表单字段定义 JSON，合并生成填好的 PDF 并保存。与 **issue-115** 产出的 JSON 格式兼容。

---

## 1. 目标与范围

- **输入**：PDF form template（空白表单 PDF）+ PDF form fields definition file（issue-115 导出的 JSON）。
- **输出**：填好数据的 PDF，保存到指定路径；API 可返回文件或路径。
- **技术栈**：Java 17+，Spring Boot 3.x，Apache PDFBox（加载/填写 AcroForm），Spring Web（REST），springdoc-openapi（Swagger UI）。
- **约束**：实现放在独立 package 中，分层清晰、可测试、符合 Spring 惯例。

---

## 2. 表单字段定义 JSON 格式（与 issue-115 一致）

前端导出的 `fields.json` 结构必须被后端兼容。定义如下：

```json
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
    }
  ]
}
```

- **name**：必填，字段唯一标识，填 PDF 时按 name 匹配 AcroForm 控件。
- **type**：`string` | `number` | `date` | `boolean` | `checkbox`，用于生成 mock 或校验。
- **description**：可选。
- **x, y, width, height, page**：坐标与页码（填写 AcroForm 时可能不直接用到，但需解析保存；若仅按 name 填 AcroForm，可先解析忽略坐标）。

后端 DTO 需与该 schema 一致，便于反序列化。

---

## 3. 实现阶段与顺序

### 阶段 0：项目与配置

- **先做**：新建 Spring Boot 项目（或在本仓库下新建独立 module），Java 17+，Spring Boot 3.x。
- **依赖**（`pom.xml` 或 Gradle）：
  - `spring-boot-starter-web`
  - `org.apache.pdfbox:pdfbox`（2.x 或 3.x，推荐 3.x 若用 Java 17+）
  - `springdoc-openapi-starter-webmvc-ui`（Swagger UI）
- **配置**：在 `application.yml` 中预留“输出目录”配置，例如 `pdf.output.dir`，用于保存生成的 PDF。
- **验收**：`mvn spring-boot:run` 启动成功，访问 `/swagger-ui.html` 能看到 Swagger UI。

---

### 阶段 1：DTO 与“加载定义文件”

- **先做**：
  - 定义 `FieldsDefinition`（或 `FormFieldsDefinition`）：含 `List<FieldDefinition>`，字段与上述 JSON 的 `fields` 元素一致（name, type, description, x, y, width, height, page）。
  - 定义单条 `FieldDefinition` 的 Java 类/记录，与 JSON 属性一一对应。
- **验收**：单元测试：给定一份符合 issue-115 的 JSON 字符串，反序列化为 `FieldsDefinition`，断言 `fields.size()` 及首个 field 的 name/type/page 正确。

---

### 阶段 2：加载 PDF 模板

- **先做**：在独立 package 内实现“加载 PDF”能力（例如 `PdfTemplateLoader` 或 `PdfFormService.loadTemplate(InputStream)`），使用 PDFBox 的 `Loader.loadPDF(InputStream)` 得到 `PDDocument`，不填表，仅加载。资源使用 try-with-resources 或显式 close。
- **验收**：单元测试：用 classpath 下或临时文件中的一份 PDF 调用 loader，断言返回非 null 的 `PDDocument` 且 `getNumberOfPages() > 0`。

---

### 阶段 3：API 接收“模板 + 定义文件”

- **先做**：
  - 设计 REST 接口：例如 `POST /api/pdf/merge` 或 `POST /api/pdf/fill`。
  - 入参：`multipart/form-data`，两个部分：`template`（PDF 文件）、`definition`（JSON 文件，即 form fields definition file）。
  - 使用 `@RequestParam("template") MultipartFile template` 和 `@RequestParam("definition") MultipartFile definition` 接收。
  - 在 Controller 内：将 `template.getInputStream()` 交给阶段 2 的 loader 加载 PDF；将 `definition.getBytes()` 或 `definition.getInputStream()` 反序列化为阶段 1 的 `FieldsDefinition`。
- **验收**：Swagger UI 中上传一个 PDF + 一份符合 issue-115 的 JSON，接口返回 200（可先只做解析与校验，暂不写 PDF）；或返回 415/400 时给出清晰错误信息。

---

### 阶段 4：按定义准备字段数据（Mock）

- **先做**：根据 `FieldsDefinition` 中的每个 field 的 `name` 和 `type` 生成 mock 值（例如 string→"test", number→123, date→"2025-01-01", boolean/checkbox→true），得到 `Map<String, Object>`（name → value）。可放在单独类如 `FieldDataPreparer` 或 `FormDataService.prepareMockData(FieldsDefinition)`。
- **验收**：单元测试：传入含 string、number、checkbox 的 `FieldsDefinition`，断言返回的 map 中 key 为各 field name，value 类型与 type 对应。

---

### 阶段 5：合并模板与字段数据并生成填好的 PDF

- **先做**：
  - 使用 PDFBox：从已加载的 `PDDocument` 获取 AcroForm（`document.getDocumentCatalog().getAcroForm()`），若为 null 可返回明确错误（“该 PDF 不是表单”）。
  - 遍历 AcroForm 的 fields，按 name 在阶段 4 的 `Map<String, Object>` 中取值，调用 `field.getValueAsString()` 的 setter 或 PDFBox 提供的填表 API 写入值。
  - 注意：若 definition 中的 name 与 PDF 内 AcroForm 的 name 不一致，可只填能匹配上的；未匹配的可在日志中 warn。
  - 输出为新的 `PDDocument` 或覆盖内存中的副本，不修改原始上传的 template。
- **验收**：单元测试：用一份带 AcroForm 的 PDF（可放在 `src/test/resources`）+ 对应的 definition JSON，调用“加载 + 准备 mock + 合并”，断言生成的 PDDocument 中对应字段的 value 与 mock 一致（用 PDFBox 读回 value）。若暂无带 AcroForm 的 PDF，可先测“有 AcroForm 的文档能加载且不抛异常”。

---

### 阶段 6：保存到指定路径并暴露 API

- **先做**：
  - 将阶段 5 生成的填好的 PDF 写入配置的 `pdf.output.dir`（或请求参数中的路径），文件名可带时间戳或 UUID 避免冲突。
  - 在阶段 3 的 Controller 中串联：接收 template + definition → 加载 template → 解析 definition → 准备 mock 数据 → 合并生成 PDF → 保存到指定路径。
  - 返回：可以是“保存成功 + 文件路径”的 JSON，或直接返回生成的 PDF 文件流（`Content-Disposition: attachment`）。先实现一种即可（建议先返回路径或 JSON，便于 Swagger 测试）。
- **验收**：Swagger UI 中上传 PDF template + definition JSON，调用接口后返回 200，且配置的输出目录下出现新 PDF；用 PDF 阅读器打开，对应 AcroForm 控件应显示 mock 值。

---

## 4. 推荐包结构与分层

- `...api` 或 `...web`：REST Controller（接收 multipart，调用 service）。
- `...service`：编排“加载模板、解析定义、准备数据、合并、保存”（如 `PdfFormFillService`）。
- `...model` 或 `...dto`：`FieldsDefinition`、`FieldDefinition`、以及 API 响应 DTO。
- `...pdf` 或 `...pdfbox`：封装 PDFBox 的加载、AcroForm 填写、保存（可拆为 loader + filler + writer），便于单测和替换。

---

## 5. 简版测试验收汇总（Swagger UI）

| 阶段 | 验收方式 |
|------|----------|
| 0 | 启动应用，打开 Swagger UI，无报错。 |
| 1 | 不依赖 HTTP：单测反序列化 issue-115 的 JSON。 |
| 2 | 单测加载 PDF 得到 PDDocument。 |
| 3 | Swagger UI：上传 PDF + JSON，接口 200 且能解析；坏 JSON 返回 4xx。 |
| 4 | 单测：FieldsDefinition → mock Map 正确。 |
| 5 | 单测：带 AcroForm 的 PDF + definition → 填好的 PDDocument 可读回值。 |
| 6 | Swagger UI：上传 PDF + JSON，返回 200，输出目录生成 PDF，打开后表单已填 mock 数据。 |

---

## 6. 与 issue-115 的对应关系

- **issue-115**（本仓库前端）：用户画框、填 name/type/description，导出 `fields.json`。该 JSON 即 **PDF form fields definition file**。
- **issue-122**（本指导的后端）：接收 **PDF form template**（任意空白或带 AcroForm 的 PDF）+ 上述 **definition file**，按 definition 中的 name/type 准备数据（当前用 mock），填进 PDF 的 AcroForm，保存为填好的 PDF。前端“选择 PDF”和“导入 JSON”后续可对接本 API（multipart 上传 template + definition）。

---

## 7. 注意事项

- PDF 若无 AcroForm，需在 API 或 service 层返回明确错误，避免 NPE。
- 填表时注意编码（如 UTF-8）和 PDFBox 对字体/嵌入的要求，避免中文乱码。
- 输出路径需考虑权限与目录存在性；若目录不存在可先创建或返回 500 与清晰信息。

完成上述阶段后，即可得到可独立运行、通过 Swagger UI 验收的后端；之后再在 frontend 中增加“上传 PDF + JSON 并调用本 API”的流程即可完成前后端对接。
