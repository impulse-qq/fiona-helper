<script setup>
import { ref, onMounted } from 'vue'
import CharacterGrid from './components/CharacterGrid.vue'
import CharacterForm from './components/CharacterForm.vue'
import api from './api/client.js'

const characters = ref([])
const showForm = ref(false)
const editingCharacter = ref(null)
const loading = ref(false)

onMounted(load)

async function load() {
  loading.value = true
  try {
    characters.value = await api.listCharacters()
  } catch (err) {
    console.error('加载角色失败:', err)
  } finally {
    loading.value = false
  }
}

function onCreate() {
  editingCharacter.value = null
  showForm.value = true
}

function onEdit(c) {
  editingCharacter.value = c
  showForm.value = true
}

function closeForm() {
  showForm.value = false
  editingCharacter.value = null
}

async function onSave(data) {
  try {
    if (editingCharacter.value) {
      await api.updateCharacter(editingCharacter.value.id, data)
    } else {
      await api.createCharacter(data)
    }
    closeForm()
    await load()
  } catch (err) {
    alert('保存失败: ' + err.message)
  }
}

async function onDelete(c) {
  if (!confirm(`确定删除角色「${c.name}」?`)) return
  try {
    await api.deleteCharacter(c.id)
    await load()
  } catch (err) {
    alert('删除失败: ' + err.message)
  }
}
</script>

<template>
  <div class="app">
    <CharacterGrid
      :characters="characters"
      @select="onEdit"
      @create="onCreate"
    />

    <CharacterForm
      v-if="showForm"
      :character="editingCharacter"
      @save="onSave"
      @cancel="closeForm"
    />
  </div>
</template>

<style scoped>
.app {
  min-height: 100vh;
}
</style>
