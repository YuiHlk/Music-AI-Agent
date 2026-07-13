package com.musicai.agent.infrastructure;

import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class GuitarProConnector {

    public void open(Path artifact) {
        Path normalized = artifact.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("Artifact file not found");
        }
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) {
            throw new IllegalStateException("Opening Guitar Pro is supported only on Windows");
        }
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            throw new IllegalStateException("Desktop file opening is not available in this environment");
        }
        try {
            Desktop.getDesktop().open(normalized.toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not open artifact with its Windows application", exception);
        }
    }
}
