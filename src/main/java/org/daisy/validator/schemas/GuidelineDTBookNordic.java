package org.daisy.validator.schemas;

public class GuidelineDTBookNordic extends GuidelineDTBook {
    public GuidelineDTBookNordic() {
        super();
        schemaMap.put(CONTENT_FILES, new Schema("nordic2015-1.xsl", "Nordic HTML (DTBook Content Document)", ""));
        schemaMap.put(DTBOOKNORDIC2005_3, new Schema("rng/nordic-dtbook-2005-3.rng", "DTBook 2005-3 Nordic rules", ""));
    }
}
