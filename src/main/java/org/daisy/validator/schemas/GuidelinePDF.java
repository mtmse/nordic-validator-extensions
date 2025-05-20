package org.daisy.validator.schemas;

public class GuidelinePDF extends GuidelineExt {

    public GuidelinePDF() {
        schemaMap.put(PDF, new Schema(
            "ISO-14289-2-2024",
            "PDF ISO-14289-2-2024 - Accessible PDF",
            "Validating that the PDF file is accessible."
        ));
    }

    @Override
    public String getSchemaPath() {
        return "pdf";
    }

    @Override
    public String getGuidelineName() {
        return "PDF ISO-14289-2-2024 - Accessible PDF";
    }

    @Override
    public String getNavReferenceTransformation() {
        return null;
    }
}
