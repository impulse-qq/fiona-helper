<script setup>
defineProps({
  session: { type: Object, required: true }
})

defineEmits(['click'])

function formatDate(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString('zh-CN')
}
</script>

<template>
  <div class="card" @click="$emit('click', session)">
    <div class="top">
      <span class="char">{{ session.characterName || '无角色' }}</span>
      <span v-if="session.imageCount > 0" class="badge">{{ session.imageCount }} 🖼</span>
    </div>
    <div class="date">{{ formatDate(session.createdAt) }}</div>
  </div>
</template>

<style scoped>
.card {
  background: #1a1a2e;
  border-radius: 12px;
  padding: 16px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  border: 1px solid #2a2a4a;
}
.card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.4);
  border-color: #4a4a8a;
}
.top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.char {
  font-size: 15px;
  font-weight: 600;
  color: #e8e8f0;
}
.badge {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  background: #2d2d5a;
  color: #a0a0d0;
}
.date {
  font-size: 12px;
  color: #6a6a9a;
}
</style>
