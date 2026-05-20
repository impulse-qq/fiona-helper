<script setup>
import { ref, onMounted } from 'vue'
import CharacterGrid from './components/CharacterGrid.vue'
import CharacterForm from './components/CharacterForm.vue'
import PipelineList from './components/PipelineList.vue'
import SessionList from './components/SessionList.vue'
import SessionDetail from './components/SessionDetail.vue'
import api from './api/client.js'

const view = ref('characters') // 'characters' | 'pipelines' | 'sessions' | 'sessionDetail'
const characters = ref([])
const pipelines = ref([])
const sessions = ref([])
const selectedPipeline = ref(null)
const selectedSession = ref(null)
const showForm = ref(false)
const editingCharacter = ref(null)
const loading = ref(false)

onMounted(async () => {
  await loadCharacters()
  await loadPipelines()
})

async function loadCharacters() {
  try { characters.value = await api.listCharacters() } catch (e) { console.error(e) }
}

async function loadPipelines() {
  try { pipelines.value = await api.listPipelines() } catch (e) { console.error(e) }
}

async function loadSessions(pipelineId) {
  try { sessions.value = await api.getPipelineSessions(pipelineId) } catch (e) { console.error(e) }
}

async function loadSessionDetail(sessionId) {
  try {
    const detail = await api.getSession(sessionId)
    selectedSession.value = detail
  } catch (e) { console.error(e) }
}

// Character actions
function onCreateCharacter() {
  editingCharacter.value = null
  showForm.value = true
}
function onEditCharacter(c) {
  editingCharacter.value = c
  showForm.value = true
}
function closeForm() {
  showForm.value = false
  editingCharacter.value = null
}
async function onSaveCharacter(data) {
  try {
    if (editingCharacter.value) {
      await api.updateCharacter(editingCharacter.value.id, data)
    } else {
      await api.createCharacter(data)
    }
    closeForm()
    await loadCharacters()
  } catch (err) {
    alert('保存失败: ' + err.message)
  }
}
async function onDeleteCharacter(c) {
  if (!confirm(`确定删除角色「${c.name}」?`)) return
  try {
    await api.deleteCharacter(c.id)
    await loadCharacters()
  } catch (err) {
    alert('删除失败: ' + err.message)
  }
}

// Navigation
function goToPipelines() {
  view.value = 'pipelines'
}
function goToCharacters() {
  view.value = 'characters'
}
async function onSelectPipeline(pipeline) {
  selectedPipeline.value = pipeline
  await loadSessions(pipeline.id)
  view.value = 'sessions'
}
function onBackFromSessions() {
  view.value = 'pipelines'
  selectedPipeline.value = null
  sessions.value = []
}
async function onSelectSession(session) {
  await loadSessionDetail(session.id)
  view.value = 'sessionDetail'
}
function onBackFromSessionDetail() {
  view.value = 'sessions'
  selectedSession.value = null
}
async function onSessionImageUploaded() {
  if (selectedSession.value) {
    await loadSessionDetail(selectedSession.value.id)
  }
}
</script>

<template>
  <div class="app">
    <nav class="nav">
      <button :class="{ active: view === 'characters' }" @click="goToCharacters">角色</button>
      <button :class="{ active: view === 'pipelines' || view === 'sessions' || view === 'sessionDetail' }" @click="goToPipelines">Pipeline</button>
    </nav>

    <CharacterGrid
      v-if="view === 'characters'"
      :characters="characters"
      @select="onEditCharacter"
      @create="onCreateCharacter"
    />

    <PipelineList
      v-if="view === 'pipelines'"
      :pipelines="pipelines"
      @select="onSelectPipeline"
      @back="goToCharacters"
    />

    <SessionList
      v-if="view === 'sessions'"
      :pipeline="selectedPipeline"
      :sessions="sessions"
      @select="onSelectSession"
      @back="onBackFromSessions"
    />

    <SessionDetail
      v-if="view === 'sessionDetail' && selectedSession"
      :session="selectedSession"
      @back="onBackFromSessionDetail"
      @imageUploaded="onSessionImageUploaded"
    />

    <CharacterForm
      v-if="showForm"
      :character="editingCharacter"
      @save="onSaveCharacter"
      @cancel="closeForm"
    />
  </div>
</template>

<style scoped>
.app {
  min-height: 100vh;
}
.nav {
  display: flex;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  background: #151528;
  border-bottom: 1px solid #2a2a4a;
}
.nav button {
  padding: 8px 20px;
  border-radius: 8px;
  border: none;
  background: transparent;
  color: #8a8ac4;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}
.nav button:hover {
  color: #e0e0e8;
}
.nav button.active {
  background: #2a2a4a;
  color: #e8e8f0;
}
</style>
