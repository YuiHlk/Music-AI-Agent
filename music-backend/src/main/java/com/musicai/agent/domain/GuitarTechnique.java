package com.musicai.agent.domain;

/**
 * 定义吉他音符可采用的演奏技法。
 */
public enum GuitarTechnique {
    /** 常规拨弦，不附加特殊技法。 */
    NORMAL,
    /** 手掌制音，获得短促、低沉的音色。 */
    PALM_MUTE,
    /** 击弦，由较低音通过左手敲击连接到较高音。 */
    HAMMER_ON,
    /** 勾弦，由较高音通过左手离弦连接到较低音。 */
    PULL_OFF,
    /** 滑音，在同一琴弦上连续移动品位。 */
    SLIDE,
    /** 推弦，通过改变弦张力抬高音高。 */
    BEND,
    /** 揉弦，使持续音产生周期性音高波动。 */
    VIBRATO
}
