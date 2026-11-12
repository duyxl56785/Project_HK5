package com.projecthk5.orderservice.integration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Keep the classpath out of the Windows CreateProcess command line for crash-probe JVMs. */
final class CrashJvmArgFile {
    private CrashJvmArgFile() { }

    static Path writeClasspath(String classpath) throws IOException {
        if (classpath == null || classpath.isEmpty() || classpath.indexOf('\n') >= 0
                || classpath.indexOf('\r') >= 0 || classpath.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid child JVM classpath");
        }
        Path argFile = Files.createTempFile("tv3-checkout-crash-jvm-", ".args");
        try {
            // The Java launcher parses this file as arguments, not a shell command.
            // Quote the whole path list and escape backslashes for Windows paths.
            String escaped = classpath.replace("\\", "\\\\").replace("\"", "\\\"");
            Files.writeString(argFile, "-cp\n\"" + escaped + "\"\n", StandardCharsets.UTF_8);
            argFile.toFile().deleteOnExit();
            return argFile;
        } catch (IOException | RuntimeException failure) {
            Files.deleteIfExists(argFile);
            throw failure;
        }
    }
}
