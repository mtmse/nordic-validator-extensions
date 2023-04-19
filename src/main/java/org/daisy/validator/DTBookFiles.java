package org.daisy.validator;

import org.daisy.validator.report.Issue;
import org.daisy.validator.schemas.Guideline;
import org.daisy.validator.schemas.GuidelineDTBookNordic;
import org.daisy.validator.schemas.GuidelineExt;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.File;
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

public class DTBookFiles {
    private String dtbookFile;
    private List<String> contentFiles = new ArrayList<>();
    private Map<String, Long> audioFiles = new HashMap<>();
    private List<String> smilFiles = new ArrayList<>();
    private Set<Issue> errorList = new HashSet<>();
    private final ExecutorService executor;
    private final CompletionService<List<Issue>> completionService;
    private int submittedWork = 0;
    private final File dtbookDir = UtilExt.createTempDirectory();
    private final File schemaDir = UtilExt.createTempDirectory();
    private final Guideline guideline;
    private final String validationType;

    public DTBookFiles(String filename, String issue) throws Exception {
        this(filename, issue, null);
    }

    public DTBookFiles(String filename, String issue, Guideline guideline) throws Exception {
        executor = null;
        completionService = null;
        this.guideline = guideline;
        this.validationType = GuidelineExt.DTBOOK2005_1;
        errorList.add(new Issue("", issue, filename, GuidelineExt.DTBOOK2005_1, Issue.ERROR_FATAL));
    }

    public DTBookFiles(ZipFile zipFile, int threads, Guideline guideline) throws Exception {
        this.guideline = guideline;
        this.executor = Executors.newFixedThreadPool(threads);
        this.completionService = new ExecutorCompletionService<>(executor);

        Iterator zeIter = zipFile.entries().asIterator();
        while (zeIter.hasNext()) {
            ZipEntry ze = (ZipEntry) zeIter.next();
            if (!new File(dtbookDir, ze.getName()).getParentFile().exists()) {
                new File(dtbookDir, ze.getName()).getParentFile().mkdirs();
            }
            if (ze.isDirectory()) {
                new File(dtbookDir, ze.getName()).mkdirs();
            } else if (ze.getName().endsWith(".xml")) {
                dtbookFile = ze.getName();
                UtilExt.writeFileWithoutDoctype(
                    new File(dtbookDir, dtbookFile), zipFile.getInputStream(zipFile.getEntry(dtbookFile))
                );
            } else if(ze.getName().endsWith(".smil")) {
                smilFiles.add(ze.getName());
                UtilExt.writeFileWithoutDoctype(
                    new File(dtbookDir, ze.getName()), zipFile.getInputStream(zipFile.getEntry(ze.getName()))
                );
            } else {
                UtilExt.writeFile(
                    new File(dtbookDir, ze.getName()), zipFile.getInputStream(zipFile.getEntry(ze.getName()))
                );

                if (ze.getName().endsWith(".mp3") || ze.getName().endsWith(".mp2") || ze.getName().endsWith(".wav")) {
                    AudioFileFormat audioFormat = AudioSystem.getAudioFileFormat(new File(dtbookDir, ze.getName()));
                    long frames = audioFormat.getFrameLength();
                    long durationInMilliSeconds = Math.round((frames * 1000) / audioFormat.getFormat().getFrameRate());
                    audioFiles.put(ze.getName(), durationInMilliSeconds);
                }

                contentFiles.add(ze.getName());
           }
        }

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        XPath xPath = XPathFactory.newInstance().newXPath();

        XPathExpression xPathExpVersion = xPath.compile("//dtbook/@version");

        unpackSchemas();

        Document xmlDocument = null;
        try {
            xmlDocument = db.parse(new File(dtbookDir, dtbookFile));
        } catch (SAXParseException saxEx) {
            String lineIn = String.format("(Line: %05d Column: %05d) ", saxEx.getLineNumber(), saxEx.getColumnNumber());
            errorList.add(
                new Issue("", "[" + GuidelineExt.DTBOOK2005_1 + "] " + lineIn + saxEx.getMessage(),
                    dtbookFile,
                    GuidelineExt.DTBOOK2005_1,
                    Issue.ERROR_ERROR
                ));
            this.validationType = GuidelineExt.DTBOOK2005_1;
            return;
        }

        String versionStr = (String) xPathExpVersion.evaluate(xmlDocument, XPathConstants.STRING);
        if ("2005-3".equals(versionStr)) {
            this.validationType = GuidelineExt.DTBOOK2005_3_MATHML_3;
        } else if ("2005-2".equals(versionStr)) {
            this.validationType = GuidelineExt.DTBOOK2005_2_MATHML_3;
        } else if ("2005-1".equals(versionStr)) {
            this.validationType = GuidelineExt.DTBOOK2005_1;
        } else if ("1.1.0".equals(versionStr)) {
            this.validationType = GuidelineExt.DTBOOK_110;
        } else {
            this.validationType = GuidelineExt.DTBOOK2005_3_MATHML_3;
        }
    }

    private void validateFile(String filename, String validationType) throws Exception {
        if(completionService == null) return;
        completionService.submit(new ValidateFile(
            dtbookDir,
            filename,
            new File(schemaDir, guideline.getSchema(validationType).getFilename()),
            validationType
        ));
        submittedWork++;

        completionService.submit(new TransformFile(
            dtbookDir,
            filename,
                GuidelineExt.MATHML,
            new File(schemaDir, guideline.getSchema(GuidelineExt.MATHML).getFilename())
        ));
        submittedWork++;

        if (guideline instanceof GuidelineDTBookNordic) {
            completionService.submit(new ValidateFile(
                    dtbookDir,
                    filename,
                    new File(schemaDir, guideline.getSchema(GuidelineDTBookNordic.DTBOOKNORDIC2005_3).getFilename()),
                    GuidelineDTBookNordic.DTBOOKNORDIC2005_3
            ));
            submittedWork++;

            completionService.submit(new TransformFile(
                    dtbookDir,
                    filename,
                    GuidelineExt.CONTENT_FILES,
                    new File(schemaDir, guideline.getSchema(GuidelineExt.CONTENT_FILES).getFilename())
            ));
            submittedWork++;
        }
    }

    public void unpackSchemas() throws Exception {
        UtilExt.unpackSchemaDir(guideline.getSchemaPath(), schemaDir);
        UtilExt.unpackSchemaDir("mathml3", schemaDir);
        UtilExt.unpackSchemaDir("daisy202", schemaDir);
        if (guideline instanceof GuidelineDTBookNordic) {
            UtilExt.unpackSchemaDir("2015-1", schemaDir);
        }
    }

    public void cleanUp() {
        if(DevFlags.CLEAN_UP_WORK_DIR) {
            UtilExt.deleteDirectory(dtbookDir);
        }
        if(DevFlags.CLEAN_UP_SCHEMA_DIR) {
            UtilExt.deleteDirectory(schemaDir);
        }
    }

    public void validate() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        XPath xPath = XPathFactory.newInstance().newXPath();
        String expRel = "//*/@src | //*/@imgsrc";
        String expId = "//par | //text | //h1 | //h2 | //h3 | //h4 | //h5 | //h6";
        XPathExpression xPathExpRel = xPath.compile(expRel);
        XPathExpression xPathExpId = xPath.compile(expId);

        Set<Issue> uris = new HashSet<>();
        Set<String> ids = new HashSet<>();

        List[] res = findLinks(db, xPathExpRel, xPathExpId, dtbookFile);
        uris.addAll(res[0]);
        ids.addAll(res[1]);
        validateFile(dtbookFile, validationType);
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
            validateFile(contentFile, Guideline.XHTML);
        }

        for (Issue uri : uris) {
            if (contentFiles.contains(uri.getLocation())) {
                continue;
            }
            if (ids.contains(uri.getLocation())) {
                continue;
            }

            errorList.add(uri);
        }

        validateAudio();

        int received = 0;
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


    private void validateAudio() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        XPath xPath = XPathFactory.newInstance().newXPath();

        XPathExpression xPathExpTotalTime = xPath.compile("//meta[@name='ncc:totalTime']/@content");

        Document xmlDocument = null;
        try {
            xmlDocument = db.parse(new File(dtbookDir, dtbookFile));
        } catch (SAXParseException saxEx) {
            String lineIn = String.format("(Line: %05d Column: %05d) ", saxEx.getLineNumber(), saxEx.getColumnNumber());
            errorList.add(
                new Issue("", "[" + validationType + "] " + lineIn + saxEx.getMessage(),
                    dtbookFile,
                    validationType,
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
            smilFiles.add(s.substring(0, s.indexOf("#")));
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
                smilDocument = db.parse(new File(dtbookDir, smilFile));
            } catch (SAXParseException saxEx) {
                String lineIn = String.format("(Line: %05d Column: %05d) ", saxEx.getLineNumber(), saxEx.getColumnNumber());
                errorList.add(
                    new Issue("", "[" + validationType + "] " + lineIn + saxEx.getMessage(),
                        smilFile,
                        validationType,
                        Issue.ERROR_ERROR
                    ));
                continue;
            }

            elapsedTime = validateSmilFile(
                smilDocument, smilFile, xPathExpTotalElapsedTime, xPathExptimeInThisSmil, xPathExpAudio, elapsedTime
            );
        }

        if (Math.abs(elapsedTime - totalTime) > 500) {
            errorList.add(new Issue(
                "",
                "[" +validationType + "] Total time in metadata " + UtilExt.formatTime(totalTime) +
                        " does not equal total elapsed time " + UtilExt.formatTime(elapsedTime),
                dtbookFile,
                validationType,
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

        if (Math.abs(totalElapsedTime - elapsedTime) > 500) {
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
            if (beginning > audioFiles.get(el.getAttribute("src"))) {
                createSmilError(smilFile, "Beginning of clip is not in audio " + el.getAttribute("src"));
            }
            if (ending > audioFiles.get(el.getAttribute("src"))) {
                createSmilError(smilFile, "Ending of clip is not in audio " + el.getAttribute("src"));
            }
            timeInThisSmilFile += ending - beginning;
        }

        String timeInThisSmilStr = (String) xPathExpTimeInThisSmil.evaluate(smilDocument, XPathConstants.STRING);
        long timeInThisSmil = UtilExt.parseMilliSeconds(timeInThisSmilStr);
        if (Math.abs(timeInThisSmil - timeInThisSmilFile) > 500) {
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
            xmlDocument = builder.parse(new File(dtbookDir, file));
        } catch (SAXParseException saxEx) {
            String lineIn = String.format("(Line: %05d Column: %05d) ", saxEx.getLineNumber(), saxEx.getColumnNumber());
            errorList.add(
                new Issue("", "[" + validationType + "] " + lineIn + saxEx.getMessage(),
                    file,
                    validationType,
                    Issue.ERROR_ERROR
                ));
            return uris;
        }

        NodeList nodeList = (NodeList) xPathExpRel.evaluate(xmlDocument, XPathConstants.NODESET);
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node n  = nodeList.item(i);
            String filename = new File(new File(file).getParent(), n.getNodeValue()).getPath();
            uris[0].add(new Issue(
                filename,
                "[" +validationType + "] The reference " + filename + " points to a id in the target resource that does not exist.",
                file,
                validationType,
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
                    errorList.add(new Issue(
                        n.getNodeName(),
                        "[" +validationType + "] Incorrect heading hierarchy at " +
                                n.getNodeName() + " expected h" + (lastHeading + 1) + " or less",
                        file,
                        validationType,
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
