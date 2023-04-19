package org.daisy.validator.schemas;

public enum Format {
    EPUB("epub"),
    PEF("pef"),
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
