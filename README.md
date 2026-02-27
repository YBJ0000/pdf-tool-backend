# PDF Form Fill Backend

Backend service that merges **any PDF** with a **fields definition** (JSON from issue-115). It always **overlays** field values as text at the coordinates given in the definition (no AcroForm fill). Output is saved to a configurable directory.

**Stack:** Java 17, Spring Boot 3.2, Gradle, Apache PDFBox, Springdoc (Swagger UI).

---

## What it does

- Accepts a **PDF template** (any PDF, with or without AcroForm) and a **definition file** (JSON with a `fields` array: `name`, `type`, `description`, `x`, `y`, `width`, `height`, `page`).
- **Overlay only:** For each field, generates mock values per type (string, number, date, checkbox, etc.) and **draws** the value as text at `(x, y, width, height)` on the given `page`. Coordinates are interpreted as PDF points (origin bottom-left) unless `scale` is provided.
- **Checkbox / boolean:** When a field has `type` `checkbox` or `boolean` and value is `true`, the service draws a **checked symbol image** in the field rectangle instead of the text "true". The image path is configured by `pdf.checkbox.checked-image` (default: `classpath:checked-symbol.png`) or overridden per request in the definition JSON with top-level **`checkboxCheckedImage`** (e.g. `"classpath:checked-symbol.png"` or a file path). Value `false` draws nothing in the field.
- **Gray form fields:** If the template has AcroForm with opaque field backgrounds (e.g. gray boxes), the service **flattens** the form first (by default) so that overlay text is drawn on top and is not covered. Set `pdf.flatten-before-overlay: false` in config to skip flattening (e.g. if a particular PDF has flatten issues).
- **Definition format:** Optional top-level **`scale`** in the JSON: when present and &gt; 0, `x`, `y`, `width`, `height` are treated as **viewport/canvas pixels** (e.g. from a frontend tool like pdf-tool-spike); the backend converts them to PDF points using `scale` (1 PDF point = `scale` pixels) and flips y from top-left-down to PDF bottom-left-up. Omit `scale` or leave it null to use coordinates as PDF points.
- Writes the filled PDF to `pdf.output.dir` (default: `./filled-pdfs`). The default checkbox image is bundled under `src/main/resources/checked-symbol.png`; you can replace it or set `pdf.checkbox.checked-image` to another path.

---

## Run the app

```bash
./gradlew bootRun
```

Server runs at **http://localhost:8080**. Swagger UI: **http://localhost:8080/swagger-ui.html**.

---

## How to test

**Unit tests**

```bash
./gradlew test
```

**Manual test (Swagger UI)**

1. Start the app with `./gradlew bootRun`.
2. Open http://localhost:8080/swagger-ui.html.
3. Use **POST /api/pdf/merge**:
   - **template**: upload any PDF.
   - **definition**: upload a JSON file with a `fields` array (`name`, `type`, `description`, `x`, `y`, `width`, `height`, `page`). If coordinates come from a frontend (e.g. pdf-tool-spike export), include **`scale`** in the JSON so positions match; without `scale`, coordinates are treated as PDF points.
4. On success you get `outputPath`; the filled PDF is saved under that path (e.g. under `filled-pdfs/`).

---

## Mock data strategy

Mock values are generated from field definitions by `FieldDataPreparer`:

- **Type-based defaults**: `string → "test"`, `number → 123`, `date → "2025-01-01"` (used as fallback).
- **Name-aware overrides** (higher priority than type): common names like *first/family/surname/worker name*, *email*, *phone/facsimile/fax*, *address*, and *DOB/Date of Birth/appointment dates* are mapped to more realistic sample values (e.g. `"John"`, `"Smith"`, `"worker@example.com"`, `"+61 400 123 456"`, `"1990-01-01"`), while keeping the original type-based behavior for other fields.
 - **Checkbox / boolean**: within a single definition, checkbox/boolean fields alternate `true` / `false` in order (1st true, 2nd false, 3rd true, ...), so not every checkbox is checked in the rendered PDF.
