<script setup>
import PipelineCard from './PipelineCard.vue'

defineProps({
  pipelines: { type: Array, default: () => [] }
})

defineEmits(['select', 'back'])
</script>

<template>
  <div class="grid-container">
    <div class="header">
      <button class="btn-back" @click="$emit('back')">← 角色</button>
      <h1>Pipeline 列表</h1>
    </div>
    <div class="grid">
      <PipelineCard
        v-for="p in pipelines"
        :key="p.id"
        :pipeline="p"
        @click="$emit('select', $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.grid-container {
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
  font-size: 24px;
  font-weight: 600;
  color: #e8e8f0;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
}
</style>
