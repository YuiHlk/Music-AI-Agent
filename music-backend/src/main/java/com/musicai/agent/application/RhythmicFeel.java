package com.musicai.agent.application;

/**
 * 定义生成器的节奏切分密度与重音感觉。
 */
public enum RhythmicFeel {
    /** 平直节奏，以每拍两个均分槽位推进。 */
    STRAIGHT,
    /** 切分节奏，通过细分拍位上的休止制造错位重音。 */
    SYNCOPATED,
    /** 驱动型节奏，按复杂度选择较密集的细分。 */
    DRIVING,
    /** 半拍感，每拍仅保留一个较长槽位。 */
    HALF_TIME
}
