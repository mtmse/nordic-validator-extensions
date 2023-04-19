package org.daisy.validator.schemas;

import org.json.simple.JSONObject;

import java.util.Map;

public abstract class GuidelineExt extends Guideline {
    public static final String PEF = "pef";
    public static final String NCC = "ncc";
    public static final String NCX = "ncx";
    public static final String MSMIL = "msmil";
    public static final String SMIL = "smil";
    public static final String DTBOOK_110 = "dtbook-1.1.0";
    public static final String DTBOOK2005_1 = "dtbook-2005-1";
    public static final String DTBOOK2005_2_MATHML_2 = "dtbook-2005-2-mathml-2";
    public static final String DTBOOK2005_2_MATHML_3 = "dtbook-2005-2-mathml-3";
    public static final String DTBOOK2005_3_MATHML_2 = "dtbook-2005-3-mathml-2";
    public static final String DTBOOK2005_3_MATHML_3 = "dtbook-2005-3-mathml-3";
    public static final String DTBOOKNORDIC2005_3 = "dtbook-2005-3-nordic";

    public static final String MATHML = "mathml";

    public JSONObject getSchemaInformationJSON() {
        JSONObject allSchemas = new JSONObject();
        for (Map.Entry<String, Schema> entry : schemaMap.entrySet()) {
            JSONObject schemaJSON = new JSONObject();
            schemaJSON.put("filename", entry.getValue().getFilename());
            schemaJSON.put("description", entry.getValue().getDescription());
            schemaJSON.put("document-type", entry.getValue().getDocumentType());
            allSchemas.put(entry.getKey(), schemaJSON);
        }
        return allSchemas;
    }
}
