<script setup>
import { ref } from 'vue'
import api from '../api/client.js'

const props = defineProps({
  characterId: { type: String, required: true }
})

const emit = defineEmits(['uploaded'])

const preview = ref(null)
const uploading = ref(false)

function onFileChange(e) {
  const file = e.target.files[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    alert('请选择图片文件')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    alert('文件大小不能超过 5MB')
    return
  }
  preview.value = URL.createObjectURL(file)
  upload(file)
}

async function upload(file) {
  uploading.value = true
  try {
    const url = await api.uploadAvatar(props.characterId, file)
    emit('uploaded', url)
  } catch (err) {
    alert('上传失败: ' + err.message)
    preview.value = null
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div class="uploader">
    <label class="dropzone" :class="{ uploading }">
      <input type="file" accept="image/*" @change="onFileChange" />
      <img v-if="preview" :src="preview" class="preview" />
      <span v-else class="hint">点击上传头像</span>
    </label>
  </div>
</template>

<style scoped>
.uploader {
  width: 100%;
}
.dropzone {
  display: block;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  border: 2px dashed #4a4a8a;
  background: #1a1a2e;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  transition: border-color 0.2s;
  position: relative;
}
.dropzone:hover {
  border-color: #6a6aca;
}
.dropzone input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}
.preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.hint {
  font-size: 12px;
  color: #6a6a9a;
  text-align: center;
  padding: 8px;
}
</style>
