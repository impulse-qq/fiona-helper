<script setup>
defineProps({
  character: { type: Object, required: true }
})

defineEmits(['click'])

function initials(name) {
  return name ? name.charAt(0).toUpperCase() : '?'
}

function tags(personality) {
  if (!personality) return []
  return personality.split(/[,，]/).map(t => t.trim()).filter(Boolean)
}
</script>

<template>
  <div class="card" @click="$emit('click', character)">
    <div class="avatar">
      <img v-if="character.avatarUrl" :src="character.avatarUrl" alt="avatar" />
      <div v-else class="fallback">{{ initials(character.name) }}</div>
    </div>
    <div class="info">
      <h3 class="name">{{ character.name }}</h3>
      <div v-if="tags(character.personality).length" class="tags">
        <span v-for="tag in tags(character.personality)" :key="tag" class="tag">{{ tag }}</span>
      </div>
    </div>
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
.avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  margin: 0 auto 12px;
  background: #2d2d5a;
  display: flex;
  align-items: center;
  justify-content: center;
}
.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.fallback {
  font-size: 32px;
  font-weight: 600;
  color: #8a8ac4;
}
.name {
  font-size: 16px;
  font-weight: 600;
  text-align: center;
  color: #e8e8f0;
  margin-bottom: 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: center;
}
.tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background: #2d2d5a;
  color: #a0a0d0;
}
</style>
