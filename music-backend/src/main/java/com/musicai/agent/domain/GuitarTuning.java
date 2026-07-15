package com.musicai.agent.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 表示六弦吉他的调弦方案，空弦按最高音弦到最低音弦排列。
 *
 * @param name 调弦方案名称
 * @param openStringsHighToLow 六根空弦的绝对音高
 */
public record GuitarTuning(String name, List<Pitch> openStringsHighToLow) {

    /** 标准 EADGBE 调弦。 */
    public static final GuitarTuning STANDARD = new GuitarTuning("Standard E", List.of(
            new Pitch(64), new Pitch(59), new Pitch(55), new Pitch(50), new Pitch(45), new Pitch(40)));
    /** 将最低音弦降至 D2 的 Drop D 调弦。 */
    public static final GuitarTuning DROP_D = new GuitarTuning("Drop D", List.of(
            new Pitch(64), new Pitch(59), new Pitch(55), new Pitch(50), new Pitch(45), new Pitch(38)));

    /**
     * 创建调弦并固定空弦音高快照。
     */
    public GuitarTuning {
        openStringsHighToLow = List.copyOf(openStringsHighToLow);
        if (openStringsHighToLow.size() != 6) {
            throw new IllegalArgumentException("A guitar tuning must contain exactly six strings");
        }
    }

    /**
     * 查找指定音高在当前调弦上的全部可演奏位置。
     *
     * @param pitch 目标绝对音高
     * @param maximumFret 允许使用的最高品位
     * @return 按低品位、再按高音弦优先排序的位置列表
     */
    public List<FretPosition> positionsFor(Pitch pitch, int maximumFret) {
        if (maximumFret < 0 || maximumFret > 24) {
            throw new IllegalArgumentException("Maximum fret must be between 0 and 24");
        }
        List<FretPosition> positions = new ArrayList<>();
        for (int index = 0; index < openStringsHighToLow.size(); index++) {
            int fret = pitch.midiNumber() - openStringsHighToLow.get(index).midiNumber();
            if (fret >= 0 && fret <= maximumFret) {
                positions.add(new FretPosition(index + 1, fret));
            }
        }
        // 首选低品位；同品位时首选编号较小的高音弦，使自动指法稳定且便于复现。
        return positions.stream()
                .sorted(Comparator.comparingInt(FretPosition::fret).thenComparingInt(FretPosition::stringNumber))
                .toList();
    }

    /**
     * 取得指定音高的首选可演奏位置。
     *
     * @param pitch 目标绝对音高
     * @param maximumFret 允许使用的最高品位
     * @return 确定性排序后的首选位置
     * @throws IllegalArgumentException 指定品位范围内不存在可演奏位置时
     */
    public FretPosition requirePlayablePosition(Pitch pitch, int maximumFret) {
        return positionsFor(pitch, maximumFret).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Pitch " + pitch.midiNumber() + " is not playable"));
    }
}
