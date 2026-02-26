# Issue #122 -- PDF Overlay Rendering Based on JSON Field Definitions

## Summary

Implement PDF generation by rendering (overlaying) field values onto a
PDF template based on the field rectangles exported from issue #115.

This implementation **must not rely on AcroForm form fields**. Instead,
it must render field values directly into the PDF content stream using
the coordinates (`x`, `y`, `width`, `height`, `page`) provided in the
JSON definition.

The solution must work for PDFs that do **not** contain form fields.

**Current implementation:** The backend uses **overlay only** for all PDFs (no AcroForm form-fill). Original note:
The backend was to support **overlay only** (obsolete: previously "two modes") in the same project: if the template has AcroForm, use form filling (existing behaviour); otherwise use coordinate-based overlay rendering (this document). That way both “form PDF” and “any PDF” are supported.

------------------------------------------------------------------------

## Background

Issue #115 exports a JSON file describing fields marked on a PDF,
including:

-   name
-   type
-   description
-   x
-   y
-   width
-   height
-   page

Issue #122 consumes: 1. A PDF template (with or without form fields) 2.
The JSON field definition file from #115 3. Field data (mock data
allowed for now)

The goal is to generate a new PDF where values are rendered at the
specified positions.

------------------------------------------------------------------------

## Requirements

### 1. Load PDF Template

-   Load a PDF template from a given path.
-   The template may or may not contain AcroForm fields.

### 2. Load Field Definition JSON

-   Load and parse the JSON file generated in issue #115.
-   The JSON contains a list of field definitions with positioning
    metadata.

### 3. Prepare Field Data

-   Prepare field values according to the definition schema.
-   Mock data is acceptable for testing.
-   Support basic type handling (string, number, date).

### 4. Render Field Values (Overlay Rendering)

-   For each field definition:
    -   Locate the specified `page`
    -   Render the field value into the rectangle defined by:
        -   `x`
        -   `y`
        -   `width`
        -   `height`
-   Rendering must:
    -   Draw text directly into the PDF content stream
    -   Not create or modify AcroForm fields
    -   Work even if the template contains no form fields

### 5. Save Generated PDF

-   Save the final PDF to a specified output path.

------------------------------------------------------------------------

## Rendering Rules (Must Be Explicitly Defined)

The implementation must clearly define and document:

1.  Coordinate System
    -   Confirm whether coordinates use PDF user space (origin
        bottom-left).
    -   Unit: points (1/72 inch).
    -   Define handling for page rotation and crop boxes.
2.  Text Layout Behavior
    -   Define what happens when text exceeds the rectangle:
        -   Shrink-to-fit
        -   Wrap text
        -   Truncate
    -   Behavior must be deterministic.
3.  Font Handling
    -   Support UTF-8 text.
    -   Support Chinese characters (embed appropriate font if required).
4.  Type Formatting
    -   number → formatted appropriately
    -   date → formatted consistently
    -   string → rendered as-is

------------------------------------------------------------------------

## Design Constraints

-   Implementation must reside in a dedicated package.
-   Expose a clean API surface such as:
    -   loadTemplate(...)
    -   loadDefinition(...)
    -   prepareData(...)
    -   renderOverlay(...)
    -   save(...)
-   Follow Spring best practices:
    -   Clear layering
    -   Dependency injection
    -   Testable components
    -   Alignment with repository conventions

------------------------------------------------------------------------

## Acceptance Criteria

-   A PDF template can be loaded successfully.
-   A JSON field definition file from issue #115 can be loaded
    successfully.
-   Field values are rendered at the correct page and coordinates.
-   Works for PDFs with **no AcroForm fields**.
-   The output PDF is generated and saved successfully.
-   Implementation is placed in a dedicated package with a clean API.
-   The solution follows Spring architectural best practices.

------------------------------------------------------------------------

## Non-Goals (for the overlay path only)

-   The **overlay** path does not use or create AcroForm fields.
-   The template is not required to contain form fields for overlay to work.
-   Do not modify the original template file in place (output is a new file).
-   (The same backend may still offer AcroForm-based filling when the template has form fields; that is a separate path.)

------------------------------------------------------------------------

## Notes

-   Mock data may be used during development.
-   Apache PDFBox is recommended but not mandatory.
-   Rendering logic should be unit-testable where possible.

------------------------------------------------------------------------

