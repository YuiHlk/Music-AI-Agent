package com.musicai.agent.export;

import com.musicai.agent.domain.ChordEvent;
import com.musicai.agent.domain.ChordNote;
import com.musicai.agent.domain.NoteEvent;
import com.musicai.agent.domain.RhythmicDuration;
import com.musicai.agent.domain.Score;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class MusicXmlExporter {

    private static final int DIVISIONS = 480;

    public Path export(Score score, Path target) throws IOException {
        score.validate();
        try {
            Document document = build(score);
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.transform(new DOMSource(document), new StreamResult(target.toFile()));
            return target;
        } catch (ParserConfigurationException | TransformerException exception) {
            throw new IOException("Could not create MusicXML", exception);
        }
    }

    public Document build(Score score) throws ParserConfigurationException {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = document.createElement("score-partwise");
        root.setAttribute("version", "4.0");
        document.appendChild(root);

        appendText(document, root, "movement-title", score.title());
        Element partList = child(document, root, "part-list");
        Element scorePart = child(document, partList, "score-part");
        scorePart.setAttribute("id", "P1");
        appendText(document, scorePart, "part-name", score.tracks().getFirst().name());

        Element part = child(document, root, "part");
        part.setAttribute("id", "P1");
        for (var measure : score.tracks().getFirst().measures()) {
            Element measureElement = child(document, part, "measure");
            measureElement.setAttribute("number", Integer.toString(measure.number()));
            if (measure.number() == 1) {
                appendAttributes(document, measureElement, score);
                Element direction = child(document, measureElement, "direction");
                Element sound = child(document, direction, "sound");
                sound.setAttribute("tempo", Integer.toString(score.tempo()));
            }
            for (var event : measure.events()) {
                if (event instanceof NoteEvent note) {
                    appendPitchedNote(document, measureElement,
                            new ChordNote(note.pitch(), note.fretPosition()), note.duration(), false);
                } else if (event instanceof ChordEvent chord) {
                    boolean continuation = false;
                    for (ChordNote note : chord.notes()) {
                        appendPitchedNote(document, measureElement, note, chord.duration(), continuation);
                        continuation = true;
                    }
                } else {
                    Element noteElement = child(document, measureElement, "note");
                    child(document, noteElement, "rest");
                    appendDuration(document, noteElement, event.duration());
                }
            }
        }
        return document;
    }

    private static void appendPitchedNote(Document document, Element measure, ChordNote note,
                                          RhythmicDuration duration, boolean chordContinuation) {
        Element noteElement = child(document, measure, "note");
        if (chordContinuation) {
            child(document, noteElement, "chord");
        }
        Element pitch = child(document, noteElement, "pitch");
        appendText(document, pitch, "step", note.pitch().step());
        if (note.pitch().alter() != 0) {
            appendText(document, pitch, "alter", Integer.toString(note.pitch().alter()));
        }
        appendText(document, pitch, "octave", Integer.toString(note.pitch().octave()));
        appendDuration(document, noteElement, duration);
        Element notations = child(document, noteElement, "notations");
        Element technical = child(document, notations, "technical");
        appendText(document, technical, "string", Integer.toString(note.fretPosition().stringNumber()));
        appendText(document, technical, "fret", Integer.toString(note.fretPosition().fret()));
    }

    private static void appendDuration(Document document, Element noteElement, RhythmicDuration duration) {
        long ticks = duration.toTicks(DIVISIONS);
        appendText(document, noteElement, "duration", Long.toString(ticks));
        appendText(document, noteElement, "type", noteType(ticks));
    }

    private static void appendAttributes(Document document, Element measure, Score score) {
        Element attributes = child(document, measure, "attributes");
        appendText(document, attributes, "divisions", Integer.toString(DIVISIONS));
        Element key = child(document, attributes, "key");
        appendText(document, key, "fifths", "1");
        appendText(document, key, "mode", "minor");
        Element time = child(document, attributes, "time");
        appendText(document, time, "beats", Integer.toString(score.timeSignature().beats()));
        appendText(document, time, "beat-type", Integer.toString(score.timeSignature().beatUnit()));
        Element clef = child(document, attributes, "clef");
        appendText(document, clef, "sign", "G");
        appendText(document, clef, "line", "2");
        appendText(document, clef, "clef-octave-change", "-1");
        Element staffDetails = child(document, attributes, "staff-details");
        appendText(document, staffDetails, "staff-lines", "6");
    }

    private static String noteType(long ticks) {
        return switch ((int) ticks) {
            case 1920 -> "whole";
            case 960 -> "half";
            case 480 -> "quarter";
            case 240 -> "eighth";
            case 120 -> "16th";
            default -> throw new IllegalArgumentException("Unsupported MusicXML duration: " + ticks);
        };
    }

    private static Element child(Document document, Element parent, String name) {
        Element element = document.createElement(name);
        parent.appendChild(element);
        return element;
    }

    private static void appendText(Document document, Element parent, String name, String value) {
        Element element = child(document, parent, name);
        element.setTextContent(value);
    }
}
