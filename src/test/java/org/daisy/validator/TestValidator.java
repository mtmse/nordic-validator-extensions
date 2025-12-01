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
        //runDaisy202File("/home/danielp/daisywork/pa_tal_om_lidingo20250822.zip");
        //runDaisy202File("/home/danielp/daisywork/V020482_015824_138_CA72659_HS_SYTB.zip");
        runDaisy202File("/home/danielp/daisywork/i_freske_lufta_20251107.zip");
        //runEPUBFile("/home/danielp/daisywork/V020482_015824_138_CA72659_HS_SYTB.epub");
        //runEPUBFile("/home/danielp/daisywork/epub/audio/V22222_test_pauser.epub");
        //runEPUBFile("/home/danielp/daisywork/MathML_fix.epub");
        System.out.println("end");
    }

    public static void runEPUBFile(String filename) {
        System.out.println("===============" + filename + "=================");
        try {
            ZipFile zipFile = new ZipFile(filename);
            EPUBFilesExt epubFiles = new EPUBFilesExt(NordicValidator.getEPUBFiles(zipFile, 1, new Guideline2020()));
            Guideline guideline = epubFiles.getGuideline();

            epubFiles.validate();

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
            Daisy202Files d2f = new Daisy202Files(
                new ZipFile(new File(filename)),
                1,
                new GuidelineDaisy202()
            );
            d2f.unpackSchemas();
            d2f.validate(null);
            d2f.cleanUp();

            for (Issue i : d2f.getErrorList()) {
                System.out.println(i.getFilename() + " " + i.getValidationType() + " " + i.getDescription());
            }

            List<Issue> issueList = new ArrayList<>();
            issueList.addAll(d2f.getErrorList());

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
