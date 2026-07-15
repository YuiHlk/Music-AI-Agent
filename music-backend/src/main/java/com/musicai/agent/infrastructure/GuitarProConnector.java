package com.musicai.agent.infrastructure;

import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 通过 Windows 文件关联应用打开吉他谱导出文件。
 */
@Component
public class GuitarProConnector {

    /**
     * 使用系统关联的桌面应用打开指定产物。
     *
     * @param artifact 待打开的产物路径
     * @throws IllegalArgumentException 文件不存在或不是普通文件时抛出
     * @throws IllegalStateException 当前平台不支持桌面打开操作或打开失败时抛出
     */
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
