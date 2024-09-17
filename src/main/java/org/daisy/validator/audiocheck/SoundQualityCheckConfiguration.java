package org.daisy.validator.audiocheck;

import org.json.simple.JSONObject;

public class SoundQualityCheckConfiguration {
    private double peekLevelThreshold = -3;
    private double clippingLevelThreshold = -0.1;
    private double unevenPeekOverlap = 5;
    private double unevenPeekRangeInSeconds = 30;
    private double unevenPeekDifferenceThreshold = 0.05;
    private double averagePeekLevelDifference = 0.5;
    private double initialSilenceThreshold = -50;
    private double longSilenceLevel = -50;
    private double longSilenceDuration = 8;

    public SoundQualityCheckConfiguration() {
    }

    public SoundQualityCheckConfiguration(JSONObject config) {
        if (config == null) return;
        if (config.containsKey("peek_level_threshold")) {
            peekLevelThreshold = Double.parseDouble((String) config.get("peek_level_threshold"));
        }
        if (config.containsKey("clipping_level_threshold")) {
            clippingLevelThreshold = Double.parseDouble((String) config.get("clipping_level_threshold"));
        }
        if (config.containsKey("uneven_peek_overlap")) {
            unevenPeekOverlap = Double.parseDouble((String) config.get("uneven_peek_overlap"));
        }
        if (config.containsKey("uneven_peek_range_in_seconds")) {
            unevenPeekRangeInSeconds = Double.parseDouble((String) config.get("uneven_peek_range_in_seconds"));
        }
        if (config.containsKey("uneven_peek_difference_threshold")) {
            unevenPeekDifferenceThreshold = Double.parseDouble((String) config.get("uneven_peek_difference_threshold"));
        }
        if (config.containsKey("average_peek_level_difference")) {
            averagePeekLevelDifference = Double.parseDouble((String) config.get("average_peek_level_difference"));
        }
        if (config.containsKey("initial_silence_threshold")) {
            initialSilenceThreshold = Double.parseDouble((String) config.get("initial_silence_threshold"));
        }
        if (config.containsKey("long_silence_level")) {
            longSilenceLevel = Double.parseDouble((String) config.get("long_silence_level"));
        }
        if (config.containsKey("long_silence_duration")) {
            longSilenceDuration = Double.parseDouble((String) config.get("long_silence_duration"));
        }
    }

    public double getPeekLevelThreshold() {
        return peekLevelThreshold;
    }

    public double getClippingLevelThreshold() {
        return clippingLevelThreshold;
    }

    public double getUnevenPeekOverlap() {
        return unevenPeekOverlap;
    }

    public double getUnevenPeekRangeInSeconds() {
        return unevenPeekRangeInSeconds;
    }

    public double getUnevenPeekDifferenceThreshold() {
        return unevenPeekDifferenceThreshold;
    }

    public double getAveragePeekLevelDifference() {
        return averagePeekLevelDifference;
    }

    public double getInitialSilenceThreshold() {
        return initialSilenceThreshold;
    }

    public double getLongSilenceLevel() {
        return longSilenceLevel;
    }

    public double getLongSilenceDuration() {
        return longSilenceDuration;
    }
}

