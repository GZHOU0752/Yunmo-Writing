import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useApi } from './useApi'

export const useNovelStore = defineStore('novel', () => {
  const novels = ref([])
  const genres = ref([])
  const loading = ref(false)
  const api = useApi()

  async function fetchNovels() {
    loading.value = true
    try {
      novels.value = await api.novels.list()
    } finally {
      loading.value = false
    }
  }

  async function fetchGenres() {
    try {
      genres.value = await api.genre.list()
    } catch (e) {
      console.error('获取类型列表失败:', e)
    }
  }

  async function createNovel(title, genreId, synopsis) {
    const novel = await api.novels.create({ title, genre_id: genreId, synopsis: synopsis || '' })
    novels.value.unshift(novel)
    return novel
  }

  async function deleteNovel(id) {
    await api.novels.delete(id)
    novels.value = novels.value.filter(n => n.id !== id)
  }

  return { novels, genres, loading, fetchNovels, fetchGenres, createNovel, deleteNovel }
})
