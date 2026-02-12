package org.daisy.validator.schemas;

import com.adobe.epubcheck.api.EpubCheck;
import org.daisy.validator.ace.ACEValidator;

public class GuidelineEPUB3 extends GuidelineExt {
    public GuidelineEPUB3() {
        schemaMap.put(XHTML, new Schema("nordic-html5.rng", "", ""));
        schemaMap.put(EPUB, new Schema("", "Nordic EPUB3", "General EPUB requirements"));
        schemaMap.put(EPUBCHECK, new Schema("", "EPUBCheck EPUB3", "Validating with EPUBCheck ", () -> EpubCheck.version()));
        schemaMap.put(ACE, new Schema("", "DAISY Accessibility Checker for EPUB", "Validating with ACE ", () -> ACEValidator.getVersion()));
        schemaMap.put(SMIL, new Schema("", "Synchronized Multimedia Integration Language document", ""));
    }

    @Override
    public String getSchemaPath() {
        return "epub3";
    }

    @Override
    public String getGuidelineName() {
        return "EPUB 3 Format";
    }

    @Override
    public String getNavReferenceTransformation() {
        return null;
    }
}
