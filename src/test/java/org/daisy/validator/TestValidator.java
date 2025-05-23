package org.daisy.validator;

import org.daisy.validator.audiocheck.SentenceCheckConfiguration;
import org.daisy.validator.audiocheck.SoundQualityCheckConfiguration;
import org.daisy.validator.report.Issue;
import org.daisy.validator.report.ReportGenerator;
import org.daisy.validator.schemas.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

public class TestValidator {
    public static void main(String[] args) throws Exception {
        //runDaisy202File("/home/danielp/daisywork/daisy2/more/CA68783.zip");
        //runEPUBFile("/home/danielp/daisywork/epub/audio/V22222_test_pauser.epub");
        runPDFFile("/home/danielp/pdfwork/fel/test.pdf");
    }

    public static void runPDFFile(String filename) {
        System.out.println("===============" + filename + "=================");
        try {
            PDFValidator pdfValidator = new PDFValidator(new File(filename), new GuidelinePDF());
            pdfValidator.validate();

            for (Issue i : pdfValidator.getErrorList()) {
                System.out.println(i.getFilename() + " " + i.getValidationType() + " " + i.getDescription());
            }

            List<Issue> issueList = new ArrayList<>();
            issueList.addAll(pdfValidator.getErrorList());

            ReportGenerator rg = new ReportGenerator();
            rg.generateHTMLReport(
                    new GuidelinePDF(),
                    filename,
                    "test.html",
                    issueList
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void runEPUBFile(String filename) {
        System.out.println("===============" + filename + "=================");
        try {
            ZipFile zipFile = new ZipFile(filename);
            EPUBFilesExt epubFiles = new EPUBFilesExt(NordicValidator.getEPUBFiles(zipFile, 1, new Guideline2020()));
            Guideline guideline = epubFiles.getGuideline();

            SentenceCheckConfiguration sentenceCheckConfiguration = new SentenceCheckConfiguration();
            SoundQualityCheckConfiguration soundQualityCheckConfiguration = new SoundQualityCheckConfiguration();
            epubFiles.validateAudioClips(sentenceCheckConfiguration, soundQualityCheckConfiguration);
            epubFiles.cleanUp();

            for (Issue i : epubFiles.getInstance().getErrorList()) {
                System.out.println(i.getFilename() + " " + i.getValidationType() + " " + i.getDescription());
            }

            List<Issue> issueList = new ArrayList<>();
            issueList.addAll(epubFiles.getInstance().getErrorList());

            ReportGenerator rg = new ReportGenerator();
            rg.generateHTMLReport(
                    guideline,
                    filename,
                    "test.html",
                    issueList
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void runDaisy202File(String filename) {
        System.out.println("===============" + filename + "=================");
        try {
            Daisy202Files d3f = new Daisy202Files(
                new ZipFile(new File(filename)),
                1,
                new GuidelineDaisy202()
            );
            d3f.unpackSchemas();
            d3f.validate(new SentenceCheckConfiguration());
            d3f.cleanUp();

            for (Issue i : d3f.getErrorList()) {
                System.out.println(i.getFilename() + " " + i.getValidationType() + " " + i.getDescription());
            }

            List<Issue> issueList = new ArrayList<>();
            issueList.addAll(d3f.getErrorList());

            ReportGenerator rg = new ReportGenerator();
            rg.generateHTMLReport(
                new GuidelineDaisy202(),
                filename,
                "test.html",
                issueList
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
