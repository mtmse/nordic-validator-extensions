package org.daisy.validator;

import org.daisy.validator.audiocheck.AudioClip;
import org.daisy.validator.audiocheck.SentenceCheckConfiguration;
import org.daisy.validator.audiocheck.SoundQualityCheckConfiguration;
import org.daisy.validator.report.Issue;
import org.daisy.validator.schemas.Guideline;
import org.daisy.validator.schemas.GuidelineExt;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

public class Daisy202Files {
    private String nccFile;
    private String masterSmil;
    private List<String> contentFiles = new ArrayList<>();
    private Map<String, Long> audioFiles = new HashMap<>();
    private List<String> smilFiles = new ArrayList<>();
    private Set<Issue> errorList = new HashSet<>();
    private final ExecutorService executor;
    private final CompletionService<List<Issue>> completionService;
    private int submittedWork = 0;
    private final File daisyDir = UtilExt.createTempDirectory();
    private final File schemaDir = UtilExt.createTempDirectory();
    private final Guideline guideline;
    private List<AudioClip> audioClips = new ArrayList();

    public Daisy202Files(String filename, String issue) throws Exception {
        this(filename, issue, null);
    }

    public Daisy202Files(String filename, String issue, Guideline guideline) throws Exception {
        executor = null;
        completionService = null;
        this.guideline = guideline;
        errorList.add(new Issue("", issue, filename, GuidelineExt.NCC, Issue.ERROR_FATAL));
    }

    public Daisy202Files(ZipFile zipFile, int threads, Guideline guideline) throws Exception {
        this.guideline = guideline;
        this.executor = Executors.newFixedThreadPool(threads);
        this.completionService = new ExecutorCompletionService<>(executor);

        Iterator zeIter = zipFile.entries().asIterator();
        while (zeIter.hasNext()) {
            ZipEntry ze = (ZipEntry) zeIter.next();
            if (!new File(daisyDir, ze.getName()).getParentFile().exists()) {
                new File(daisyDir, ze.getName()).getParentFile().mkdirs();
            }
            if (ze.isDirectory()) {
                new File(daisyDir, ze.getName()).mkdirs();
            } else if (ze.getName().equals("ncc.html") || ze.getName().endsWith("/ncc.html")) {
                nccFile = ze.getName();
                UtilExt.writeFileWithoutDoctype(
                    new File(daisyDir, nccFile), zipFile.getInputStream(zipFile.getEntry(nccFile))
                );
            } else if (ze.getName().equals("master.smil") || ze.getName().endsWith("/master.smil")) {
                masterSmil = ze.getName();
                UtilExt.writeFileWithoutDoctype(
                    new File(daisyDir, masterSmil), zipFile.getInputStream(zipFile.getEntry(masterSmil))
                );
            } else if(ze.getName().endsWith(".smil")) {
                smilFiles.add(ze.getName());
                UtilExt.writeFileWithoutDoctype(
                    new File(daisyDir, ze.getName()), zipFile.getInputStream(zipFile.getEntry(ze.getName()))
                );
            } else {
                if (ze.getName().endsWith(".html") || ze.getName().endsWith(".xhtml")) {
                    UtilExt.writeFileWithoutDoctype(
                        new File(daisyDir, ze.getName()), zipFile.getInputStream(zipFile.getEntry(ze.getName()))
                    );
                } else {
                    UtilExt.writeFile(
                        new File(daisyDir, ze.getName()), zipFile.getInputStream(zipFile.getEntry(ze.getName()))
                    );

                    if (ze.getName().endsWith(".mp3") || ze.getName().endsWith(".mp2") || ze.getName().endsWith(".wav")) {
                        AudioFileFormat audioFormat = AudioSystem.getAudioFileFormat(new File(daisyDir, ze.getName()));
                        long frames = audioFormat.getFrameLength() + 1;
                        long durationInMilliSeconds = Math.round((frames * 1000) / audioFormat.getFormat().getFrameRate());
                        audioFiles.put(ze.getName(), durationInMilliSeconds);
                    }
                }

                contentFiles.add(ze.getName());
           }
        }

        unpackSchemas();
    }

    private void validateFile(String filename, String validationType) throws Exception {
        if(completionService == null) return;
        completionService.submit(new ValidateFile(
            daisyDir,
            filename,
            new File(schemaDir, guideline.getSchema(validationType).getFilename()),
            validationType
        ));
        submittedWork++;
    }

    public void unpackSchemas() throws Exception {
        UtilExt.unpackSchemaDir(guideline.getSchemaPath(), schemaDir);
        UtilExt.unpackSchemaDir("relaxngcommon", schemaDir);
    }

    public void cleanUp() {
        if(DevFlags.CLEAN_UP_WORK_DIR) {
            UtilExt.deleteDirectory(daisyDir);
        }
        if(DevFlags.CLEAN_UP_SCHEMA_DIR) {
            UtilExt.deleteDirectory(schemaDir);
        }
    }

    public void validate(SentenceCheckConfiguration sentenceCheckConfiguration) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        XPath xPath = XPathFactory.newInstance().newXPath();
        String expRel = "//*/@href | //*/@src | //*/@cite | //*/@longdesc | //object/@data | //form/@action | //head/@profile";
        String expId = "//*[@id]";
        XPathExpression xPathExpRel = xPath.compile(expRel);
        XPathExpression xPathExpId = xPath.compile(expId);

        Set<Issue> uris = new HashSet<>();
        Set<String> ids = new HashSet<>();

        List[] res = findLinks(db, xPathExpRel, xPathExpId, nccFile);
        uris.addAll(res[0]);
        ids.addAll(res[1]);
        validateFile(nccFile, GuidelineExt.NCC);
        if (masterSmil != null) {
            res = findLinks(db, xPathExpRel, xPathExpId, masterSmil);
            uris.addAll(res[0]);
            ids.addAll(res[1]);
            validateFile(masterSmil, GuidelineExt.MSMIL);
        }
        for (String smilFile : smilFiles) {
            res = findLinks(db, xPathExpRel, xPathExpId, smilFile);
            uris.addAll(res[0]);
            ids.addAll(res[1]);
            validateFile(smilFile, GuidelineExt.SMIL);
        }
        for (String contentFile : contentFiles) {
            if (!contentFile.endsWith(".html") && !contentFile.endsWith(".xhtml")) {
                continue;
            }
            res = findLinks(db, xPathExpRel, xPathExpId, contentFile);
            uris.addAll(res[0]);
            ids.addAll(res[1]);
        }

        for (Issue uri : uris) {
            if (contentFiles.contains(uri.getLocation())) {
                continue;
            }
            if (smilFiles.contains(uri.getLocation())) {
                continue;
            }
            if (ids.contains(uri.getLocation())) {
                continue;
            }

            errorList.add(uri);
        }

        validateAudio(sentenceCheckConfiguration);

        int received = 0;

        String validationType = Guideline.XHTML;

        boolean errors = false;
        while(received < submittedWork && !errors) {
            Future<List<Issue>> resultFuture = completionService.take();
            try {
                errorList.addAll(resultFuture.get());
                received++;
            } catch (ExecutionException ee) {
                if (ee.getCause() instanceof SAXParseException) {
                    SAXParseException saxEx = (SAXParseException) ee.getCause();
                    String lineIn = String.format("(Line: %05d Column: %05d) ", saxEx.getLineNumber(), saxEx.getColumnNumber());
                    errorList.add(
                        new Issue("", "[" + validationType + "] " + lineIn + saxEx.getMessage(),
                            "",
                            validationType,
                            Issue.ERROR_ERROR
                        ));
                    errors = true;
                } else {
                    errorList.add(
                        new Issue("", "[" + validationType + "] " + ee.getMessage(),
                            "",
                            validationType,
                            Issue.ERROR_ERROR
                        ));
                }
            } catch(Exception e) {
                errorList.add(
                    new Issue("", "[" + validationType + "] " + e.getMessage(),
                        "",
                        validationType,
                        Issue.ERROR_ERROR
                    ));
                errors = true;
            }
        }
        executor.shutdownNow();
    }


    private void validateAudio(SentenceCheckConfiguration sentenceCheckConfiguration) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        XPath xPath = XPathFactory.newInstance().newXPath();

        XPathExpression xPathExpTotalTime = xPath.compile("//meta[@name='ncc:totalTime']/@content");

        Document xmlDocument = null;
        try {
            xmlDocument = db.parse(new File(daisyDir, nccFile));
        } catch (FileNotFoundException fnfe) {
            errorList.add(
                new Issue("", "[" + Guideline.XHTML + "] " + fnfe.getMessage(),
                    nccFile,
                    Guideline.XHTML,
                    Issue.ERROR_ERROR
                ));
            return;
        } catch (SAXParseException saxEx) {
            String lineIn = String.format("(Line: %05d Column: %05d) ", saxEx.getLineNumber(), saxEx.getColumnNumber());
            errorList.add(
                new Issue("", "[" + Guideline.XHTML + "] " + lineIn + saxEx.getMessage(),
                    nccFile,
                    Guideline.XHTML,
                    Issue.ERROR_ERROR
                ));
            return;
        }

        String totalTimeStr = (String) xPathExpTotalTime.evaluate(xmlDocument, XPathConstants.STRING);
        long totalTime = UtilExt.parseMilliSeconds(totalTimeStr);

        XPathExpression xPathExpRel = xPath.compile("//*/@href");
        NodeList nodeList = (NodeList) xPathExpRel.evaluate(xmlDocument, XPathConstants.NODESET);
        Set<String> smilFiles = new LinkedHashSet<>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node n = nodeList.item(i);
            String s = n.getNodeValue();
            if (s.contains(":")) {
                continue;
            }
            if (s.contains("#")) {
                String filename = Util.getRelativeFilename(nccFile, s);
                smilFiles.add(filename.substring(0, filename.indexOf("#")));
            } else {
                String filename = Util.getRelativeFilename(nccFile, s);
                smilFiles.add(filename);
            }
        }

        XPathExpression xPathExpTotalElapsedTime = xPath.compile("//meta[@name='ncc:totalElapsedTime']/@content");
        XPathExpression xPathExptimeInThisSmil = xPath.compile("//meta[@name='ncc:timeInThisSmil']/@content");
        XPathExpression xPathExpAudio = xPath.compile(
            "//audio[@clip-begin] | //audio[@clip-end] | //audio[@clipBegin] | //audio[@clipEnd]"
        );

        long elapsedTime = 0;
        for (String smilFile : smilFiles) {
            Document smilDocument = null;
            try {
                smilDocument = db.parse(new File(daisyDir, smilFile));
            } catch (FileNotFoundException fnfe) {
                errorList.add(
                    new Issue("", "[" + Guideline.XHTML + "] " + fnfe.getMessage(),
                        nccFile,
                        Guideline.XHTML,
                        Issue.ERROR_ERROR
                    ));
                continue;
            } catch (SAXParseException saxEx) {
                String lineIn = String.format("(Line: %05d Column: %05d) ", saxEx.getLineNumber(), saxEx.getColumnNumber());
                errorList.add(
                    new Issue("", "[" + Guideline.XHTML + "] " + lineIn + saxEx.getMessage(),
                        nccFile,
                        Guideline.XHTML,
                        Issue.ERROR_ERROR
                    ));
                continue;
            }

            elapsedTime = validateSmilFile(
                smilDocument, smilFile, xPathExpTotalElapsedTime, xPathExptimeInThisSmil, xPathExpAudio, elapsedTime
            );
        }

        if (sentenceCheckConfiguration != null) {
            AudioClip.validateAudioClips(sentenceCheckConfiguration, audioClips, daisyDir, errorList, GuidelineExt.NCC);
        }

        if (Math.abs(elapsedTime - totalTime) > 500) {
            errorList.add(new Issue(
                "",
                "[" +Guideline.XHTML + "] Total time in metadata " + UtilExt.formatTime(totalTime) +
                        " does not equal total elapsed time " + UtilExt.formatTime(elapsedTime),
                nccFile,
                GuidelineExt.SMIL,
                Issue.ERROR_ERROR
            ));
        }
    }

    private long validateSmilFile(
            Document smilDocument,
            String smilFile,
            XPathExpression xPathExpTotalElapsedTime,
            XPathExpression xPathExpTimeInThisSmil,
            XPathExpression xPathExpAudio,
            long elapsedTime
    ) throws Exception {
        String totalTimeStr = (String) xPathExpTotalElapsedTime.evaluate(smilDocument, XPathConstants.STRING);
        long totalElapsedTime = UtilExt.parseMilliSeconds(totalTimeStr);

        if (!totalTimeStr.isBlank() && Math.abs(totalElapsedTime - elapsedTime) > 500) {
            createSmilError(smilFile,
                "Elapsed time before this smil time " + UtilExt.formatTime(elapsedTime) +
                " does not match the time in metadata " + UtilExt.formatTime(totalElapsedTime)
            );
        }

        long timeInThisSmilFile = 0;

        NodeList nodeList = (NodeList) xPathExpAudio.evaluate(smilDocument, XPathConstants.NODESET);
        for (int i = 0; i < nodeList.getLength(); i++) {
            Element el = (Element) nodeList.item(i);
            long beginning = 0;
            long ending = 0;
            if (el.hasAttribute("clipBegin") || el.hasAttribute("clipEnd")) {
                if (!el.hasAttribute("clipBegin") || !el.hasAttribute("clipEnd")) {
                    createSmilError(smilFile, "Missing clip beginning or end at " + el.getAttribute("src"));
                    continue;
                }
                beginning = UtilExt.parseMilliSeconds(el.getAttribute("clipBegin"));
                ending = UtilExt.parseMilliSeconds(el.getAttribute("clipEnd"));
            } else {
                if (!el.hasAttribute("clip-begin") || !el.hasAttribute("clip-end")) {
                    createSmilError(smilFile, "Missing clip beginning or end at " + el.getAttribute("src"));
                    continue;
                }
                beginning = UtilExt.parseMilliSeconds(el.getAttribute("clip-begin"));
                ending = UtilExt.parseMilliSeconds(el.getAttribute("clip-end"));
            }

            String filename = Util.getRelativeFilename(smilFile, el.getAttribute("src"));

            if (!audioFiles.containsKey(filename) || beginning > audioFiles.get(filename)) {
                createSmilError(smilFile, "Beginning of clip is not in audio " + el.getAttribute("src"));
            }
            if (!audioFiles.containsKey(filename) || ending > audioFiles.get(filename)) {
                createSmilError(smilFile, "Ending of clip is not in audio " + el.getAttribute("src"));
            }

            audioClips.add(new AudioClip(smilFile, el, beginning, ending));
            timeInThisSmilFile += ending - beginning;
        }

        String timeInThisSmilStr = (String) xPathExpTimeInThisSmil.evaluate(smilDocument, XPathConstants.STRING);
        long timeInThisSmil = UtilExt.parseMilliSeconds(timeInThisSmilStr);
        if (!timeInThisSmilStr.isBlank() && Math.abs(timeInThisSmil - timeInThisSmilFile) > 500) {
            createSmilError(smilFile,
                "Total time in this smil file " + UtilExt.formatTime(timeInThisSmilFile) +
                " does not match the time in metadata " + UtilExt.formatTime(timeInThisSmil)
            );
        }

        return elapsedTime + timeInThisSmilFile;
    }

    private void createSmilError(String smilFile, String msg) {
        errorList.add(new Issue(
                "",
                "[" +GuidelineExt.SMIL + "] " + msg,
                smilFile,
                GuidelineExt.SMIL,
                Issue.ERROR_ERROR
        ));
    }

    final Pattern headingPattern = Pattern.compile("[Hh]([1-6])");

    private List<String>[] findLinks(DocumentBuilder builder, XPathExpression xPathExpRel, XPathExpression xPathExpId, String file) throws SAXException, IOException, XPathExpressionException {
        List[] uris = new List[2];
        uris[0] = new ArrayList<>();
        uris[1] = new ArrayList<>();
        Document xmlDocument = null;
        try {
            xmlDocument = builder.parse(new File(daisyDir, file));
        } catch (FileNotFoundException fnfe) {
            errorList.add(
                new Issue("", "[" + Guideline.XHTML + "] " + fnfe.getMessage(),
                    file,
                    Guideline.XHTML,
                    Issue.ERROR_ERROR
                ));
            return uris;
        } catch (SAXParseException saxEx) {
            String lineIn = String.format("(Line: %05d Column: %05d) ", saxEx.getLineNumber(), saxEx.getColumnNumber());
            errorList.add(
                new Issue("", "[" + Guideline.XHTML + "] " + lineIn + saxEx.getMessage(),
                    file,
                    Guideline.XHTML,
                    Issue.ERROR_ERROR
                ));
            return uris;
        }

        NodeList nodeList = (NodeList) xPathExpRel.evaluate(xmlDocument, XPathConstants.NODESET);
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node n  = nodeList.item(i);
            String filename = Util.getRelativeFilename(file, n.getNodeValue());
            if (n.getNodeValue().startsWith("#")) {
                filename = n.getNodeValue();
            }
            if (filename.contains(":")) {
                continue;
            }
            if ((filename.startsWith("#") || !filename.contains("#")) && !filename.contains(".")) {
                filename = file + (filename.startsWith("#") ? "" : "#") + filename;
            }
            uris[0].add(new Issue(
                filename,
                "[" +GuidelineExt.SMIL + "] The reference " + filename + " points to a id in the target resource that does not exist.",
                file,
                GuidelineExt.SMIL,
                Issue.ERROR_ERROR
            ));
        }

        int lastHeading = 0;
        nodeList = (NodeList) xPathExpId.evaluate(xmlDocument, XPathConstants.NODESET);
        for (int i = 0; i < nodeList.getLength(); i++) {
            Element n = (Element) nodeList.item(i);

            Matcher m = headingPattern.matcher(n.getNodeName());
            if (m.find()) {
                int heading = Integer.parseInt(m.group(1));
                if (heading > lastHeading + 1) {
                    String textContent = n.getTextContent() != null ? n.getTextContent().trim() : "";
                    errorList.add(new Issue(
                        n.getNodeName(),
                        "[" +Guideline.XHTML + "] Incorrect heading hierarchy at " +
                                n.getNodeName() + " expected h" + (lastHeading + 1) + " or less (" + textContent + ")",
                        file,
                        Guideline.XHTML,
                        Issue.ERROR_ERROR
                    ));
                }
                lastHeading = heading;
            }

            if (n.hasAttribute("id")) {
                uris[1].add(file + "#" + n.getAttribute("id"));
            }
        }

        return uris;
    }

    public Set<Issue> getErrorList() {
        return errorList;
    }
}
