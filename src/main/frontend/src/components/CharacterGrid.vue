<script setup>
import CharacterCard from './CharacterCard.vue'

defineProps({
  characters: { type: Array, default: () => [] }
})

defineEmits(['select', 'create'])
</script>

<template>
  <div class="grid-container">
    <div class="header">
      <h1>角色卡片</h1>
      <button class="btn-add" @click="$emit('create')">+</button>
    </div>
    <div class="grid">
      <CharacterCard
        v-for="c in characters"
        :key="c.id"
        :character="c"
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
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.header h1 {
  font-size: 24px;
  font-weight: 600;
  color: #e8e8f0;
}
.btn-add {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: none;
  background: #4a4af0;
  color: white;
  font-size: 24px;
  cursor: pointer;
  transition: background 0.2s;
}
.btn-add:hover {
  background: #5a5aff;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
}
</style>
