package com.musicai.agent.application;

import com.musicai.agent.domain.KeySignature;
import com.musicai.agent.domain.Measure;
import com.musicai.agent.domain.MusicalEvent;
import com.musicai.agent.domain.NoteEvent;
import com.musicai.agent.domain.Pitch;
import com.musicai.agent.domain.RestEvent;
import com.musicai.agent.domain.RhythmicDuration;
import com.musicai.agent.domain.Score;
import com.musicai.agent.domain.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * 根据结构化创作约束生成可复现且可演奏的单轨吉他 Riff 乐谱。
 */
public final class GuitarRiffGenerator {

    // 音阶以相对根音的半音数表示，使同一套生成规则可以直接移调到任意调性。
    private static final int[] MAJOR_SCALE = {0, 2, 4, 5, 7, 9, 11};
    private static final int[] MINOR_SCALE = {0, 2, 3, 5, 7, 8, 10};
    private static final int[] MINOR_PENTATONIC = {0, 3, 5, 7, 10};
    private static final int[] BLUES_SCALE = {0, 3, 5, 6, 7, 10};

    /**
     * 生成并完整校验一份吉他 Riff 乐谱。
     *
     * @param constraints 创作与结构约束
     * @return 对相同约束可确定性复现的乐谱
     */
    public Score generate(CreationConstraints constraints) {
        KeySignature key = KeySignature.parse(constraints.keySignature());
        int[] scale = scaleFor(constraints, key);
        int basePitch = playableRoot(key.rootPitchClass(), constraints);
        int subdivisions = subdivisions(constraints);
        //Math.multiplyExact()和普通乘法的区别在于：整数溢出时会抛出ArithmeticException，而不是悄悄产生错误数值。
        int slotsPerMeasure = Math.multiplyExact(constraints.timeSignature().beats(), subdivisions);
        RhythmicDuration slotDuration = new RhythmicDuration(1,
                Math.multiplyExact(constraints.timeSignature().beatUnit(), subdivisions));
        // 固定约束产生固定种子：既允许复现结果，也让关键提示词变化真正进入音符序列。
        SplittableRandom random = new SplittableRandom(mixSeed(constraints));
        int[] motif = createMotif(slotsPerMeasure, scale.length, constraints, random);

        List<Measure> measures = new ArrayList<>();
        for (int measureNumber = 1; measureNumber <= constraints.measures(); measureNumber++) {
            measures.add(createMeasure(measureNumber, constraints, scale, basePitch, motif,
                    slotDuration, subdivisions, random.split()));
        }
        Score score = new Score("Generated " + constraints.mood().name().toLowerCase() + " "
                + constraints.style() + " guitar riff", constraints.tempo(), constraints.keySignature(),
                constraints.timeSignature(), List.of(new Track("Electric Guitar", constraints.tuning(), measures)));
        score.validate();
        return score;
    }

    private static Measure createMeasure(int measureNumber, CreationConstraints constraints, int[] scale,
                                         int basePitch, int[] motif, RhythmicDuration duration,
                                         int subdivisions, SplittableRandom random) {
        List<MusicalEvent> events = new ArrayList<>(motif.length);
        for (int slot = 0; slot < motif.length; slot++) {
            if (shouldRest(slot, measureNumber, subdivisions, constraints, random)) {
                events.add(new RestEvent(duration));
                continue;
            }
            int degree = variedDegree(motif[slot], slot, measureNumber, scale.length, constraints, random);
            int octave = degree / scale.length;
            int pitchNumber = basePitch + scale[degree % scale.length] + octave * 12;
            Pitch pitch = new Pitch(pitchNumber);
            events.add(new NoteEvent(pitch, duration, constraints.tuning().requirePlayablePosition(pitch, 22)));
        }
        return new Measure(measureNumber, events);
    }

    private static int[] createMotif(int slots, int scaleSize, CreationConstraints constraints,
                                     SplittableRandom random) {
        int[] motif = new int[slots];
        int availableDegrees = Math.min(scaleSize, constraints.complexity() + 2);
        for (int slot = 0; slot < slots; slot++) {
            if (slot == 0 || slot % Math.max(2, slots / 2) == 0) {
                motif[slot] = 0;
            } else if (isHeavyStyle(constraints.style()) && slot % 2 == 0) {
                motif[slot] = random.nextInt(100) < 65 ? 0 : random.nextInt(availableDegrees);
            } else {
                motif[slot] = random.nextInt(availableDegrees);
            }
        }
        return motif;
    }

    private static int variedDegree(int original, int slot, int measureNumber, int scaleSize,
                                    CreationConstraints constraints, SplittableRandom random) {
        // 每四小节重新落到主音，避免随机变化破坏 Riff 的乐句边界。
        if (slot == 0 && (measureNumber == 1 || measureNumber % 4 == 1)) {
            return 0;
        }
        int degree = original;
        if (measureNumber % 4 == 3 && slot % 3 == 2) {
            degree = Math.floorMod(degree + 1, scaleSize);
        } else if (measureNumber % 4 == 0 && slot >= Math.max(1, constraints.timeSignature().beats())) {
            degree = slot == constraints.timeSignature().beats() ? Math.min(4, scaleSize - 1) : 0;
        } else if (constraints.complexity() >= 4 && random.nextInt(100) < 18) {
            degree = Math.floorMod(degree + (random.nextBoolean() ? 1 : -1), scaleSize);
        }
        if (constraints.mood() == MusicalMood.BRIGHT && constraints.complexity() >= 3 && slot % 4 == 3) {
            degree += scaleSize;
        }
        return degree;
    }

    private static boolean shouldRest(int slot, int measure, int subdivisions, CreationConstraints constraints,
                                      SplittableRandom random) {
        if (slot == 0 || slot == constraints.timeSignature().beats() * subdivisions - 1) {
            return false;
        }
        if (constraints.rhythmicFeel() == RhythmicFeel.SYNCOPATED && slot % subdivisions == 1) {
            return true;
        }
        int chance = switch (constraints.mood()) {
            case CALM -> 22;
            case MELANCHOLIC -> 14;
            case AGGRESSIVE, ENERGETIC -> 3;
            default -> 8;
        };
        return (measure + slot) % 4 != 0 && random.nextInt(100) < chance;
    }

    private static int[] scaleFor(CreationConstraints constraints, KeySignature key) {
        if (constraints.style().contains("blues")) {
            return BLUES_SCALE;
        }
        if (constraints.style().contains("rock") || constraints.style().contains("metal")) {
            return key.mode() == KeySignature.Mode.MINOR ? MINOR_PENTATONIC
                    : MAJOR_SCALE;
        }
        return key.mode() == KeySignature.Mode.MAJOR ? MAJOR_SCALE : MINOR_SCALE;
    }

    private static int playableRoot(int pitchClass, CreationConstraints constraints) {
        // 从当前调弦最低音向上寻找根音，防止生成器先产生无法映射到指板的低音。
        int lowestOpenPitch = constraints.tuning().openStringsHighToLow().stream()
                .mapToInt(Pitch::midiNumber).min().orElseThrow();
        // MIDI 38 是 D2：它限制 Riff 的最低音区；若最低空弦更高，则改用实际调弦边界。
        int pitch = Math.max(38, lowestOpenPitch);
        while (Math.floorMod(pitch, 12) != pitchClass) {
            pitch++;
        }
        // 高于 D#3 时下移一个八度，使根音回到主要 Riff 音区且仍不低于 D2。
        return pitch > 51 ? pitch - 12 : pitch;
    }

    private static int subdivisions(CreationConstraints constraints) {
        return switch (constraints.rhythmicFeel()) {
            case HALF_TIME -> 1;
            case STRAIGHT -> 2;
            case SYNCOPATED -> 4;
            case DRIVING -> constraints.complexity() >= 3 ? 4 : 2;
        };
    }

    private static boolean isHeavyStyle(String style) {
        return style.contains("rock") || style.contains("metal");
    }

    private static long mixSeed(CreationConstraints constraints) {
        // variationSeed 来自原始提示词，其余字段再次混入以避免不同结构化约束发生碰撞。
        long seed = constraints.variationSeed();
        seed = 31 * seed + constraints.keySignature().toLowerCase().hashCode();
        seed = 31 * seed + constraints.style().hashCode();
        seed = 31 * seed + constraints.mood().ordinal();
        seed = 31 * seed + constraints.rhythmicFeel().ordinal();
        return 31 * seed + constraints.complexity();
    }
}
