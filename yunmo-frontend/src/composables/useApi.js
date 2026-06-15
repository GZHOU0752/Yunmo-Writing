const BASE = '/api/v1'

function authHeaders() {
  const token = localStorage.getItem('yunmo_token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export function getToken() { return localStorage.getItem('yunmo_token') }
export function setToken(t) { localStorage.setItem('yunmo_token', t) }
export function clearToken() { localStorage.removeItem('yunmo_token') }
export function isLoggedIn() { return !!localStorage.getItem('yunmo_token') }

async function request(url, options) {
  const res = await fetch(`${BASE}${url}`, {
    headers: { 'Content-Type': 'application/json', ...authHeaders(), ...options?.headers },
    ...options,
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({ detail: res.statusText }))
    throw new Error(err.detail || `HTTP ${res.status}`)
  }
  // 204 No Content 或空响应体不解析 JSON
  const text = await res.text()
  return text ? JSON.parse(text) : null
}

/** 文件下载辅助函数 */
async function downloadFile(url, filename) {
  const token = localStorage.getItem('yunmo_token')
  const res = await fetch(`${BASE}${url}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!res.ok) throw new Error(`下载失败: HTTP ${res.status}`)
  const blob = await res.blob()
  const a = document.createElement('a')
  const blobUrl = URL.createObjectURL(blob)
  a.href = blobUrl
  a.download = filename
  a.click()
  // 延迟释放 Blob URL，确保浏览器已完成下载
  setTimeout(() => URL.revokeObjectURL(blobUrl), 1000)
}

export function useApi() {
  return {
    novels: {
      list: () => request('/novels'),
      get: (id) => request(`/novels/${id}`),
      create: (data) => request('/novels', { method: 'POST', body: JSON.stringify(data) }),
      update: (id, data) => request(`/novels/${id}`, { method: 'PATCH', body: JSON.stringify(data) }),
      delete: (id) => request(`/novels/${id}`, { method: 'DELETE' }),
      search: (id, keyword) => request(`/novels/${id}/search`, { method: 'POST', body: JSON.stringify({ keyword }) }),
      replace: (id, find, replace, chapterNumbers) => request(`/novels/${id}/replace`, { method: 'POST', body: JSON.stringify({ find, replace, chapterNumbers }) }),
    },
    characters: {
      list: (novelId) => request(`/novels/${novelId}/characters`),
      create: (novelId, data) => request(`/novels/${novelId}/characters`, { method: 'POST', body: JSON.stringify(data) }),
      update: (novelId, id, data) => request(`/novels/${novelId}/characters/${id}`, { method: 'PATCH', body: JSON.stringify(data) }),
    },
    chapters: {
      list: (novelId) => request(`/novels/${novelId}/chapters`),
      get: (novelId, cn) => request(`/novels/${novelId}/chapters/${cn}`),
      create: (novelId, chapterNumber) => request(`/novels/${novelId}/chapters`, {
        method: 'POST',
        body: JSON.stringify(chapterNumber != null ? { chapterNumber } : {}),
      }),
      update: (novelId, cn, data) => request(`/novels/${novelId}/chapters/${cn}`, { method: 'PATCH', body: JSON.stringify(data) }),
      delete: (novelId, cn) => request(`/novels/${novelId}/chapters/${cn}`, { method: 'DELETE' }),
      versions: (novelId, cn) => request(`/novels/${novelId}/chapters/${cn}/versions`),
      restore: (novelId, cn, versionId) => request(`/novels/${novelId}/chapters/${cn}/versions/${versionId}/restore`, { method: 'POST' }),

    },
    world: {
      list: (novelId) => request(`/novels/${novelId}/world`),
      create: (novelId, data) => request(`/novels/${novelId}/world`, { method: 'POST', body: JSON.stringify(data) }),
    },
    generation: {
      stream: (novelId, cn, focus) =>
        fetch(`${BASE}/novels/${novelId}/chapters/${cn}/generate`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...authHeaders() },
          body: JSON.stringify({ focus }),
        }),
    },
    genre: {
      list: () => request('/genre/list'),
    },
    foreshadows: {
      list: (novelId) => request(`/novels/${novelId}/foreshadows`),
      reminders: (novelId, chapter) =>
        request(`/novels/${novelId}/foreshadows/reminders?currentChapter=${chapter}`),
    },
    export: {
      md: (novelId) => downloadFile(`/novels/${novelId}/export/md`, 'novel.md'),
      txt: (novelId) => downloadFile(`/novels/${novelId}/export/txt`, 'novel.txt'),
      epub: (novelId) => downloadFile(`/novels/${novelId}/export/epub`, 'novel.epub'),
    },
    organizations: {
      list: (novelId) => request(`/novels/${novelId}/organizations`),
      create: (novelId, data) => request(`/novels/${novelId}/organizations`, { method: 'POST', body: JSON.stringify(data) }),
    },
    relations: {
      get: (novelId) => request(`/novels/${novelId}/relations`),
    },
    stats: {
      overview: (novelId) => request(`/novels/${novelId}/stats`),
      setTarget: (novelId, targetWordCount) => request(`/novels/${novelId}/stats/target`, { method: 'PUT', body: JSON.stringify({ targetWordCount }) }),
    },
    references: {
      list: (novelId) => request(`/novels/${novelId}/references`),
      upload: (novelId, fileName, content) => request(`/novels/${novelId}/references`, { method: 'POST', body: JSON.stringify({ fileName, content }) }),
      delete: (novelId, materialId) => request(`/novels/${novelId}/references/${materialId}`, { method: 'DELETE' }),
      status: (novelId) => request(`/novels/${novelId}/references/status`),
    },
    outline: {
      list: (novelId) => request(`/novels/${novelId}/outline`),
      create: (novelId, data) => request(`/novels/${novelId}/outline`, { method: 'POST', body: JSON.stringify(data) }),
      update: (novelId, id, data) => request(`/novels/${novelId}/outline/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
      delete: (novelId, id) => request(`/novels/${novelId}/outline/${id}`, { method: 'DELETE' }),
      reorder: (novelId, items) => request(`/novels/${novelId}/outline/reorder`, { method: 'PUT', body: JSON.stringify(items) }),
      bindChapter: (novelId, id, chapterNumber) => request(`/novels/${novelId}/outline/${id}/bind-chapter`, { method: 'PUT', body: JSON.stringify({ chapterNumber }) }),
    },
    auth: {
      login: (email, password) => request('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
      register: (email, password, displayName) => request('/auth/register', { method: 'POST', body: JSON.stringify({ email, password, display_name: displayName }) }),
      me: () => request('/auth/me'),
      logout: () => request('/auth/logout', { method: 'POST' }),
    },
  }
}
