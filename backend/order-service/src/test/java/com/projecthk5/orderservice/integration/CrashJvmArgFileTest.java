package com.projecthk5.orderservice.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class CrashJvmArgFileTest {
    @Test
    void longClasspathAndWindowsStylePathsLaunchViaShortJavaArgFile() throws Exception {
        String classpath = "C:\\Program Files\\TV3 Demo\\lib.jar" + File.pathSeparator
                + String.join(File.pathSeparator,
                    Collections.nCopies(7_000, "a path with spaces\\and-backslashes"));
        assertTrue(classpath.length() > 131_072, "Reproduce a classpath too long for a normal OS argument");
        Path argFile = CrashJvmArgFile.writeClasspath(classpath);
        try {
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            ProcessBuilder builder = new ProcessBuilder(java, "@" + argFile.toAbsolutePath(),
                    "-XshowSettings:properties", "-version");
            assertTrue(builder.command().stream().mapToInt(String::length).sum() < 4_096,
                    "Full classpath must not reach CreateProcess command arguments");
            builder.redirectErrorStream(true);
            Process child = builder.start();
            String output;
            try {
                output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertEquals(0, child.waitFor(), output);
            } finally {
                if (child.isAlive()) child.destroyForcibly().waitFor();
            }
            assertTrue(output.contains("java.class.path"), output);
            assertTrue(output.contains("TV3 Demo"), "Java must parse spaces within the classpath argument");
            assertTrue(output.contains("and-backslashes"), "Java must parse Windows path separators");
        } finally {
            Files.deleteIfExists(argFile);
        }
        assertFalse(Files.exists(argFile), "Temporary classpath file must be removed");
    }

    @Test
    void refusesNewlineInClasspathWithoutWritingAnArgumentFile() {
        assertThrows(IllegalArgumentException.class,
                () -> CrashJvmArgFile.writeClasspath("a.jar\n-J-Dunsafe=true"));
    }
}
