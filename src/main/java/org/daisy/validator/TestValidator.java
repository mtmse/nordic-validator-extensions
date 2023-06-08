package org.daisy.validator;

import org.daisy.validator.audiocheck.ReportConfiguration;
import org.daisy.validator.report.Issue;
import org.daisy.validator.report.ReportGenerator;
import org.daisy.validator.schemas.GuidelineDaisy202;
import org.daisy.validator.schemas.GuidelineDaisy3;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

public class TestValidator {
    public static void main(String[] args) {
        /*
        String[] files = new String[] {
            "/mnt/pdfdrive/talking-book-checker/no_known_issues/no-issue-1.zip"
        };
        for (String file : files) {
            runFile(file);
        }
        */
        runFile("/home/danielp/daisywork/daisy2/V002956.zip");
    }

    public static void runFile(String filename) {
        System.out.println("===============" + filename + "=================");
        try {
            Daisy202Files d3f = new Daisy202Files(
                    new ZipFile(new File(filename)),
                    1,
                    new GuidelineDaisy202()
            );
            d3f.unpackSchemas();
            d3f.validate(new ReportConfiguration());
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
