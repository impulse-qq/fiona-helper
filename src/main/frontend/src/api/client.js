const BASE = '/api'

async function request(url, options = {}) {
  const res = await fetch(BASE + url, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  })
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(`${res.status}: ${text}`)
  }
  if (res.status === 204) return null
  return res.json()
}

export default {
  listCharacters() {
    return request('/characters')
  },

  getCharacter(id) {
    return request(`/characters/${id}`)
  },

  createCharacter(data) {
    return request('/characters', {
      method: 'POST',
      body: JSON.stringify(data)
    })
  },

  updateCharacter(id, data) {
    return request(`/characters/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data)
    })
  },

  deleteCharacter(id) {
    return request(`/characters/${id}`, { method: 'DELETE' })
  },

  uploadAvatar(id, file) {
    const form = new FormData()
    form.append('file', file)
    return fetch(`${BASE}/characters/${id}/avatar`, {
      method: 'POST',
      body: form
    }).then(async res => {
      if (!res.ok) throw new Error(`${res.status}: ${await res.text()}`)
      return res.text()
    })
  }
}
