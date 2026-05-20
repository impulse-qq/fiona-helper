<script setup>
defineProps({
  pipeline: { type: Object, required: true }
})

defineEmits(['click'])
</script>

<template>
  <div class="card" @click="$emit('click', pipeline)">
    <div class="icon">🚀</div>
    <div class="info">
      <h3 class="name">{{ pipeline.name }}</h3>
      <div v-if="pipeline.worldSetting" class="world">{{ pipeline.worldSetting }}</div>
      <div class="slots">{{ pipeline.slots?.length || 0 }} slots</div>
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
  text-align: center;
}
.card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.4);
  border-color: #4a4a8a;
}
.icon {
  font-size: 36px;
  margin-bottom: 8px;
}
.name {
  font-size: 16px;
  font-weight: 600;
  color: #e8e8f0;
  margin-bottom: 4px;
}
.world {
  font-size: 12px;
  color: #8a8ac4;
  margin-bottom: 4px;
}
.slots {
  font-size: 11px;
  color: #6a6a9a;
}
</style>
