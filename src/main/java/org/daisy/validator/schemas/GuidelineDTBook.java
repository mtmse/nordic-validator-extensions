package org.daisy.validator.schemas;

public class GuidelineDTBook extends GuidelineExt {
    public GuidelineDTBook() {
        schemaMap.put(DTBOOK_110, new Schema("rng/dtbook/dtbook-1.1.0.rng", "DTBook 1.1.0", ""));
        schemaMap.put(DTBOOK2005_1, new Schema("rng/dtbook/dtbook-2005-1.rng", "DTBook 2005-1", ""));
        schemaMap.put(DTBOOK2005_2_MATHML_2, new Schema("rng/dtbook-2005-2.mathml-2.integration.rng", "DTBook 2005-2 MathML 2", ""));
        schemaMap.put(DTBOOK2005_2_MATHML_3, new Schema("rng/dtbook-2005-2.mathml-3.integration.rng", "DTBook 2005-2 MathML 3", ""));
        schemaMap.put(DTBOOK2005_3_MATHML_2, new Schema("rng/dtbook-2005-3.mathml-2.integration.rng", "DTBook 2005-3 MathML 2", ""));
        schemaMap.put(DTBOOK2005_3_MATHML_3, new Schema("rng/dtbook-2005-3.mathml-3.integration.rng", "DTBook 2005-3 MathML 3", ""));
        schemaMap.put(SMIL, new Schema("d202smil.rng", "Synchronized Multimedia Integration Language document", ""));
        schemaMap.put(MATHML, new Schema("compiled/dtbook.mathml.xsl", "Math Markup Language", "A more in-depth check of MathML validity."));
    }

    @Override
    public String getSchemaPath() {
        return "dtbook";
    }

    @Override
    public String getGuidelineName() {
        return "DTBook Formats";
    }

    @Override
    public String getNavReferenceTransformation() {
        return null;
    }
}
