package org.daisy.validator;

import org.daisy.validator.audiocheck.AudioClip;
import org.daisy.validator.report.Issue;
import org.daisy.validator.schemas.Guideline;
import org.daisy.validator.schemas.GuidelineExt;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

public class EPUBFilesExt {
    private final EPUBFiles epubFiles;

    public EPUBFilesExt(EPUBFiles epubFiles) {
        this.epubFiles = epubFiles;
    }

    public void validateWithAce(File workFile) throws Exception {
        this.epubFiles.validateWithAce(workFile);
    }

    public void validateWithEpubCheck(File workFile) throws Exception {
        this.epubFiles.validateWithEpubCheck(workFile);
    }

    public void validate() throws Exception {
        this.epubFiles.validate();
    }

    public void cleanUp() throws Exception {
        this.epubFiles.cleanUp();
    }

    public Guideline getGuideline() throws Exception {
        return this.epubFiles.getGuideline();
    }

    public EPUBFiles getInstance() {
        return this.epubFiles;
    }

    public void validateAudioClips() throws Exception {
        List<AudioClip> audioClips = new ArrayList<>();

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        XPath xPath = XPathFactory.newInstance().newXPath();
        Document xmlDocument = null;

        String packageOBF = epubFiles.getPackageFile();

        try {
            xmlDocument = db.parse(new File(epubFiles.getEpubDir(), packageOBF));
        } catch (SAXParseException var20) {
            String lineIn = String.format("(Line: %05d Column: %05d) ", var20.getLineNumber(), var20.getColumnNumber());
            epubFiles.getErrorList().add(
                new Issue("", "[opf] " + lineIn + var20.getMessage(), packageOBF, "opf", 1)
            );
        }

        XPathExpression xPathExpAudio = xPath.compile("//audio[@clip-begin] | //audio[@clip-end] | //audio[@clipBegin] | //audio[@clipEnd]");
        XPathExpression xPathExpSmilFiles = xPath.compile("//item[@media-type='application/smil+xml']");
        NodeList nodeList = (NodeList)xPathExpSmilFiles.evaluate(xmlDocument, XPathConstants.NODESET);

        for(int i = 0; i < nodeList.getLength(); ++i) {
            Element el = (Element)nodeList.item(i);
            String filename = Util.getRelativeFilename(packageOBF, el.getAttribute("href"));
            Document smilDocument = null;

            try {
                smilDocument = db.parse(new File(epubFiles.getEpubDir(), filename));
                validateSmilFileExt(smilDocument, filename, xPathExpAudio, audioClips);
            } catch (SAXParseException saxPE) {
                String lineIn = String.format("(Line: %05d Column: %05d) ", saxPE.getLineNumber(), saxPE.getColumnNumber());
                epubFiles.getErrorList().add(
                    new Issue("", "[OPF] " + lineIn + saxPE.getMessage(), packageOBF, Guideline.OPF, Issue.ERROR_ERROR)
                );
            } catch (Exception e) {
                epubFiles.getErrorList().add(
                    new Issue("", "[SMIL] " + e.getMessage(), packageOBF, Guideline.SMIL, Issue.ERROR_ERROR)
                );
            }
        }

        AudioClip.validateAudioClips(audioClips, epubFiles.getEpubDir(), epubFiles.getErrorList(), Guideline.OPF);
    }

    private void validateSmilFileExt(
            Document smilDocument,
            String smilFile,
            XPathExpression xPathExpAudio,
            List<AudioClip> audioClips
    ) throws Exception {
        NodeList nodeList = (NodeList) xPathExpAudio.evaluate(smilDocument, XPathConstants.NODESET);
        for (int i = 0; i < nodeList.getLength(); i++) {
            Element el = (Element) nodeList.item(i);
            long beginning = 0;
            long ending = 0;
            if (el.hasAttribute("clipBegin") || el.hasAttribute("clipEnd")) {
                beginning = Util.parseMilliSeconds(el.getAttribute("clipBegin"));
                ending = Util.parseMilliSeconds(el.getAttribute("clipEnd"));
            } else {
                beginning = Util.parseMilliSeconds(el.getAttribute("clip-begin"));
                ending = Util.parseMilliSeconds(el.getAttribute("clip-end"));
            }
            audioClips.add(new AudioClip(smilFile, el, beginning, ending));
        }
    }
}
