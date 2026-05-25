<script setup>
defineProps({
  slots: { type: Array, default: () => [] }
})
</script>

<template>
  <div class="prompt-list">
    <div v-for="slot in slots" :key="slot.slotId" class="slot-item">
      <div class="slot-header">
        <span class="slot-name">{{ slot.slotName }}</span>
        <span class="slot-index">#{{ slot.orderIndex + 1 }}</span>
      </div>
      <div class="slot-content">{{ slot.content || '（未填充）' }}</div>
    </div>
  </div>
</template>

<style scoped>
.prompt-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.slot-item {
  background: #151528;
  border-radius: 8px;
  padding: 12px 16px;
  border: 1px solid #2a2a4a;
}
.slot-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.slot-name {
  font-size: 14px;
  font-weight: 600;
  color: #c0c0e0;
}
.slot-index {
  font-size: 11px;
  color: #6a6a9a;
}
.slot-content {
  font-size: 13px;
  color: #a0a0c0;
  line-height: 1.5;
  white-space: pre-wrap;
}
</style>
