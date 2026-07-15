<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createMidiPlayer } from './midiPlayer.js'

/**
 * 工作台在单一 MVP 组件中协调认证、项目、异步生成、SSE、轮询、乐谱预览与 MIDI 试听。
 * @typedef {{id: string, title: string, status: string, currentVersion: number}} StoredProject
 * @typedef {{id: string, status: string, errorMessage?: string}} StoredTask
 * @typedef {{id: string, type: string, versionNumber: number}} StoredArtifact
 * @typedef {{type: string, notes?: Array<{stringNumber: number, fret: number}>}} ScoreEvent
 */

const title = ref('My first rock riff')
const prompt = ref('生成一段 8 小节、120 BPM、E 小调、标准调弦的摇滚吉他 Riff')
const project = ref(null)
const projectList = ref([])
const task = ref(null)
const artifacts = ref([])
const score = ref(null)
const timeline = ref([])
const busy = ref(false)
const playing = ref(false)
const authRequired = ref(false)
const accessKey = ref('')
const midiPlayer = createMidiPlayer()
let eventSource
let pollTimer

// 这是面向用户的阶段近似值；FAILED=100 表示流程已终止，并不表示成功完成。
const progress = computed(() => ({ PENDING: 5, PARSING_REQUIREMENTS: 20, PLANNING: 34,
  GENERATING: 48, VALIDATING: 68, EXPORTING: 86, COMPLETED: 100, FAILED: 100 })[task.value?.status] ?? 0)
const currentMidi = computed(() => artifacts.value.filter(item => item.type === 'MIDI')
  .sort((a, b) => b.versionNumber - a.versionNumber)[0])

/**
 * 调用同源 JSON API，并把 401 同步为全局认证界面状态。
 *
 * @param {string} path API 相对路径。
 * @param {RequestInit} [options] fetch 选项；调用方 header 可覆盖默认 JSON 类型。
 * @returns {Promise<any>} 204 时为 null，其余成功响应为 JSON。
 * @throws {Error} 响应非成功状态时抛出服务端消息或 HTTP 状态。
 */
async function api(path, options = {}) {
  const response = await fetch(path, { headers: { 'Content-Type': 'application/json', ...options.headers }, ...options })
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    if (response.status === 401) authRequired.value = true
    throw new Error(body.message || `请求失败 (${response.status})`)
  }
  if (response.status === 204) return null
  return response.json()
}

/** 用最近 30 个项目替换项目浏览列表。 */
async function loadProjects() { projectList.value = await api('/api/projects?limit=30') }
/** 初始化服务端安全状态，并仅在会话可用时加载项目。 */
async function initializeSession() {
  const session = await api('/api/session')
  authRequired.value = session.securityEnabled && !session.authenticated
  if (!authRequired.value) await loadProjects()
}
/** 用访问密钥换取 HttpOnly cookie 会话，密钥不会持久化到浏览器存储。 */
async function login() {
  busy.value = true
  try {
    await api('/api/session', { method: 'POST', body: JSON.stringify({ accessKey: accessKey.value }) })
    accessKey.value = ''; authRequired.value = false
    await loadProjects(); ElMessage.success('访问会话已建立')
  } catch (error) { ElMessage.error(error.message) } finally { busy.value = false }
}
/** 创建并选中新项目，同时清空上一个项目的任务、产物和乐谱状态。 */
async function createProject() {
  busy.value = true
  try {
    project.value = await api('/api/projects', { method: 'POST', body: JSON.stringify({ title: title.value }) })
    task.value = null; artifacts.value = []; score.value = null
    timeline.value = [{ type: 'PROJECT_CREATED', text: `项目 ${project.value.id.slice(0, 8)} 已创建` }]
    connectEvents(); await loadProjects(); ElMessage.success('项目已创建')
  } catch (error) { ElMessage.error(error.message) } finally { busy.value = false }
}
/**
 * 切换到项目详情，并行加载其当前乐谱和全部导出物。
 * @param {StoredProject} selected 项目列表中的摘要。
 */
async function selectProject(selected) {
  stopPlayback(); project.value = await api(`/api/projects/${selected.id}`); title.value = project.value.title
  task.value = null; timeline.value = [{ type: 'PROJECT_SELECTED', text: `已载入 ${project.value.title}` }]
  connectEvents(); await Promise.all([loadArtifacts(), loadScore()])
}
/** 创建缺失项目后提交异步生成任务；SSE 展示事件，轮询负责权威终态。 */
async function generate() {
  if (!project.value) await createProject()
  if (!project.value) return
  busy.value = true
  try {
    task.value = await api(`/api/projects/${project.value.id}/generate`, { method: 'POST', body: JSON.stringify({ prompt: prompt.value }) })
    startPolling()
  } catch (error) { ElMessage.error(error.message) } finally { busy.value = false }
}
/**
 * 关闭旧连接并订阅当前项目的内存态 SSE 事件。
 * 浏览器负责网络断线重连，EXPORT_COMPLETED 事件触发交付物刷新。
 */
function connectEvents() {
  eventSource?.close()
  eventSource = new EventSource(`/api/projects/${project.value.id}/events`)
  ;['AGENT_MESSAGE', 'PLAN_CREATED', 'SECTION_GENERATING', 'TRACK_COMPLETED', 'VALIDATION_COMPLETED',
    'EXPORTING', 'EXPORT_COMPLETED', 'TASK_FAILED'].forEach(name => eventSource.addEventListener(name, event => {
    timeline.value.push({ type: name, text: event.data })
    if (name === 'EXPORT_COMPLETED') refreshGeneratedResult()
  }))
}
/**
 * 每 700ms 查询任务权威状态，并在终态刷新项目、乐谱和导出物。
 * SSE 可能丢失或重连，因此不能单独承担任务完成判定。
 */
function startPolling() {
  clearInterval(pollTimer)
  pollTimer = setInterval(async () => {
    if (!task.value) return
    try {
      task.value = await api(`/api/tasks/${task.value.id}`)
      if (['COMPLETED', 'FAILED'].includes(task.value.status)) {
        clearInterval(pollTimer); project.value = await api(`/api/projects/${project.value.id}`)
        await refreshGeneratedResult()
        task.value.status === 'FAILED' ? ElMessage.error(task.value.errorMessage || '生成失败') : ElMessage.success('乐谱与导出文件已生成')
      }
    } catch (error) { clearInterval(pollTimer); ElMessage.error(error.message) }
  }, 700)
}
/** 并行刷新当前项目交付物、乐谱预览和项目列表摘要。 */
async function refreshGeneratedResult() { await Promise.all([loadArtifacts(), loadScore(), loadProjects()]) }
/** 在存在当前项目时替换其全部版本导出物列表。 */
async function loadArtifacts() { if (project.value) artifacts.value = await api(`/api/projects/${project.value.id}/artifacts`) }
/** 加载当前版本的稳定预览 DTO；无版本时清除旧预览。 */
async function loadScore() {
  if (!project.value?.currentVersion) { score.value = null; return }
  score.value = await api(`/api/projects/${project.value.id}/score`)
}
/**
 * 格式化一个乐谱事件在指定弦上的品位。
 * @param {ScoreEvent} event 预览事件。
 * @param {number} stringNumber 1 为最高音弦的弦号。
 * @returns {number|'·'|'—'} 品位、休止符占位或无音符占位。
 */
function fretFor(event, stringNumber) {
  if (event.type === 'REST') return '·'
  return event.notes?.find(note => note.stringNumber === stringNumber)?.fret ?? '—'
}
/**
 * 从二进制下载接口取得最新 MIDI，并沿点击事件链启动或停止 Web Audio 试听。
 * 下载不复用 JSON API 包装器，因为成功响应不是 JSON。
 */
async function togglePlayback() {
  if (playing.value) { stopPlayback(); return }
  if (!currentMidi.value) return
  try {
    const response = await fetch(`/api/artifacts/${currentMidi.value.id}/download`)
    if (!response.ok) throw new Error(`MIDI 下载失败 (${response.status})`)
    playing.value = true
    await midiPlayer.play(await response.arrayBuffer(), () => { playing.value = false })
  } catch (error) { playing.value = false; ElMessage.error(error.message) }
}
/** 停止底层音源并同步试听按钮状态。 */
function stopPlayback() { midiPlayer.stop(); playing.value = false }
onMounted(() => initializeSession().catch(error => ElMessage.error(error.message)))
// 组件卸载时同时释放长连接、轮询和 Web Audio 音源，避免后台工作泄漏。
onBeforeUnmount(() => { eventSource?.close(); clearInterval(pollTimer); midiPlayer.stop() })
</script>

<template>
  <main class="workspace">
    <header class="topbar"><div><p class="eyebrow">PLAYABLE COMPOSITION LAB</p><h1>Music AI Agent</h1></div>
      <div class="status-pill" :class="task?.status?.toLowerCase()"><span />{{ task?.status || 'READY' }}</div></header>
    <section v-if="authRequired" class="auth-panel panel">
      <div class="panel-heading compact-heading"><span>KEY</span><div><h2>访问保护</h2><p>输入部署环境配置的项目访问密钥，密钥不会写入浏览器存储。</p></div></div>
      <div class="auth-form"><el-input v-model="accessKey" type="password" show-password maxlength="512" placeholder="MUSIC_AI_ACCESS_KEY" @keyup.enter="login" />
        <el-button type="primary" :loading="busy" @click="login">建立会话</el-button></div>
    </section>
    <template v-else>
      <section class="project-browser panel">
        <div class="panel-heading compact-heading"><span>00</span><div><h2>项目库</h2><p>选择历史项目继续预览、试听或生成新版本</p></div></div>
        <div class="project-list"><button v-for="item in projectList" :key="item.id" type="button" class="project-chip" :class="{ active: project?.id === item.id }" @click="selectProject(item)">
          <strong>{{ item.title }}</strong><span>v{{ item.currentVersion }} · {{ item.status }}</span></button>
          <p v-if="!projectList.length" class="empty">还没有项目，创建第一个 Riff。</p></div>
      </section>
      <section class="hero-grid">
        <article class="composer-card panel"><div class="panel-heading"><span>01</span><div><h2>创作指令</h2><p>自然语言将被解析为可验证的音乐约束</p></div></div>
          <el-input v-model="title" size="large" maxlength="200" placeholder="项目名称" />
          <el-input v-model="prompt" type="textarea" :rows="7" maxlength="4000" show-word-limit />
          <div class="actions"><el-button :loading="busy" @click="createProject">新建项目</el-button><el-button type="primary" :loading="busy" @click="generate">生成 Riff</el-button></div>
        </article>
        <article class="progress-card panel"><div class="panel-heading"><span>02</span><div><h2>生成进度</h2><p>领域校验通过后才会导出文件</p></div></div>
          <div class="meter"><strong>{{ progress }}%</strong><el-progress :percentage="progress" :show-text="false" :stroke-width="8" /></div>
          <dl class="project-stats"><div><dt>项目</dt><dd>{{ project?.id?.slice(0, 8) || '—' }}</dd></div><div><dt>版本</dt><dd>v{{ project?.currentVersion || 0 }}</dd></div><div><dt>状态</dt><dd>{{ task?.status || project?.status || '等待指令' }}</dd></div></dl>
          <div class="timeline"><div v-for="(item, index) in timeline.slice(-5)" :key="index" class="timeline-item"><i /><div><strong>{{ item.type }}</strong><p>{{ item.text }}</p></div></div><p v-if="!timeline.length" class="empty">事件流将在这里显示</p></div>
        </article>
      </section>
      <section class="score-panel panel"><div class="panel-heading score-heading"><span>03</span><div><h2>六线谱预览</h2>
        <p v-if="score">{{ score.tempo }} BPM · {{ score.keySignature }} · {{ score.timeSignature }} · {{ score.tracks?.[0]?.tuning }}</p><p v-else>生成或选择已有版本后显示</p></div>
        <el-button :disabled="!currentMidi" type="primary" plain @click="togglePlayback">{{ playing ? '停止试听' : '试听 MIDI' }}</el-button></div>
        <div v-if="score?.tracks?.[0]" class="tab-score"><article v-for="measure in score.tracks[0].measures" :key="measure.number" class="tab-measure"><b>小节 {{ measure.number }}</b>
          <div v-for="stringNumber in 6" :key="stringNumber" class="tab-string"><span>{{ stringNumber }}</span><i v-for="(event, eventIndex) in measure.events" :key="eventIndex">{{ fretFor(event, stringNumber) }}</i></div></article></div>
        <p v-else class="artifact-placeholder">当前项目还没有可预览的乐谱版本。</p>
      </section>
      <section class="artifacts panel"><div class="panel-heading"><span>04</span><div><h2>可交付文件</h2><p>下载后可导入 Guitar Pro 8 继续编辑</p></div></div>
        <div class="artifact-grid"><a v-for="artifact in artifacts" :key="artifact.id" :href="`/api/artifacts/${artifact.id}/download`" class="artifact-card"><b>{{ artifact.type }}</b><span>版本 {{ artifact.versionNumber }}</span><em>下载 →</em></a>
          <div v-if="!artifacts.length" class="artifact-placeholder">生成完成后，MIDI 与 MusicXML 将出现在这里。</div></div>
      </section>
    </template>
  </main>
</template>
