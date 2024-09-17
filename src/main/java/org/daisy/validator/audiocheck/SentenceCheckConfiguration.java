package org.daisy.validator.audiocheck;

import org.json.simple.JSONObject;

public class SentenceCheckConfiguration {
    public double shortLimit = 3;
    public double meanDurationPerSyllableNormal = 0.3;
    public double meanDurationPerSyllableShort = 0.8;
    public double diffLimit = 0.5;
    public double diffLimitShort = 1.5;

    public SentenceCheckConfiguration() {}
    public SentenceCheckConfiguration(JSONObject config) {
        if (config == null) return;
        if (config.containsKey("short-limit")) {
            shortLimit = Double.parseDouble((String) config.get("short-limit"));
        }
        if (config.containsKey("short_limit")) {
            shortLimit = Double.parseDouble((String) config.get("short_limit"));
        }
        if (config.containsKey("mean-duration-per-syllable-normal")) {
            meanDurationPerSyllableNormal = Double.parseDouble((String) config.get("mean-duration-per-syllable-normal"));
        }
        if (config.containsKey("mean_duration_per_syllable_normal")) {
            meanDurationPerSyllableNormal = Double.parseDouble((String) config.get("mean_duration_per_syllable_normal"));
        }
        if (config.containsKey("mean-duration-per-syllable-short")) {
            meanDurationPerSyllableShort = Double.parseDouble((String) config.get("mean-duration-per-syllable-short"));
        }
        if (config.containsKey("mean_duration_per_syllable_short")) {
            meanDurationPerSyllableShort = Double.parseDouble((String) config.get("mean_duration_per_syllable_short"));
        }
        if (config.containsKey("diff-limit")) {
            diffLimit = Double.parseDouble((String) config.get("diff-limit"));
        }
        if (config.containsKey("diff_limit")) {
            diffLimit = Double.parseDouble((String) config.get("diff_limit"));
        }
        if (config.containsKey("diff-limit-short")) {
            diffLimitShort = Double.parseDouble((String) config.get("diff-limit-short"));
        }
        if (config.containsKey("diff_limit_short")) {
            diffLimitShort = Double.parseDouble((String) config.get("diff_limit_short"));
        }
    }

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
