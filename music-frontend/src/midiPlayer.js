/**
 * @typedef {Object} MidiEvent
 * @property {number} tick 事件相对曲首的 PPQ tick。
 * @property {number} note MIDI 音高编号。
 * @property {boolean} on 是否为 note-on；velocity 为零的 note-on 会转换为 note-off。
 * @property {number} [velocity] note-on 力度。
 */

/**
 * @typedef {Object} MidiPlayer
 * @property {(buffer: ArrayBuffer, onFinished?: () => void) => Promise<void>} play 停止旧播放并调度新的 MIDI 数据。
 * @property {() => void} stop 停止当前已调度的音源，不关闭共享 AudioContext。
 */

/**
 * 读取 MIDI 变长整数，并原地推进共享解析偏移量。
 *
 * @param {DataView} view MIDI 二进制视图。
 * @param {{offset: number}} state 当前读取位置。
 * @returns {number} 最多四字节编码的非负整数。
 */
function readVariableLength(view, state) {
  // MIDI delta-time 使用每字节 7 位有效数据的变长整数，最高位表示后续字节。
  let value = 0
  for (let index = 0; index < 4; index += 1) {
    const byte = view.getUint8(state.offset++)
    value = (value << 7) | (byte & 0x7f)
    if ((byte & 0x80) === 0) break
  }
  return value
}

/**
 * 解析项目导出的定速 PPQ MIDI，提取用于轻量试听的音符事件。
 *
 * 解析器支持 track chunk、running status、tempo meta event 和 note-on/off，
 * 但不保留轨道、通道或完整 tempo map，也不支持 SMPTE division。
 *
 * @param {ArrayBuffer} buffer 完整 MIDI 文件内容。
 * @returns {{events: MidiEvent[], secondsPerTick: number}} 试听事件及每 tick 秒数。
 * @throws {Error} MIDI header、轨道或时间格式不受支持时抛出。
 */
function parseMidi(buffer) {
  const view = new DataView(buffer)
  if (view.getUint32(0) !== 0x4d546864) throw new Error('不是有效的 MIDI 文件')
  const headerLength = view.getUint32(4)
  const trackCount = view.getUint16(10)
  const division = view.getUint16(12)
  if (division & 0x8000) throw new Error('暂不支持 SMPTE 时间格式的 MIDI')
  const events = []
  // 播放器只保留一个全局 tempo，因此适合项目生成的定速 MIDI，不能精确还原中途变速。
  let tempo = 500000
  let offset = 8 + headerLength
  for (let track = 0; track < trackCount; track += 1) {
    if (view.getUint32(offset) !== 0x4d54726b) throw new Error('MIDI 轨道数据损坏')
    const end = offset + 8 + view.getUint32(offset + 4)
    const state = { offset: offset + 8 }
    let tick = 0
    let runningStatus = 0
    while (state.offset < end) {
      tick += readVariableLength(view, state)
      let status = view.getUint8(state.offset++)
      // Running status 允许连续通道事件省略状态字节，解析时必须复用上一个状态。
      if (status < 0x80) { state.offset--; status = runningStatus } else if (status < 0xf0) runningStatus = status
      if (status === 0xff) {
        const type = view.getUint8(state.offset++)
        const length = readVariableLength(view, state)
        if (type === 0x51 && length === 3) tempo = (view.getUint8(state.offset) << 16) | (view.getUint8(state.offset + 1) << 8) | view.getUint8(state.offset + 2)
        state.offset += length
      } else if (status === 0xf0 || status === 0xf7) state.offset += readVariableLength(view, state)
      else {
        const command = status & 0xf0
        const note = view.getUint8(state.offset++)
        // Program Change 与 Channel Pressure 只有一个数据字节，其余通道消息消费两个。
        const value = (command === 0xc0 || command === 0xd0) ? 0 : view.getUint8(state.offset++)
        if (command === 0x90 && value > 0) events.push({ tick, note, on: true, velocity: value })
        else if (command === 0x80 || (command === 0x90 && value === 0)) events.push({ tick, note, on: false })
      }
    }
    offset = end
  }
  // tempo 的单位是微秒/四分音符，除以 PPQ division 后得到每 tick 秒数。
  return { events, secondsPerTick: tempo / 1000000 / division }
}

/**
 * 创建基于 Web Audio 三角波合成的项目 MIDI 试听器。
 *
 * 该播放器不加载 General MIDI 音色库；完成回调只表示调度窗口结束，
 * 不是音频设备的精确播放完成事件。
 *
 * @returns {MidiPlayer} 可复用的浏览器端播放器。
 */
export function createMidiPlayer() {
  let context
  let sources = []
  let finishTimer
  /** 停止当前调度并清除完成回调；重复停止保持幂等。 */
  function stop() {
    clearTimeout(finishTimer)
    sources.forEach(source => { try { source.stop() } catch { /* already stopped */ } })
    sources = []
  }
  /**
   * @param {ArrayBuffer} buffer 项目导出的 MIDI 文件内容。
   * @param {() => void} [onFinished] 调度时长结束后的可选回调。
   * @returns {Promise<void>} AudioContext 恢复且全部事件完成调度时兑现。
   */
  async function play(buffer, onFinished) {
    stop()
    context ||= new AudioContext()
    // resume 必须沿用户交互链调用，否则浏览器的自动播放策略可能保持 AudioContext 挂起。
    await context.resume()
    const { events, secondsPerTick } = parseMidi(buffer)
    // 以音高为键适用于本项目的简单素材；跨轨、跨通道或重叠同音会共享同一个活动音源。
    const active = new Map()
    let duration = 0
    for (const event of events) {
      // 预留 40ms 调度窗口，避免 Web Audio 在“当前时刻”创建节点造成首音丢失。
      const time = context.currentTime + 0.04 + event.tick * secondsPerTick
      duration = Math.max(duration, event.tick * secondsPerTick)
      if (event.on) {
        const oscillator = context.createOscillator()
        const gain = context.createGain()
        // 十二平均律以 MIDI 69 的 A4=440Hz 为基准换算频率。
        oscillator.type = 'triangle'; oscillator.frequency.value = 440 * 2 ** ((event.note - 69) / 12)
        gain.gain.setValueAtTime(Math.max(0.015, event.velocity / 127 * 0.12), time)
        oscillator.connect(gain).connect(context.destination); oscillator.start(time)
        active.set(event.note, { oscillator, gain }); sources.push(oscillator)
      } else {
        const voice = active.get(event.note)
        // exponentialRamp 不能以零为目标，因此先衰减到近静音值再停止音源。
        if (voice) { voice.gain.gain.exponentialRampToValueAtTime(0.001, time + 0.04); voice.oscillator.stop(time + 0.05); active.delete(event.note) }
      }
    }
    finishTimer = setTimeout(() => { sources = []; onFinished?.() }, (duration + 0.15) * 1000)
  }
  return { play, stop }
}
