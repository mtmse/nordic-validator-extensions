package org.daisy.validator.schemas;

public class GuidelineDaisy202 extends GuidelineExt {
    public GuidelineDaisy202() {
        schemaMap.put(NCC, new Schema("d202ncc.rng", "Navigation Control Center document", ""));
        schemaMap.put(MSMIL, new Schema("d202msmil.rng", "Master Synchronized Multimedia Integration Language document", ""));
        schemaMap.put(SMIL, new Schema("d202smil.rng", "Synchronized Multimedia Integration Language document", ""));
        schemaMap.put(XHTML, new Schema("d202ncc.rng", "Other content files used for text content", ""));
    }

    @Override
    public String getSchemaPath() {
        return "daisy202";
    }

    @Override
    public String getGuidelineName() {
        return "Daisy 2.02 Format";
    }

    @Override
    public String getNavReferenceTransformation() {
        return null;
    }
}
