function readVariableLength(view, state) {
  let value = 0
  for (let index = 0; index < 4; index += 1) {
    const byte = view.getUint8(state.offset++)
    value = (value << 7) | (byte & 0x7f)
    if ((byte & 0x80) === 0) break
  }
  return value
}

function parseMidi(buffer) {
  const view = new DataView(buffer)
  if (view.getUint32(0) !== 0x4d546864) throw new Error('不是有效的 MIDI 文件')
  const headerLength = view.getUint32(4)
  const trackCount = view.getUint16(10)
  const division = view.getUint16(12)
  if (division & 0x8000) throw new Error('暂不支持 SMPTE 时间格式的 MIDI')
  const events = []
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
        const value = (command === 0xc0 || command === 0xd0) ? 0 : view.getUint8(state.offset++)
        if (command === 0x90 && value > 0) events.push({ tick, note, on: true, velocity: value })
        else if (command === 0x80 || (command === 0x90 && value === 0)) events.push({ tick, note, on: false })
      }
    }
    offset = end
  }
  return { events, secondsPerTick: tempo / 1000000 / division }
}

export function createMidiPlayer() {
  let context
  let sources = []
  let finishTimer
  function stop() {
    clearTimeout(finishTimer)
    sources.forEach(source => { try { source.stop() } catch { /* already stopped */ } })
    sources = []
  }
  async function play(buffer, onFinished) {
    stop()
    context ||= new AudioContext()
    await context.resume()
    const { events, secondsPerTick } = parseMidi(buffer)
    const active = new Map()
    let duration = 0
    for (const event of events) {
      const time = context.currentTime + 0.04 + event.tick * secondsPerTick
      duration = Math.max(duration, event.tick * secondsPerTick)
      if (event.on) {
        const oscillator = context.createOscillator()
        const gain = context.createGain()
        oscillator.type = 'triangle'; oscillator.frequency.value = 440 * 2 ** ((event.note - 69) / 12)
        gain.gain.setValueAtTime(Math.max(0.015, event.velocity / 127 * 0.12), time)
        oscillator.connect(gain).connect(context.destination); oscillator.start(time)
        active.set(event.note, { oscillator, gain }); sources.push(oscillator)
      } else {
        const voice = active.get(event.note)
        if (voice) { voice.gain.gain.exponentialRampToValueAtTime(0.001, time + 0.04); voice.oscillator.stop(time + 0.05); active.delete(event.note) }
      }
    }
    finishTimer = setTimeout(() => { sources = []; onFinished?.() }, (duration + 0.15) * 1000)
  }
  return { play, stop }
}
