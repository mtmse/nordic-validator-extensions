package org.daisy.validator.audiocheck;

import org.apache.log4j.Logger;
import org.daisy.validator.report.Issue;
import org.daisy.validator.schemas.GuidelineExt;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AudioFiles {
    private static final Logger logger = Logger.getLogger(AudioFiles.class.getName());
    private static final Pattern maxAmpPattern = Pattern.compile("Maximum amplitude[ ]*:[ ]*(-?[0-9.]+)");
    private static final Pattern silencePatternStart = Pattern.compile("silencedetect.*silence_start: (-?[0-9.]+)");
    private static final Pattern silencePatternEnd = Pattern.compile("silencedetect.*silence_end: (-?[0-9.]+)");
    private final List<AudioFile> audioFiles;
    private final Set<Issue> errorList;
    private final SoundQualityCheckConfiguration configuration;

    public AudioFiles(File tmpDir, List<File> list, SoundQualityCheckConfiguration configuration) {
        audioFiles = new ArrayList<>();
        errorList = new HashSet<>();

        this.configuration = configuration;

        Instant workStart = Instant.now();

        // Initialize the audio files
        for (File file : list) {
            AudioFile audioFile = new AudioFile(tmpDir, file);
            audioFile.initWithStats(bashExecute("sox " + audioFile.path + " -n stat"));
            audioFile.initWithInfo(bashExecute("soxi " + audioFile.path));
            audioFiles.add(audioFile);
        }

        System.out.println("Init done in " + LocalTime.MIDNIGHT.plus(Duration.between(workStart, Instant.now())).format(DateTimeFormatter.ofPattern("HH:mm:ss")));
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

            if (audioFile.sampleRate != 22050) {
                addError(audioFile.name, "Sample rate is not 22050 Hz (" + audioFile.sampleRate + " Hz)");
            }

            if (audioFile.channels != 1) {
                addError(audioFile.name, "Audio is not mono");
            }


            if (audioFile.bitrate != 33 && audioFile.bitrate != 48 && audioFile.bitrate != 128) {
                addError(audioFile.name, "Bitrate is not 33 kbit/s, 48 kbit/s or 128 kbit/s (" + audioFile.bitrate + " kbit/s)");
            }

            if (audioFile.peakLevel < configuration.getPeekLevelThreshold()) {  // Assuming -3dBFS is the threshold
                addError(audioFile.name,
                    String.format(
                        "Audio file peek level does not exceed %.2f dBFS (%.2f dBFS)",
                        configuration.getPeekLevelThreshold(),
                        audioFile.peakLevel
                    )
                );
            }
        }

        Instant workStart = Instant.now();
        for (AudioFile audioFile : audioFiles) {
            String filePath = audioFile.originalFile.getAbsolutePath();

            Map<Double, Double> peakOverTime = getPeakLevelsOverTime(filePath);
            boolean clipping = false;
            double startTime = 0;
            List<String> clippingList = new ArrayList<>();

            for (Map.Entry<Double, Double> peak : peakOverTime.entrySet()) {
                if (!clipping && peak.getValue() >= configuration.getClippingLevelThreshold()) {
                    clipping = true;
                    startTime = peak.getKey();
                }
                if (clipping && peak.getValue() < configuration.getClippingLevelThreshold()) {
                    String start = LocalTime.MIDNIGHT.plus(Duration.of((long)(startTime * 1000), ChronoUnit.MILLIS)).format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                    String end = LocalTime.MIDNIGHT.plus(Duration.of((long)(peak.getKey() * 1000), ChronoUnit.MILLIS)).format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));

                    clippingList.add(start + "-" + end);
                    clipping = false;
                }
            }

            if (!clippingList.isEmpty()) {
                addError(audioFile.name, "Clipping detected at timestamps: " + clippingList);
            }

            /*
            if (checkClipping(filePath, "")) {
                addError(audioFile.name, "Clipping detected in audio file");
            }
            */
        }
        System.out.println("Clipping done in " + LocalTime.MIDNIGHT.plus(Duration.between(workStart, Instant.now())).format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        workStart = Instant.now();
        for (AudioFile audioFile : audioFiles) {
            String filePath = audioFile.originalFile.getAbsolutePath();

            List<Double> unevenPeakTimestamps = getUnevenPeakTimestamps(filePath, audioFile.peakLevel, audioFile.duration);
            if (!unevenPeakTimestamps.isEmpty()) {
                List<String> peakList = new ArrayList<>();
                for (Double peak : unevenPeakTimestamps) {
                    String start = LocalTime.MIDNIGHT.plus(Duration.of(peak.intValue(), ChronoUnit.SECONDS)).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    String end = LocalTime.MIDNIGHT.plus(Duration.of(peak.intValue() + 5, ChronoUnit.SECONDS)).format(DateTimeFormatter.ofPattern("HH:mm:ss"));

                    peakList.add(start + "-" + end);
                }
                addError(audioFile.name,  "Uneven peak levels detected at timestamps: " + peakList);
            }
        }
        System.out.println("UnevenPeaks done in " + LocalTime.MIDNIGHT.plus(Duration.between(workStart, Instant.now())).format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        workStart = Instant.now();
        List<String> inconsistentPeakFiles = checkPeakLevelsConsistency();
        for (String filename : inconsistentPeakFiles) {
            addError(filename, "Peak level is inconsistent with other audio files");
        }
        System.out.println("Inconsistent peaks done in " + LocalTime.MIDNIGHT.plus(Duration.between(workStart, Instant.now())).format(DateTimeFormatter.ofPattern("HH:mm:ss")));


/*
            This test is currently not working.

            List<Double> abruptChanges = detectAbruptChanges(filePath, audioFile.duration);
            if (!abruptChanges.isEmpty()) {
                addError(audioFile.name, "Abrupt changes detected at timestamps: " + abruptChanges);
            }
 */

        workStart = Instant.now();
        for (AudioFile audioFile : audioFiles) {
            String filePath = audioFile.originalFile.getAbsolutePath();
            double initialSilencePeak = getInitialSilencePeak(filePath);
            if (initialSilencePeak > configuration.getInitialSilenceThreshold()) {  // Assuming -50dBFS is the threshold
                addError(audioFile.name, String.format(
                    "Background noise exceeds threshold %.2f dBFS (%.2f dBFS)",
                    configuration.getInitialSilenceThreshold(),
                    initialSilencePeak
                ));
            }
        }
        System.out.println("Background noise done in " + LocalTime.MIDNIGHT.plus(Duration.between(workStart, Instant.now())).format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        workStart = Instant.now();
        for (AudioFile audioFile : audioFiles) {
            String filePath = audioFile.originalFile.getAbsolutePath();
            Map<Double, Double> longSilences = getLongSilences(filePath);
            if (!longSilences.isEmpty()) {
                List<String> peakList = new ArrayList<>();
                for (Map.Entry<Double, Double> peak : longSilences.entrySet()) {

                    String start = LocalTime.MIDNIGHT.plus(Duration.of(peak.getKey().intValue(), ChronoUnit.SECONDS)).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    String end = LocalTime.MIDNIGHT.plus(Duration.of(peak.getValue().intValue() + 5, ChronoUnit.SECONDS)).format(DateTimeFormatter.ofPattern("HH:mm:ss"));

                    peakList.add(start + "-" + end);
                }
                addError(audioFile.name, "Long silences detected at intervals: " + peakList);
            }
        }
        System.out.println("Long silences done in " + LocalTime.MIDNIGHT.plus(Duration.between(workStart, Instant.now())).format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    private boolean isMPEGAudioLayer3(String filePath) {
        String format = bashExecute("file \"" + filePath + "\"").toLowerCase();
        return format.contains("layer iii") || format.contains("layer 3");
    }

    private Map<Double, Double> getLongSilences(String filePath) {
        String command = String.format(
            "ffmpeg -i " + filePath + " -af silencedetect=noise=%fdB:d=%f -f null -",
            configuration.getLongSilenceLevel(),
            configuration.getLongSilenceDuration()
        );
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
            if (Math.abs(entry.getValue() - averagePeak) > configuration.getAveragePeekLevelDifference()) {
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

    private List<Double> getUnevenPeakTimestamps(String filePath, double peakLevelInDbFs, double duration) {
        List<Double> unevenTimestamps = new ArrayList<>();

        logger.debug("Debugging getUnevenPeakTimestamps");
        logger.debug("Total Audio Duration: " + duration + "s");
        logger.debug("Peak Level of Entire File: " + peakLevelInDbFs + " dBFS");

        for (double start = 0; start < duration; start += (configuration.getUnevenPeekRangeInSeconds() - configuration.getUnevenPeekOverlap())) {
            double end = Math.min(start + configuration.getUnevenPeekRangeInSeconds(), duration);
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

                if (difference > configuration.getUnevenPeekDifferenceThreshold()) {
                    logger.debug("Uneven peak detected at timestamp: " + start + "s");
                    unevenTimestamps.add(start);
                }
            } else {
                logger.debug("No peak value found for segment from " + start + "s to " + end + "s");
            }
        }

        return unevenTimestamps;
    }

    public boolean checkClipping(String filePath, String trimStatement) {
        String output = bashExecute("sox " + filePath + " -n --norm -R gain 0.1 " + trimStatement);

        if(output.contains("clipped")) {
            return true; // Clipping found
        }
        return false; // No clipping
    }

    private Map<Double, Double> getPeakLevelsOverTime(String filePath) throws Exception {
        String logFile =  filePath + ".log";
        bashExecute(
            "ffmpeg -i " + filePath + " -af astats=metadata=1:reset=1:length=0.1," +
            "ametadata=print:key=lavfi.astats.Overall.Peak_level:file=" + logFile + " -f null -"
        );
        File resultFile = new File(logFile);
        Map<Double, Double> peakOverTime = new LinkedHashMap<>();
        if (!resultFile.exists()) return peakOverTime;

        BufferedReader br = new BufferedReader(new FileReader(resultFile));
        String line;
        Double time = Double.MIN_VALUE;

        while ((line = br.readLine()) != null) {
            if (line.startsWith("frame")) {
                int val = line.lastIndexOf(":");
                time = Double.parseDouble(line.substring(val + 1));
            }
            if (line.startsWith("lavfi")) {
                int val = line.lastIndexOf("=");
                try {
                    double peak = Double.parseDouble(line.substring(val + 1));
                    peakOverTime.put(time, peak);
                } catch (NumberFormatException nfe) {}
            }
        }
        br.close();

        new File(logFile).delete();

        return peakOverTime;
    }

    /*
    private List<Double> getClippingTimestamps(String filePath, double duration) throws Exception {
        List<Double> unevenTimestamps = new ArrayList<>();
        double step = 60.0; // overlap of 60 seconds, adjust as needed

        for (double start = 0; start < duration; start += step) {
            double end = Math.min(start, duration);
            double segmentDuration = end - start;
            if (checkClipping(filePath, "trim " + start + " " + segmentDuration)) {
                unevenTimestamps.add(start);
            }
        }
        return unevenTimestamps;
    }
    */
}