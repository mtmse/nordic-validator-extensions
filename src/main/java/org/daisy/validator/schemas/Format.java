package org.daisy.validator.schemas;

public enum Format {
    EPUB("epub"), // Deprecated
    EPUB2("epub2"),
    EPUB3("epub3"),
    EPUB3_NORDIC_2015_1("epub3_nordic_2015_1"),
    EPUB3_NORDIC_2020_1("epub3_nordic_2020_1"),
    PEF("pef"),
    AUDIO_FILE("audio"),
    DTBOOK("dtbook"),
    DTBOOK_NORDIC("dtbook_nordic"),
    DAISY202("daisy202"),
    DAISY3("daisy3");

    private final String name;
    Format(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
