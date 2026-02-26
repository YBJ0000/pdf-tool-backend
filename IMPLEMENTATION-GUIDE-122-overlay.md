# Implementation Guide: Issue #122 — Overlay Rendering

本文档是实现 **坐标叠加渲染（overlay）** 的步骤指引。项目**统一按 overlay 处理**：无论 PDF 是否有 AcroForm，均根据 definition 中的 `x, y, width, height, page` 在对应位置绘制字段值，支持任意 PDF。

---

## 1. 目标与范围

- **当前能力**：模板 + issue-115 的 definition JSON → 根据 `x, y, width, height, page` 把字段值**绘制**到对应位置，生成新 PDF（不依赖 AcroForm）。
- **统一 overlay**：有表单、无表单的 PDF 均走同一套 overlay 渲染；不再对 AcroForm 做“填表”处理。
- **技术栈**：Java 17+, Spring Boot 3.x, PDFBox 3.x；overlay 使用 `PDPageContentStream` 在指定页、指定矩形内绘制文本。
- **约束**：overlay 实现放在 `...pdf.overlay`，可单独单测。

---

## 2. 坐标与渲染规则（与 issue-122-overlay-rendering.md 一致）

实现前需明确并在代码/注释中固定：

1. **坐标系**
   - PDF 默认 user space：原点在页面**左下角**，x 向右、y 向上；单位 **point**（1/72 英寸）。
   - definition 中的 `(x, y)` 视为该字段矩形的**左下角**；`width`、`height` 为矩形宽高（point）。与 issue-115 导出一致。
   - 暂不处理页面旋转、crop box；使用默认页面 media box 即可。

2. **文本布局（首版可简化）**
   - 每个字段在矩形内绘制为**单行**文本。
   - 若文本宽度超过 `width`：优先 **shrink-to-fit**（缩小字号直到 fit）；若仍超则 **truncate** 并可选加省略号。行为需确定且可单测。

3. **字体与编码**
   - 首版可用 PDFBox 标准 14 字体（如 Helvetica）保证英文/数字；**UTF-8** 入参。
   - 中文支持：需嵌入支持中文的字体（如 Noto Sans SC）；可放在后续阶段，文档中注明。

4. **类型格式化**
   - `string` → 原样绘制。
   - `number` → 格式化为字符串后绘制（如 `123`）。
   - `date` → 统一格式（如 `yyyy-MM-dd`）后绘制。
   - `boolean` / `checkbox` → 如 `"true"` / `"false"` 或勾/叉符号，需在实现中约定。

---

## 3. 实现阶段与顺序

### 阶段 1：Overlay 核心渲染器

- **先做**：
  - 在独立 package（如 `com.pdfformfill.pdf.overlay`）中实现 `PdfOverlayRenderer`（或 `OverlayRenderer`）。
  - 方法签名建议：`void render(PDDocument document, List<FieldDefinition> fields, Map<String, Object> fieldData)`。
  - 遍历 `fields`：用 `page`（1-based）取 `PDPage`，用 `name` 在 `fieldData` 中取值；在 `(x, y, width, height)` 矩形内用 `PDPageContentStream` 绘制文本（append 到该页现有 content stream 或作为新 content stream）。
  - 首版字体可用 `PDType1Font.HELVETICA`（仅拉丁字符）；坐标按 PDF user space（y 向上），注意 PDFBox 中页高与 y 的换算。
- **验收**：单测：生成仅一页的空白 PDF，调用 `render` 写入一个字段（如 name="A", value="test", 给定 x,y,width,height,page=1），保存后用 PDFTextStripper 或已知坐标读回，断言该页文本包含 "test"；或人工打开生成的 PDF 确认位置合理。

---

### 阶段 2：文本溢出与字体（可选分步）

- **先做**：
  - **布局**：实现单行 shrink-to-fit（根据 `width` 估算文本宽度，超则减小字号再绘）；可选 truncate + "..."。
  - **中文**（可拆为 2b）：引入或嵌入支持中文的字体（如 classpath 或文件中的 .ttf），用 `PDType1Font` 以外的方式（如 `PDTrueTypeFont.load(...)`）设置到 `PDPageContentStream`，确保 UTF-8 字符串正确绘制。
- **验收**：单测：过长字符串在给定 width 下被缩小或截断；若有中文字体，含中文的 value 能正确显示。

---

### 阶段 3：服务集成（当前实现）

- **实现**：`PdfFormFillService.merge(...)` 在加载 template、解析 definition、准备好 `fieldData` 之后，**始终**调用 `PdfOverlayRenderer.render(document, definition.fields(), fieldData, definition.scale())`，不再根据 AcroForm 分支。保存逻辑不变：写入 `pdf.output.dir`，返回 `MergeResponse`（含 outputPath）。
- **验收**：任意 PDF 模板 + definition → 在对应 (page, x, y, width, height) 位置看到 mock 值；若 definition 带 `scale`（如前端导出），坐标按 viewport 像素换算，位置与前端一致。

---

## 4. 推荐包结构与分层

- **现有**：`api`、`service`（含 `FieldDataPreparer`）、`dto`、`pdf`（`PdfTemplateLoader`）保持不变。
- **overlay**：`pdf.overlay.PdfOverlayRenderer`；overlay 细节在 renderer 内，`PdfFormFillService` 仅做编排与调用。

---

## 5. 与 issue-115 / 现有 JSON 的对应关系

- definition JSON 格式**不变**：仍为 `fields[]`，每项含 `name`, `type`, `description`, `x`, `y`, `width`, `height`, `page`；可选顶层 `scale`（前端 viewport 像素换算用）。
- **Overlay**：用 `name`（取值）、`type`（mock 格式化）、`x`, `y`, `width`, `height`, `page`（定位与布局）；带 `scale` 时坐标按 viewport 像素换算为 PDF 点。

---

## 6. 注意事项

- Overlay 是**追加绘制**到页面内容上，不修改 AcroForm，不依赖表单域存在。
- 多页 PDF：`page` 为 1-based，与 `PDDocument.getPages()` 下标一致（或做一次 -1 映射，在实现中统一约定）。
- 输出路径、权限、目录不存在时的处理与现有实现一致（如 `Files.createDirectories`、清晰错误信息）。

完成上述阶段后，同一套 API 即可支持“仅表单 PDF”和“任意 PDF + JSON 标注”两种场景；后续可按需再细化布局（多行、对齐）或字体策略。
