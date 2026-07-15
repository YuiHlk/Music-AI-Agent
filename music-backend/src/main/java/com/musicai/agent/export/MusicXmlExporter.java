package com.musicai.agent.export;

import com.musicai.agent.domain.ChordEvent;
import com.musicai.agent.domain.ChordNote;
import com.musicai.agent.domain.KeySignature;
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

/**
 * 将领域乐谱转换为兼容 Guitar Pro 导入的 MusicXML 4.0 文档。
 */
public final class MusicXmlExporter {

    // 与 MIDI PPQ 保持一致，领域时值在两种导出格式中得到相同的量化结果。
    private static final int DIVISIONS = 480;

    /**
     * 校验乐谱并以 UTF-8 写出 MusicXML 文件。
     *
     * @param score 待导出的乐谱
     * @param target 目标文件路径
     * @return 原目标路径
     * @throws IOException 无法构建或写入 XML 文档时
     */
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

    /**
     * 构建 MusicXML 4.0 的 partwise DOM 文档。
     *
     * @param score 待转换的乐谱
     * @return 尚未写入文件的 DOM 文档
     * @throws ParserConfigurationException 当前运行环境无法创建 DOM 构建器时
     */
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
                    // MusicXML 用后续 note 的 <chord/> 表示同一起始时间，而不是嵌套的和弦节点。
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
        // 领域模型与 MusicXML 都将最高音弦编号为 1，保持该方向可避免 GP8 导入后弦位反转。
        appendText(document, technical, "string", Integer.toString(note.fretPosition().stringNumber()));
        appendText(document, technical, "fret", Integer.toString(note.fretPosition().fret()));
    }

    private static void appendDuration(Document document, Element noteElement, RhythmicDuration duration) {
        // MusicXML duration 使用每四分音符 DIVISIONS 个刻度，而不是毫秒或 MIDI 的绝对时间。
        long ticks = duration.toTicks(DIVISIONS);
        appendText(document, noteElement, "duration", Long.toString(ticks));
        appendText(document, noteElement, "type", noteType(ticks));
    }

    private static void appendAttributes(Document document, Element measure, Score score) {
        Element attributes = child(document, measure, "attributes");
        appendText(document, attributes, "divisions", Integer.toString(DIVISIONS));
        Element key = child(document, attributes, "key");
        KeySignature keySignature = KeySignature.parse(score.keySignature());
        appendText(document, key, "fifths", Integer.toString(keySignature.fifths()));
        appendText(document, key, "mode", keySignature.mode().name().toLowerCase());
        Element time = child(document, attributes, "time");
        appendText(document, time, "beats", Integer.toString(score.timeSignature().beats()));
        appendText(document, time, "beat-type", Integer.toString(score.timeSignature().beatUnit()));
        Element clef = child(document, attributes, "clef");
        appendText(document, clef, "sign", "G");
        appendText(document, clef, "line", "2");
        appendText(document, clef, "clef-octave-change", "-1");
        Element staffDetails = child(document, attributes, "staff-details");
        // 六线谱技术信息由 string/fret 承载；staff-lines 明确告诉 GP8 这是六弦乐器。
        appendText(document, staffDetails, "staff-lines", "6");
    }

    private static String noteType(long ticks) {
        return switch ((int) ticks) {
            case 1920 -> "whole";
            case 960 -> "half";
            case 480 -> "quarter";
            case 240 -> "eighth";
            case 120 -> "16th";
            case 60 -> "32nd";
            case 30 -> "64th";
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
