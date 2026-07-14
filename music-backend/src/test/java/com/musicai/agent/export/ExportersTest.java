package com.musicai.agent.export;

import com.musicai.agent.application.CreationConstraints;
import com.musicai.agent.application.GuitarRiffGenerator;
import com.musicai.agent.application.MusicalMood;
import com.musicai.agent.application.RhythmicFeel;
import com.musicai.agent.domain.GuitarTuning;
import com.musicai.agent.domain.TimeSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.midi.MidiSystem;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExportersTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void exportsAndReadsMidi() throws Exception {
        var score = new GuitarRiffGenerator().generate(CreationConstraints.defaultRockRiff());
        Path midi = new MidiExporter().export(score, temporaryDirectory.resolve("riff.mid"));

        var sequence = MidiSystem.getSequence(midi.toFile());
        assertThat(sequence.getResolution()).isEqualTo(MidiExporter.PPQ);
        assertThat(sequence.getTickLength()).isEqualTo(8L * 4 * MidiExporter.PPQ);
    }

    @Test
    void exportsWellFormedMusicXmlWithExpectedMeasures() throws Exception {
        var score = new GuitarRiffGenerator().generate(CreationConstraints.defaultRockRiff());
        Path musicXml = new MusicXmlExporter().export(score, temporaryDirectory.resolve("riff.musicxml"));

        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(musicXml.toFile());
        assertThat(document.getDocumentElement().getTagName()).isEqualTo("score-partwise");
        assertThat(document.getElementsByTagName("measure").getLength()).isEqualTo(8);
        assertThat(document.getElementsByTagName("note").getLength()).isEqualTo(64);
    }

    @Test
    void exportsParsedKeySignatureInsteadOfHardCodedEMinor() throws Exception {
        var constraints = new CreationConstraints(2, 110, "C major", "rock", GuitarTuning.STANDARD,
                TimeSignature.FOUR_FOUR, MusicalMood.BRIGHT, RhythmicFeel.STRAIGHT, 2, 7);
        var score = new GuitarRiffGenerator().generate(constraints);
        Path musicXml = new MusicXmlExporter().export(score, temporaryDirectory.resolve("c-major.musicxml"));

        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(musicXml.toFile());
        assertThat(document.getElementsByTagName("fifths").item(0).getTextContent()).isEqualTo("0");
        assertThat(document.getElementsByTagName("mode").item(0).getTextContent()).isEqualTo("major");
    }
}
