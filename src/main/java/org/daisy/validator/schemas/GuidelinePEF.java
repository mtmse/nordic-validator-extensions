package org.daisy.validator.schemas;

public class GuidelinePEF extends GuidelineExt {

    public GuidelinePEF() {
        schemaMap.put(PEF, new Schema(
            "pef-validation.rng",
            "PEF 1.0 - Portable Embosser Format validation",
            "Validating that the PEF file is correctly produced."
        ));
    }

    @Override
    public String getSchemaPath() {
        return "pef";
    }

    @Override
    public String getGuidelineName() {
        return "PEF 1.0 - Portable Embosser Format";
    }

    @Override
    public String getNavReferenceTransformation() {
        return null;
    }
}
