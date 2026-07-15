package com.musicai.agent.domain;

import java.util.List;

/**
 * 表示可验证、可导出的完整乐谱聚合。
 *
 * @param title 乐谱标题
 * @param tempo 每分钟四分音符拍数
 * @param keySignature 调号文本
 * @param timeSignature 拍号
 * @param tracks 乐谱轨道列表
 */
public record Score(String title, int tempo, String keySignature, TimeSignature timeSignature, List<Track> tracks) {
    /**
     * 创建乐谱并固定轨道列表快照。
     */
    public Score {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title must not be blank");
        }
        if (tempo < 20 || tempo > 300) {
            throw new IllegalArgumentException("Tempo must be between 20 and 300 BPM");
        }
        if (keySignature == null || keySignature.isBlank()) {
            throw new IllegalArgumentException("Key signature must not be blank");
        }
        tracks = List.copyOf(tracks);
        if (tracks.isEmpty()) {
            throw new IllegalArgumentException("Score must contain tracks");
        }
    }

    /**
     * 校验各轨道小节数、每小节时值及吉他指法可演奏性。
     *
     * @throws IllegalStateException 乐谱违反结构或演奏约束时
     */
    public void validate() {
        int expectedMeasures = tracks.getFirst().measures().size();
        for (Track track : tracks) {
            if (track.measures().size() != expectedMeasures) {
                throw new IllegalStateException("All tracks must contain the same number of measures");
            }
            track.measures().forEach(measure -> measure.validateDuration(timeSignature));
            track.validatePlayability(5);
        }
    }
}
