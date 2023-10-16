package org.daisy.validator.audiocheck;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AudioFile {
    private static final Pattern bitratePattern = Pattern.compile("Bit Rate\\s*:\\s*(\\d+)");
    private static final Pattern channelsPattern = Pattern.compile("Channels\\s*:\\s*(\\d+)");
    private static final Pattern sampleRatePattern = Pattern.compile("Sample Rate\\s*:\\s*(\\d+)");
    private static final Pattern durationPattern = Pattern.compile("Length \\(seconds\\)\\s*:\\s*(-?[0-9.]+)");

    private static final Pattern peakLevelPattern = Pattern.compile("Maximum amplitude\\s*:\\s*(-?[0-9.]+)");
    File originalFile;
    String name;
    int bitrate = -1;
    double peakLevel;
    boolean clipped;
    public double duration; // Duration in seconds
     
    String path;  // File path
    int channels = -1;
    int sampleRate = -1;


    // Constructor that accepts a java.io.File object
    public AudioFile(File tmpDir, File file) {
        this.originalFile = file;
        this.path = file.getAbsolutePath();
        this.name = file.getAbsolutePath().replace(tmpDir.getAbsolutePath() + "/", "");
    }

    public void initWithStats(String output) {
        Matcher matcher = peakLevelPattern.matcher(output);
        if(matcher.find()) {
            double amplitude = Double.parseDouble(matcher.group(1));
            // Convert amplitude to dBFS
            peakLevel = (amplitude == 0) ? Double.NEGATIVE_INFINITY : 20 * Math.log10(amplitude);
        } else {
            peakLevel = Double.NaN; // Indicates that the peak level could not be determined
        }

        matcher = durationPattern.matcher(output);
        if(matcher.find()) {
            duration = Double.parseDouble(matcher.group(1));
        } else {
            duration = Double.NaN; // Indicates that the peak level could not be determined
        }
    }

    public void initWithInfo(String output) {
        // Using regex to extract bitrate from the soxi output
        Matcher matcher = bitratePattern.matcher(output);
        if(matcher.find()) {
            bitrate = Integer.parseInt(matcher.group(1));
        }
        matcher = channelsPattern.matcher(output);
        if(matcher.find()) {
            channels = Integer.parseInt(matcher.group(1));
        }
        matcher = sampleRatePattern.matcher(output);
        if(matcher.find()) {
            sampleRate = Integer.parseInt(matcher.group(1));
        }
    }
}
