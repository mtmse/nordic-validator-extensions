package org.daisy.validator.audiocheck;

import org.apache.log4j.Logger;
import org.daisy.validator.report.Issue;
import org.daisy.validator.schemas.GuidelineExt;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AudioFiles {
    private static final Logger logger = Logger.getLogger(AudioFiles.class.getName());
    private static final Pattern clippingPattern = Pattern.compile("\\d+\\s+\\[CLIPPING\\]");
    private static final Pattern maxAmpPattern = Pattern.compile("Maximum amplitude[ ]*:[ ]*(-?[0-9.]+)");
    private static final Pattern silencePatternStart = Pattern.compile("silencedetect.*silence_start: (-?[0-9.]+)");
    private static final Pattern silencePatternEnd = Pattern.compile("silencedetect.*silence_end: (-?[0-9.]+)");
    private static final Pattern segmentRMSPattern = Pattern.compile("RMS[ ]*amplitude:[ ]*(-?[0-9.]+)");
    private final List<AudioFile> audioFiles;
    private final Set<Issue> errorList;

    private final File tmpDir;

    public AudioFiles(File tmpDir, List<File> list) {
        audioFiles = new ArrayList<>();
        errorList = new HashSet<>();
        this.tmpDir = tmpDir;
        
        // Initialize the audio files
        for (File file : list) {
            AudioFile audioFile = new AudioFile(tmpDir, file);
            audioFile.initWithStats(bashExecute("sox " + audioFile.path + " -n stat"));
            audioFile.initWithInfo(bashExecute("soxi " + audioFile.path));
            audioFiles.add(audioFile);
        }
    }

    private void addError(String filename, String issue) {
        errorList.add(new Issue("", "[" + GuidelineExt.AUDIO_QUALITY + "] " + issue, filename, GuidelineExt.SMIL, Issue.ERROR_ERROR));
    }

    public List<AudioFile> getAudioFiles() {
        return audioFiles;
    }

    public Set<Issue> getErrorList() {
        return errorList;
    }

    public void validate() throws Exception {
        for (AudioFile audioFile : audioFiles) {
            String filePath = audioFile.originalFile.getAbsolutePath();
            
            if (!isMPEGAudioLayer3(filePath)) {
                addError(audioFile.name, "File is not MPEG Audio Layer 3 format");
            }
            
            if (audioFile.sampleRate != 22050 || audioFile.channels != 1) {
                addError(audioFile.name,
                "Sample rate is not 22050 Hz or it's not mono (SampleRate " + audioFile.sampleRate +
                    " Channels " + audioFile.channels + ")"
                );
            }

            if (audioFile.bitrate != 33 && audioFile.bitrate != 48 && audioFile.bitrate != 128) {
                addError(audioFile.name, "Bitrate is not valid (not 33/48/128 kbit/s) current " + audioFile.bitrate);
            }

            if (audioFile.peakLevel < -3) {  // Assuming -3dBFS is the threshold
                addError(audioFile.name, "Audio file peek level exceeds -3 value is " + audioFile.peakLevel + " dBFS");
            }

            // Disable functions not yet tested and reviewed.
            if (checkClipping(filePath, "")) {
                addError(audioFile.name, "Clipping detected in audio file");

                List<Double> clippingTimestamps = getClippingTimestamps(filePath, audioFile.duration);
                if (!clippingTimestamps.isEmpty()) {
                    addError(audioFile.name, "Clipping detected at timestamps: " + clippingTimestamps);
                }
            }

            List<Double> unevenPeakTimestamps = getUnevenPeakTimestamps(filePath, audioFile.peakLevel, audioFile.duration);
            if (!unevenPeakTimestamps.isEmpty()) {
                addError(audioFile.name, "Uneven peak levels detected at timestamps: " + unevenPeakTimestamps);
            }

/*
            This test is currently not working.

            List<Double> abruptChanges = detectAbruptChanges(filePath, audioFile.duration);
            if (!abruptChanges.isEmpty()) {
                addError(audioFile.name, "Abrupt changes detected at timestamps: " + abruptChanges);
            }
 */

            Map<Double, Double> longSilences = getLongSilences(filePath);
            if (!longSilences.isEmpty()) {
                addError(audioFile.name, "Long silences detected at intervals: " + longSilences);
            }
            
            double initialSilencePeak = getInitialSilencePeak(filePath);
            if (initialSilencePeak > -50) {  // Assuming -50dBFS is the threshold
                addError(audioFile.name, "Background noise exceeds threshold at " + initialSilencePeak + " dBFS");
            }
        }

        List<String> inconsistentPeakFiles = checkPeakLevelsConsistency();
        for (String filename : inconsistentPeakFiles) {
            addError(filename, "The peak level is inconsistent with other audio files");
        }
    }

    private boolean isMPEGAudioLayer3(String filePath) {
        String format = bashExecute("file \"" + filePath + "\"").toLowerCase();
        return format.contains("layer iii") || format.contains("layer 3");
    }

    private Map<Double, Double> getLongSilences(String filePath) {
        String command = "ffmpeg -i " + filePath + " -af silencedetect=noise=-50dB:d=8 -f null -";
        String output = bashExecute(command);
        
        // Debug: print the ffmpeg output for clarity
        logger.debug(output);
        
        // Detecting the start and end timestamps of the silence segments
        Matcher matcherStart = silencePatternStart.matcher(output);
        Matcher matcherEnd = silencePatternEnd.matcher(output);
        
        Map<Double, Double> silences = new HashMap<>();
        double lastStart = 0;
        while(matcherStart.find()) {
            lastStart = Double.parseDouble(matcherStart.group(1));
            if(matcherEnd.find()) {
                double end = Double.parseDouble(matcherEnd.group(1));
                silences.put(lastStart, end);
            }
        }
        
        return silences;
    }

    private double getInitialSilencePeak(String filePath) {
        String output = bashExecute("sox " + filePath + " -n trim 0 0.2 stat");
        
        // Search for peak level after "Maximum amplitude:"
        Matcher matcher = maxAmpPattern.matcher(output);
        
        if (matcher.find()) {
            double linearAmplitude = Double.parseDouble(matcher.group(1));
            // Convert linear amplitude to dBFS
            double dBFS = 20 * Math.log10(linearAmplitude);
            return dBFS;
        } else {
            return Double.POSITIVE_INFINITY; // Representing an invalid peak
        }
    }
    
    private String bashExecute(String command) {
        StringBuilder output = new StringBuilder();
        
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"bash", "-c", command + " 2>&1"});
            
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            
            String line;
            
            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                // Handle non-zero exit code
                logger.debug("Command failed with exit code: " + exitCode);
                return "";
            }
            
            logger.debug(command);
            logger.debug(output.toString());
            
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return "";
        }
        
        return output.toString();
    }

    private List<String> checkPeakLevelsConsistency() {
        double totalPeak = 0;
        int validFilesCount = 0;
        Map<String, Double> filePeakMap = new HashMap<>();
        
        // 1. Gather peak levels for all the audio files and calculate total peak
        for (AudioFile audioFile : audioFiles) {
            double peak = audioFile.peakLevel;
            if (peak != Double.NEGATIVE_INFINITY) {  // Assuming this value indicates an error in getPeakLevel
                totalPeak += peak;
                validFilesCount++;
                filePeakMap.put(audioFile.name, peak);
            }
        }
        
        if (validFilesCount == 0) return new ArrayList<>();  // Return an empty list if no valid files found
        
        // 2. Calculate the average peak level
        double averagePeak = totalPeak / validFilesCount;
        
        List<String> inconsistentFiles = new ArrayList<>();
        
        // 3. Compare each audio file's peak level to the average
        for (Map.Entry<String, Double> entry : filePeakMap.entrySet()) {
            if (Math.abs(entry.getValue() - averagePeak) > 0.5) {
                inconsistentFiles.add(entry.getKey());
            }
        }
        
        return inconsistentFiles;
    }
    
/*
    public List<Double> detectAbruptChanges(String filePath, double duration) {
        List<Double> abruptChangeTimestamps = new ArrayList<>();
        double previousRMS = 0;

        final double SEGMENT_DURATION = 0.05; // 50ms
        final double RMS_THRESHOLD = 0.1; // Define a suitable threshold

        for (double start = 0; start < duration; start += SEGMENT_DURATION) {
            double segmentRMS = getSegmentRMS(filePath, start, SEGMENT_DURATION);
            if (Math.abs(segmentRMS - previousRMS) > RMS_THRESHOLD) {
                abruptChangeTimestamps.add(start);
            }
            previousRMS = segmentRMS;
        }

        return abruptChangeTimestamps;
    }

    private double getSegmentRMS(String filePath, double start, double duration) {
        String output = bashExecute("sox " + filePath + " -n trim " + start + " " + duration + " stat -rms");

        Matcher matcher = segmentRMSPattern.matcher(output);

        if(matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        } else {
            return 0;
        }
    }
 */

    private boolean checkClipping(String filePath, String trimStatement) {
        String output = bashExecute("sox " + filePath + " -n --norm -R gain 0.1 " + trimStatement);

        if(output.contains("clipped")) {
            return true; // Clipping found
        } else {
            return false; // No clipping
        }
    }

    private List<Double> getUnevenPeakTimestamps(String filePath, double peakLevelInDbFs, double duration) {
        List<Double> unevenTimestamps = new ArrayList<>();

        logger.debug("Debugging getUnevenPeakTimestamps");
        logger.debug("Total Audio Duration: " + duration + "s");
        logger.debug("Peak Level of Entire File: " + peakLevelInDbFs + " dBFS");

        double overlap = 5.0; // overlap of 5 seconds, adjust as needed

        for (double start = 0; start < duration; start += (30 - overlap)) {
            double end = Math.min(start + 30, duration);
            double segmentDuration = end - start;
            String output = bashExecute("sox " + filePath + " -n trim " + start + " " + segmentDuration + " stat");

            Matcher matcher = maxAmpPattern.matcher(output);

            if(matcher.find()) {
                double segmentAmplitude = Double.parseDouble(matcher.group(1));

                // Convert amplitude to dBFS
                double segmentPeakInDbFs = (segmentAmplitude == 0)
                        ? Double.NEGATIVE_INFINITY
                        : 20 * Math.log10(segmentAmplitude);

                logger.debug("Segment Peak from " + start + "s to " + end + "s: " + segmentPeakInDbFs + " dBFS");

                double difference = Math.abs(segmentPeakInDbFs - peakLevelInDbFs);
                logger.debug("Difference between segment peak and file peak: " + difference);

                if (difference > 0.05) {
                    logger.debug("Uneven peak detected at timestamp: " + start + "s");
                    unevenTimestamps.add(start);
                }
            } else {
                logger.debug("No peak value found for segment from " + start + "s to " + end + "s");
            }
        }

        return unevenTimestamps;
    }

    private List<Double> getClippingTimestamps(String filePath, double duration) {
        List<Double> unevenTimestamps = new ArrayList<>();

        double step = 5.0; // overlap of 5 seconds, adjust as needed

        for (double start = 0; start < duration; start += step) {
            double end = Math.min(start, duration);
            double segmentDuration = end - start;
            if (checkClipping(filePath, "trim " + start + " " + segmentDuration)) {
                unevenTimestamps.add(start);
            }
        }
        return unevenTimestamps;
    }
}