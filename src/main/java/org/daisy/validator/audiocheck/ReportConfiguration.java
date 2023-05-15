package org.daisy.validator.audiocheck;

public class ReportConfiguration {
    public double shortLimit = 3;
    public double meanDurationPerSyllableNormal = 0.3;
    public double meanDurationPerSyllableShort = 0.8;
    public double diffLimit = 0.5;
    public double diffLimitShort = 1.5;

    public double getShortLimit() {
        return shortLimit;
    }

    public double getDiffLimit() {
        return diffLimit * 1000;
    }

    public double getDiffLimitShort() {
        return diffLimitShort * 1000;
    }

    public double getMeanDurationPerSyllableNormal() {
        return meanDurationPerSyllableNormal * 1000;
    }

    public double getMeanDurationPerSyllableShort() {
        return meanDurationPerSyllableShort * 1000;
    }
}
