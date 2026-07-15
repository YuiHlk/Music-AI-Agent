package com.musicai.agent.api;

import com.musicai.agent.domain.ChordEvent;
import com.musicai.agent.domain.Measure;
import com.musicai.agent.domain.NoteEvent;
import com.musicai.agent.domain.RestEvent;
import com.musicai.agent.domain.Score;

import java.util.List;

/**
 * 与内部领域模型解耦的前端乐谱预览协议。
 *
 * @param title 乐谱标题
 * @param tempo 每分钟四分音符数
 * @param keySignature 调号显示文本
 * @param timeSignature 拍号显示文本
 * @param tracks 轨道预览
 */
public record ScorePreviewResponse(String title, int tempo, String keySignature, String timeSignature,
                                   List<TrackPreview> tracks) {

    /**
     * 将内部领域对象投影为前端需要的稳定协议，避免领域模型直接暴露给 HTTP 客户端。
     *
     * @param score 已验证的领域乐谱
     * @return 前端乐谱预览
     */
    public static ScorePreviewResponse from(Score score) {
        return new ScorePreviewResponse(score.title(), score.tempo(), score.keySignature(),
                score.timeSignature().beats() + "/" + score.timeSignature().beatUnit(),
                score.tracks().stream().map(track -> new TrackPreview(track.name(), track.tuning().name(),
                        track.measures().stream().map(ScorePreviewResponse::toMeasure).toList())).toList());
    }

    private static MeasurePreview toMeasure(Measure measure) {
        return new MeasurePreview(measure.number(), measure.events().stream().map(event -> {
            if (event instanceof NoteEvent note) {
                return new EventPreview("NOTE", note.duration().numerator(), note.duration().denominator(),
                        List.of(new TabNote(note.pitch().midiNumber(), note.fretPosition().stringNumber(),
                                note.fretPosition().fret())));
            }
            if (event instanceof ChordEvent chord) {
                return new EventPreview("CHORD", chord.duration().numerator(), chord.duration().denominator(),
                        chord.notes().stream().map(note -> new TabNote(note.pitch().midiNumber(),
                                note.fretPosition().stringNumber(), note.fretPosition().fret())).toList());
            }
            // sealed MusicalEvent 当前仅剩 RestEvent；新增 subtype 时必须同步扩展此协议映射。
            RestEvent rest = (RestEvent) event;
            return new EventPreview("REST", rest.duration().numerator(), rest.duration().denominator(), List.of());
        }).toList());
    }

    /**
     * @param name 轨道名
     * @param tuning 调弦名
     * @param measures 小节预览
     */
    public record TrackPreview(String name, String tuning, List<MeasurePreview> measures) {
    }

    /**
     * @param number 从 1 开始的小节号
     * @param events 按时间排列的音乐事件
     */
    public record MeasurePreview(int number, List<EventPreview> events) {
    }

    /**
     * @param type NOTE、CHORD 或 REST
     * @param durationNumerator 全音符比例分子
     * @param durationDenominator 全音符比例分母
     * @param notes 六线谱音符；休止符为空
     */
    public record EventPreview(String type, long durationNumerator, long durationDenominator,
                               List<TabNote> notes) {
    }

    /**
     * @param midiNumber MIDI 音高
     * @param stringNumber 吉他弦号，1 表示最高音弦
     * @param fret 品位
     */
    public record TabNote(int midiNumber, int stringNumber, int fret) {
    }
}
