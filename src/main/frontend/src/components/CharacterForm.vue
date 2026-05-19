<script setup>
import { ref, watch } from 'vue'
import ImageUploader from './ImageUploader.vue'

const props = defineProps({
  character: { type: Object, default: null }
})

const emit = defineEmits(['save', 'cancel'])

const form = ref({ name: '', baseDesign: '', personality: '' })
const avatarUrl = ref(null)

watch(() => props.character, (c) => {
  if (c) {
    form.value = {
      name: c.name || '',
      baseDesign: c.baseDesign || '',
      personality: c.personality || ''
    }
    avatarUrl.value = c.avatarUrl || null
  } else {
    form.value = { name: '', baseDesign: '', personality: '' }
    avatarUrl.value = null
  }
}, { immediate: true })

function submit() {
  if (!form.value.name.trim()) {
    alert('名称不能为空')
    return
  }
  emit('save', {
    name: form.value.name.trim(),
    baseDesign: form.value.baseDesign.trim(),
    personality: form.value.personality.trim()
  })
}

function onAvatarUploaded(url) {
  avatarUrl.value = url
}
</script>

<template>
  <div class="modal-overlay" @click.self="$emit('cancel')">
    <div class="modal">
      <h2>{{ character ? '编辑角色' : '新建角色' }}</h2>

      <div class="avatar-section" v-if="character">
        <ImageUploader
          :characterId="character.id"
          @uploaded="onAvatarUploaded"
        />
      </div>

      <div class="field">
        <label>名称 *</label>
        <input v-model="form.name" placeholder="角色名称" maxlength="128" />
      </div>

      <div class="field">
        <label>基础设定</label>
        <textarea v-model="form.baseDesign" placeholder="外貌、身份等描述" rows="3" maxlength="512" />
      </div>

      <div class="field">
        <label>性格</label>
        <textarea v-model="form.personality" placeholder="用逗号分隔多个标签，如：冷静, 寡言, 擅长潜入" rows="2" maxlength="512" />
      </div>

      <div class="actions">
        <button class="btn-primary" @click="submit">保存</button>
        <button class="btn-secondary" @click="$emit('cancel')">取消</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}
.modal {
  background: #1a1a2e;
  border-radius: 16px;
  padding: 24px;
  width: 90%;
  max-width: 420px;
  max-height: 90vh;
  overflow-y: auto;
  border: 1px solid #2a2a4a;
}
.modal h2 {
  font-size: 18px;
  margin-bottom: 16px;
  color: #e8e8f0;
}
.avatar-section {
  display: flex;
  justify-content: center;
  margin-bottom: 16px;
}
.field {
  margin-bottom: 12px;
}
.field label {
  display: block;
  font-size: 13px;
  color: #a0a0d0;
  margin-bottom: 4px;
}
.field input,
.field textarea {
  width: 100%;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid #2a2a4a;
  background: #0f0f1a;
  color: #e0e0e8;
  font-size: 14px;
  resize: vertical;
}
.field input:focus,
.field textarea:focus {
  outline: none;
  border-color: #4a4af0;
}
.actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}
.actions button {
  flex: 1;
  padding: 10px;
  border-radius: 8px;
  border: none;
  font-size: 14px;
  cursor: pointer;
  transition: opacity 0.2s;
}
.btn-primary {
  background: #4a4af0;
  color: white;
}
.btn-primary:hover {
  opacity: 0.9;
}
.btn-secondary {
  background: #2a2a4a;
  color: #c0c0d0;
}
.btn-secondary:hover {
  background: #3a3a6a;
}
</style>
