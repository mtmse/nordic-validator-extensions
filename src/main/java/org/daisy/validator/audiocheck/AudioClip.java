package org.daisy.validator.audiocheck;

import org.daisy.validator.Util;
import org.daisy.validator.report.Issue;
import org.daisy.validator.schemas.Guideline;
import org.daisy.validator.schemas.GuidelineExt;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

public class AudioClip {
    private final static String VOWELS = "aouåeiyäööæéèëêíìïîáàâóòôúùüûýÿæøAOUÅEIYÄÖÆØ";
    private final static String PUNCTUATION = "-.,\"";
    private final static String SHORT_NUMBER = "0123567";
    private final static String LONG_NUMBER = "489";
    private long vowelCount = 0;
    private String text;
    private boolean pagenum = false;
    private long clipBegin;
    private long clipEnd;

    private String smilFile;
    private String contentFile;
    private String paragraphId;
    private ReportConfiguration reportConfiguration;


    public AudioClip(String smilFile, Element el, long clipBegin, long clipEnd) {

        Element parent = (Element) el.getParentNode();
        if (
            parent.getNodeName().equalsIgnoreCase("seq") &&
            parent.getParentNode().getNodeName().equalsIgnoreCase("par")
        ) {
            parent = (Element) parent.getParentNode();
        }

        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeName().equals("text")) {
                Element textNode = (Element) children.item(i);
                String[] uri = textNode.getAttribute("src").split("#");
                String filename = new File(new File(smilFile).getParentFile(), uri[0]).getPath();
                this.contentFile = filename;
                this.paragraphId = uri[1];
                break;
            }
        }
        this.smilFile = smilFile;
        this.clipBegin = clipBegin;
        this.clipEnd = clipEnd;
    }

    public String getSmilFile() {
        return smilFile;
    }

    public String getContentFile() {
        return contentFile;
    }

    public String getParagraphId() {
        return paragraphId;
    }

    private long countVowels(String text) {
        long numVowels = 0;

        if (text.isBlank()) {
            return 1;
        }

        if (isNumeric(text) && pagenum) {
            numVowels += 2;
        }

        String[] chars = text.split("");
        for (String c : chars) {
            if (c.isBlank()) {
                continue;
            }
            if (VOWELS.contains(c) || PUNCTUATION.contains(c) || SHORT_NUMBER.contains(c)) {
                numVowels++;
            }
            if (LONG_NUMBER.contains(c)) {
                numVowels += 2;
            }
        }

        if (text.matches("\\d\\d\\d")) {
            numVowels += 2;
        }
        if (text.matches("\\d\\d\\d\\d+")) {
            numVowels += 4;
        }
        if (text.toLowerCase().contains("www")) {
            numVowels += 3;
        }
        if (text.toLowerCase().contains("http")) {
            numVowels += 4;
        }
        if (text.toLowerCase().contains("https")) {
            numVowels += 5;
        }
        if (text.contains("@")) {
            numVowels += 3;
        }
        if (text.contains("%")) {
            numVowels += 2;
        }
        if (text.matches("\\d ff")) {
            numVowels += 4;
        }

        return numVowels == 0 ? 1 : numVowels;
    }

    private boolean isNumeric(String text) {
        try {
            Double.parseDouble(text);
            return true;
        } catch (NumberFormatException nfe) {
            return false;
        }
    }

    public void initVowels() {
        this.vowelCount = countVowels(text);
    }

    public long getDuration() {
        return this.clipEnd - this.clipBegin;
    }

    private double getMeanDurationPerSyllable(ReportConfiguration reportConfiguration) {
        if (reportConfiguration.getShortLimit() < this.vowelCount) {
            return reportConfiguration.getMeanDurationPerSyllableNormal();
        } else {
            return reportConfiguration.getMeanDurationPerSyllableShort();
        }
    }

    public double getExpectedDuration(ReportConfiguration reportConfiguration) {
        return this.getMeanDurationPerSyllable(reportConfiguration) * this.vowelCount;
    }

    public double getDurationPerVowels() {
        return ((double) this.getDuration() / (double) this.vowelCount);
    }

    public double getDiff(ReportConfiguration reportConfiguration) {
        return Math.abs(this.getDurationPerVowels() - this.getMeanDurationPerSyllable(reportConfiguration));
    }

    public boolean check(ReportConfiguration reportConfiguration) {
        if (reportConfiguration.getShortLimit() < this.vowelCount) {
            return this.getDiff(reportConfiguration) < reportConfiguration.getDiffLimit();
        } else {
            return this.getDiff(reportConfiguration) < reportConfiguration.getDiffLimitShort();
        }
    }

    public static void validateAudioClips(
        ReportConfiguration reportConfiguration,
        List<AudioClip> audioClips,
        File parentDir,
        Set<Issue> errorList,
        String packageValidationType
    ) throws Exception {
        Map<String, List<AudioClip>> audioClipsByFile = new HashMap<>();
        for (AudioClip ac : audioClips) {
            if (!audioClipsByFile.containsKey(ac.getContentFile())) {
                audioClipsByFile.put(ac.getContentFile(), new ArrayList<>());
            }
            audioClipsByFile.get(ac.getContentFile()).add(ac);
        }

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        XPath xPath = XPathFactory.newInstance().newXPath();

        for (Map.Entry<String, List<AudioClip>> entry : audioClipsByFile.entrySet()) {
            Document xmlDocument = null;
            try {
                xmlDocument = db.parse(new File(parentDir, entry.getKey()));
            } catch (SAXParseException saxEx) {
                String lineIn = String.format("(Line: %05d Column: %05d) ", saxEx.getLineNumber(), saxEx.getColumnNumber());
                errorList.add(
                    new Issue("", "[" + packageValidationType + "] " + lineIn + saxEx.getMessage(),
                        entry.getKey(),
                        packageValidationType,
                        Issue.ERROR_ERROR
                    )
                );
                return;
            }

            for (AudioClip ac : entry.getValue()) {
                XPathExpression xPathContent = xPath.compile("//*[@id='" + ac.getParagraphId() +"']");
                Element element = (Element) xPathContent.evaluate(xmlDocument, XPathConstants.NODE);
                if (
                    element.getAttribute("class").equals("pagenum") ||
                    element.getAttribute("class").equals("noteref") ||
                    element.getAttribute("class").startsWith("page-")
                ) {
                    ac.pagenum = true;
                }
                if (!element.getParentNode().getNodeName().equals("h2")) {
                    ac.pagenum = true;
                }

                ac.setText(element.getTextContent().trim());
                ac.initVowels();
                if (!ac.check(reportConfiguration)) {
                    String line = "";
                    line += "Incorrect length of " + ac.getParagraphId() + " in " + ac.getContentFile();
                    line += " - Expected length " + Util.formatTime((long)ac.getExpectedDuration(reportConfiguration));
                    line += " actual length " + Util.formatTime(ac.getDuration());
                    errorList.add(
                        new Issue("", "[" + GuidelineExt.AUDIO_CHECK + "] " + line,
                            ac.getContentFile(),
                            Guideline.SMIL,
                            Issue.ERROR_ERROR
                        )
                    );
                }
            }
        }

    }

    private void setText(String text) {
        this.text = text;
    }
}
