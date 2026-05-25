<script setup>
import SessionCard from './SessionCard.vue'

defineProps({
  pipeline: { type: Object, required: true },
  sessions: { type: Array, default: () => [] }
})

defineEmits(['select', 'back'])
</script>

<template>
  <div class="container">
    <div class="header">
      <button class="btn-back" @click="$emit('back')">← Pipeline 列表</button>
      <h1>{{ pipeline.name }} — 产物列表</h1>
    </div>
    <div v-if="sessions.length === 0" class="empty">暂无完成的 session</div>
    <div class="grid">
      <SessionCard
        v-for="s in sessions"
        :key="s.id"
        :session="s"
        @click="$emit('select', $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}
.header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}
.btn-back {
  padding: 8px 16px;
  border-radius: 8px;
  border: none;
  background: #2a2a4a;
  color: #c0c0d0;
  cursor: pointer;
  font-size: 14px;
}
.btn-back:hover {
  background: #3a3a6a;
}
.header h1 {
  font-size: 20px;
  font-weight: 600;
  color: #e8e8f0;
}
.empty {
  text-align: center;
  color: #6a6a9a;
  padding: 48px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}
</style>
