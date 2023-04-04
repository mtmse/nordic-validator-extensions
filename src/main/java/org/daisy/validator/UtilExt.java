package org.daisy.validator;

import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UtilExt extends Util {
    private static final Logger logger = Logger.getLogger(UtilExt.class.getName());

    private static final Pattern milliPattern = Pattern.compile(
        "(ntp=)?(\\d{1,2}:)?(\\d{1,2}:)?(\\d+)(\\.\\d+)?(ms|h|min|s)?"
    );

    public static final long parseMilliSeconds(String s) {

        Matcher m = milliPattern.matcher(s);
        if (m.find()) {
            double val = 0;

            if (m.group(2) != null) {
                val += Long.parseLong(m.group(2).substring(0, m.group(2).length() - 1)) * 3600;
            }
            if (m.group(3) != null) {
                val += Long.parseLong(m.group(3).substring(0, m.group(3).length() - 1)) * 60;
            }
            if (m.group(4) != null) {
                val += Long.parseLong(m.group(4));
            }
            if (m.group(5) != null) {
                val += Double.parseDouble(m.group(5));
            }

            if (m.group(6) != null) {
                if ("h".equals(m.group(6))) {
                    val *= 3600 * 1000;
                } else if ("min".equals(m.group(6))) {
                    val *= 60 * 1000;
                } else if ("ms".equals(m.group(6))) {
                } else {
                    val *= 1000;
                }
            } else {
                val *= 1000;
            }

            return Math.round(val);
        }
        return 0;
    }

    public static String formatTime(long time) {
        Instant inst = Instant.ofEpochMilli(time);
        LocalTime localTime = LocalTime.ofInstant(inst, ZoneId.of("UTC"));
        return localTime.toString();
    }

    public static void writeFileWithoutDoctype(File outputFile, InputStream inputStream) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        String fileContent = "";
        while ((line = br.readLine()) != null) {
            fileContent += line;
            fileContent += "\n";
        }

        fileContent = fileContent.replaceAll(
                "(?i)<!DOCTYPE[^<>]*(?:<!ENTITY[^<>]*>[^<>]*)?>", ""
        );

        BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));
        bw.write(fileContent);
        bw.flush();
        bw.close();
    }

    public static void unpackSchemaDir(String schemaResource, File schemaDir) throws Exception {
        final File jarFile = new File(UtilExt.class.getProtectionDomain().getCodeSource().getLocation().getPath());

        if(jarFile.isFile()) {  // Run with JAR file
            final JarFile jar = new JarFile(jarFile);
            final Enumeration<JarEntry> entries = jar.entries();
            while(entries.hasMoreElements()) {
                JarEntry jEntry = entries.nextElement();
                String name = jEntry.getName();
                if (name.startsWith(schemaResource + "/")) {
                    name = name.substring(schemaResource.length() + 1);
                    if (jEntry.isDirectory()) {
                        new File(schemaDir, name).mkdirs();
                    } else {
                        UtilExt.writeFile(
                            new File(schemaDir, name),
                            EPUBFiles.class.getResourceAsStream(
                                "/" + schemaResource + "/" + name
                            )
                        );
                    }
                }
            }
            jar.close();
        } else {
            final URL url = EPUBFiles.class.getResource("/" + schemaResource);
            if (url != null) {
                try {
                    final File apps = new File(url.toURI());
                    unpackDirectory(apps, url, schemaDir, schemaResource);
                } catch (URISyntaxException ex) {
                    logger.fatal(ex.getMessage(), ex);
                }
            }
        }
    }

    private static void unpackDirectory(File apps, URL url, File parentDir, String schemaResource) throws Exception {
        for (File app : apps.listFiles()) {
            String name = app.getAbsolutePath().substring(url.getPath().length() + 1);
            if (app.isDirectory()) {
                new File(parentDir, name).mkdirs();
                unpackDirectory(app, url, parentDir, schemaResource);
            } else {
                UtilExt.writeFile(
                    new File(parentDir, name),
                    EPUBFiles.class.getResourceAsStream(
                        "/" + schemaResource + "/" + name
                    )
                );
            }
        }
    }
}
