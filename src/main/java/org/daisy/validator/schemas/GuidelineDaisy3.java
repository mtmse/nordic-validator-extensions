package org.daisy.validator.schemas;

public class GuidelineDaisy3 extends GuidelineExt {
    public GuidelineDaisy3() {
        schemaMap.put(NCX, new Schema("rng/daisy3ncx.rng", "Navigation Control Center document", ""));
        schemaMap.put(SMIL, new Schema("rng/daisy3smil.rng", "Synchronized Multimedia Integration Language document", ""));
        schemaMap.put(XHTML, new Schema("rng/daisy3ncx.rng", "Other content files used for text content", ""));
        schemaMap.put(DTBOOK_110, new Schema("rng/dtbook/dtbook-1.1.0.rng", "DTBook 1.1.0", ""));
        schemaMap.put(DTBOOK2005_1, new Schema("rng/dtbook/dtbook-2005-1.rng", "DTBook 2005-1", ""));
        schemaMap.put(DTBOOK2005_2_MATHML_2, new Schema("rng/dtbook-2005-2.mathml-2.integration.rng", "DTBook 2005-2 MathML 2", ""));
        schemaMap.put(DTBOOK2005_2_MATHML_3, new Schema("rng/dtbook-2005-2.mathml-3.integration.rng", "DTBook 2005-2 MathML 3", ""));
        schemaMap.put(DTBOOK2005_3_MATHML_2, new Schema("rng/dtbook-2005-3.mathml-2.integration.rng", "DTBook 2005-3 MathML 2", ""));
        schemaMap.put(DTBOOK2005_3_MATHML_3, new Schema("rng/dtbook-2005-3.mathml-3.integration.rng", "DTBook 2005-3 MathML 3", ""));
        schemaMap.put(OPF, new Schema("compiled/daisy3.opf.xsl", "Nordic Daisy3 Package Document", ""));
    }

    @Override
    public String getSchemaPath() {
        return "daisy3";
    }

    @Override
    public String getGuidelineName() {
        return "Daisy 3 Format";
    }

    @Override
    public String getNavReferenceTransformation() {
        return null;
    }
}
