package org.daisy.validator;

import org.daisy.validator.audiocheck.AudioClip;
import org.daisy.validator.audiocheck.SentenceCheckConfiguration;
import org.daisy.validator.report.Issue;
import org.daisy.validator.schemas.Guideline;
import org.daisy.validator.schemas.GuidelineExt;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;
import org.verapdf.pdfa.results.ValidationResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PDFValidator {
    private File pdfFile;
    private Set<Issue> errorList = new HashSet<>();
    private final Guideline guideline;

    public PDFValidator(File pdfFile, Guideline guideline) throws Exception {
        this.pdfFile = pdfFile;
        this.guideline = guideline;
    }

    public void validate() throws Exception {
        String validationType = GuidelineExt.PDF;

        VeraGreenfieldFoundryProvider.initialise();
        try {
            FileInputStream fis = new FileInputStream(this.pdfFile);
            PDFAParser parser = Foundries.defaultInstance().createParser(fis);
            PDFAFlavour flavour = parser.getFlavour();
            if (flavour == null || flavour.equals(PDFAFlavour.NO_FLAVOUR)) {
                flavour = PDFAFlavour.PDFUA_2;
            }
            PDFAValidator validator = Foundries.defaultInstance().createValidator(flavour, false);
            ValidationResult result = validator.validate(parser);
            fis.close();
            if (!result.isCompliant()) {
                for (TestAssertion assertion : result.getTestAssertions()) {

                    errorList.add(
                            new Issue(
                                    assertion.getLocation().getContext(),
                                    "[" + validationType + ":" + assertion.getRuleId().getTestNumber() + "] " + assertion.getMessage(),
                                    "",
                                    validationType,
                                    Issue.ERROR_ERROR
                            ));

                    System.out.println(assertion.getMessage() + ": " + assertion.getLocation());
                }
            }
        } catch (Exception e) {
            errorList.add(
                    new Issue("", "[" + validationType + "] " + e.getMessage(),
                            "",
                            validationType,
                            Issue.ERROR_ERROR
                    ));
        }
    }

    public Set<Issue> getErrorList() {
        return errorList;
    }
}
