<script setup>
import SlotPromptList from './SlotPromptList.vue'
import SessionImageGallery from './SessionImageGallery.vue'

defineProps({
  session: { type: Object, required: true }
})

defineEmits(['back', 'imageUploaded'])
</script>

<template>
  <div class="container">
    <div class="header">
      <button class="btn-back" @click="$emit('back')">← 产物列表</button>
      <h1>{{ session.pipelineName }} — Session 详情</h1>
    </div>

    <div class="meta">
      <span v-if="session.worldSetting">世界观: {{ session.worldSetting }}</span>
      <span v-if="session.characterName">角色: {{ session.characterName }}</span>
      <span>状态: {{ session.status }}</span>
    </div>

    <div class="section">
      <h2>组装内容</h2>
      <SlotPromptList :slots="session.slots" />
    </div>

    <SessionImageGallery
      :sessionId="session.id"
      :images="session.images"
      @uploaded="$emit('imageUploaded')"
    />
  </div>
</template>

<style scoped>
.container {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px;
}
.header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
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
.meta {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
  font-size: 13px;
  color: #8a8ac4;
}
.section h2 {
  font-size: 16px;
  color: #e8e8f0;
  margin-bottom: 12px;
}
</style>
