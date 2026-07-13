package com.musicai.agent.export;

import com.musicai.agent.application.CreationConstraints;
import com.musicai.agent.application.GuitarRiffGenerator;
import org.junit.jupiter.api.Test;

import javax.sound.midi.MidiSystem;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GuitarProAcceptanceArtifactTest {

    @Test
    void createsStableFilesForGuitarProAcceptance() throws Exception {
        Path directory = Path.of("target", "guitar-pro-acceptance").toAbsolutePath().normalize();
        Files.createDirectories(directory);
        var score = new GuitarRiffGenerator().generate(CreationConstraints.defaultRockRiff());
        Path midi = new MidiExporter().export(score, directory.resolve("eight-bar-rock-riff.mid"));
        Path musicXml = new MusicXmlExporter().export(score, directory.resolve("eight-bar-rock-riff.musicxml"));

        assertThat(MidiSystem.getSequence(midi.toFile()).getTickLength())
                .isEqualTo(8L * 4 * MidiExporter.PPQ);
        assertThat(DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(musicXml.toFile())
                .getElementsByTagName("measure").getLength()).isEqualTo(8);
    }
}
