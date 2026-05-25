<script setup>
import { ref } from 'vue'
import api from '../api/client.js'

const props = defineProps({
  sessionId: { type: String, required: true },
  images: { type: Array, default: () => [] }
})

const emit = defineEmits(['uploaded'])

const uploading = ref(false)

function onFileChange(e) {
  const files = Array.from(e.target.files)
  if (!files.length) return

  for (const file of files) {
    if (!file.type.startsWith('image/')) {
      alert(`${file.name} 不是图片文件`)
      continue
    }
    if (file.size > 5 * 1024 * 1024) {
      alert(`${file.name} 超过 5MB`)
      continue
    }
    if (file.size === 0) {
      alert(`${file.name} 不能为空`)
      continue
    }
    upload(file)
  }
  e.target.value = ''
}

async function upload(file) {
  uploading.value = true
  try {
    await api.uploadSessionImage(props.sessionId, file)
    emit('uploaded')
  } catch (err) {
    alert('上传失败: ' + err.message)
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div class="gallery">
    <div class="gallery-header">
      <h3>产物图片</h3>
      <label class="upload-btn" :class="{ uploading }">
        <input type="file" accept="image/*" multiple @change="onFileChange" />
        {{ uploading ? '上传中...' : '+ 上传图片' }}
      </label>
    </div>

    <div v-if="images.length === 0" class="empty">暂无图片</div>

    <div class="thumbnails">
      <img
        v-for="img in images"
        :key="img.id"
        :src="img.imageUrl"
        class="thumb"
        alt="session image"
      />
    </div>
  </div>
</template>

<style scoped>
.gallery {
  margin-top: 24px;
}
.gallery-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.gallery-header h3 {
  font-size: 16px;
  color: #e8e8f0;
}
.upload-btn {
  padding: 6px 14px;
  border-radius: 6px;
  border: none;
  background: #4a4af0;
  color: white;
  font-size: 13px;
  cursor: pointer;
  position: relative;
  transition: opacity 0.2s;
}
.upload-btn.uploading {
  opacity: 0.6;
  cursor: wait;
}
.upload-btn input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}
.empty {
  text-align: center;
  color: #6a6a9a;
  padding: 24px;
}
.thumbnails {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 8px;
}
.thumb {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid #2a2a4a;
  cursor: pointer;
  transition: transform 0.2s;
}
.thumb:hover {
  transform: scale(1.05);
}
</style>
