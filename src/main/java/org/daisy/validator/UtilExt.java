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

    public static void writeFileWithoutDoctype(File outputFile, InputStream inputStream) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        String fileContent = "";
        while ((line = br.readLine()) != null) {
            fileContent += line;
            fileContent += "\n";
        }

        fileContent = fileContent
            .replaceAll("(?i)<!DOCTYPE[^<>]*(?:<!ENTITY[^<>]*>[^<>]*)?>", "");

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
            final URL url = UtilExt.class.getResource("/" + schemaResource);

            if (url != null && url.getProtocol().equals("file")) {
                try {
                    final File apps = new File(url.toURI());
                    unpackDirectory(apps, url, schemaDir, schemaResource);
                } catch (URISyntaxException ex) {
                    logger.fatal(ex.getMessage(), ex);
                }
            } else {
                final File jarFile2 = new File(Util.class.getProtectionDomain().getCodeSource().getLocation().getPath());

                if(jarFile2.isFile()) {  // Run with JAR file
                    final JarFile jar = new JarFile(jarFile2);
                    final Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
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
