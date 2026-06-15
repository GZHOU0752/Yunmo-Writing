import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useApi } from './useApi'
import { useSSE } from './useSSE'

export const useWriteStore = defineStore('write', () => {
  const chapters = ref([])
  const currentChapter = ref(null)
  const sseStatus = ref('idle') // 'idle' | 'generating' | 'reviewing' | 'done'
  const streamedText = ref('')
  const qualityReport = ref(null)
  const api = useApi()

  // SSE 实例在 store 级别创建一次，避免每次 generateChapter 泄露实例
  const { streamedText: sseText, connect: sseConnect, abort: sseAbort } = useSSE()

  async function fetchChapters(novelId) {
    chapters.value = await api.chapters.list(novelId)
  }

  async function loadChapter(novelId, chapterNumber) {
    currentChapter.value = await api.chapters.get(novelId, chapterNumber)
  }

  let sseTimeoutId = null

  async function generateChapter(novelId, chapterNumber, focus) {
    // 中止上一次的 SSE 连接和超时
    sseAbort()
    if (sseTimeoutId) { clearTimeout(sseTimeoutId); sseTimeoutId = null }
    sseStatus.value = 'generating'
    streamedText.value = ''
    qualityReport.value = null

    // 累积接收到的审计数据
    let guardianData = null
    let inspectorData = null
    let deaiData = null

    sseConnect(`/api/v1/novels/${novelId}/chapters/${chapterNumber}/generate`, { focus }, (event) => {
      streamedText.value = sseText.value

      if (event.phase === 'review' || event.phase === 'deciding') {
        sseStatus.value = 'reviewing'
        // 从 review 阶段事件中累积审计结果（后端传来的是 JSON 字符串，需解析）
        if (event.data?.guardian_report) {
          guardianData = typeof event.data.guardian_report === 'string'
            ? JSON.parse(event.data.guardian_report)
            : event.data.guardian_report
        }
        if (event.data?.inspector_report) {
          inspectorData = typeof event.data.inspector_report === 'string'
            ? JSON.parse(event.data.inspector_report)
            : event.data.inspector_report
        }
        if (event.data?.deai_result) {
          try { deaiData = typeof event.data.deai_result === 'string' ? JSON.parse(event.data.deai_result) : event.data.deai_result } catch {}
        }
      }
      if (event.phase === 'error') {
        sseStatus.value = 'idle'
        qualityReport.value = { verdict: 'error', score: 0, guardianReport: event.data }
      }
      if (event.phase === 'done') {
        sseStatus.value = 'done'
        // 组装 33 维审计报告
        qualityReport.value = {
          verdict: inspectorData?.verdict || guardianData?.verdict || 'pass',
          score: inspectorData?.score || guardianData?.score || 0,
          guardianReport: guardianData,
          inspectorReport: inspectorData,
          deaiResult: deaiData,
        }
        // 重置累积数据
        guardianData = null
        inspectorData = null
        deaiData = null
        // 刷新章节列表并自动加载刚生成的章节到编辑器
        fetchChapters(novelId).then(() => {
          const chapterNum = currentChapter.value?.chapterNumber || chapterNumber
          loadChapter(novelId, chapterNum)
        })
      }
    })

    // 超时保护：5 分钟后自动恢复 idle
    sseTimeoutId = setTimeout(() => {
      if (sseStatus.value === 'generating' || sseStatus.value === 'reviewing') {
        sseStatus.value = 'idle'
      }
      sseTimeoutId = null
    }, 300_000)

    return { abort: sseAbort }
  }

  async function saveChapter(novelId, chapterNumber, content) {
    try {
      await api.chapters.update(novelId, chapterNumber, { content })
      if (currentChapter.value) {
        currentChapter.value.content = content
        // 同步更新本地字数（中文字符 + 英文单词）
        const chineseChars = (content.match(/[一-鿿]/g) || []).length
        const englishWords = (content.match(/[a-zA-Z]+/g) || []).length
        currentChapter.value.wordCount = chineseChars + englishWords
        // 同步更新章节列表中的字数
        const idx = chapters.value.findIndex(c => c.chapterNumber === chapterNumber)
        if (idx >= 0) chapters.value[idx].wordCount = currentChapter.value.wordCount
      }
    } catch (e) {
      console.error('保存章节失败:', e)
      throw e
    }
  }

  return {
    chapters, currentChapter, sseStatus, streamedText, qualityReport,
    fetchChapters, loadChapter, generateChapter, saveChapter,
  }
})
