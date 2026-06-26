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
  const checkpoint = ref(null)
  const chapterControlCard = ref(null)    // 章节控制卡
  const antiAIDiagnosis = ref(null)       // 去AI味诊断结果
  const hookSelection = ref(null)         // 钩子编排选择
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
    let fixGuidanceData = null
    let adversarialScore = 0
    let antiAIReport = null
    let guardResults = null

    sseConnect(`/api/v1/novels/${novelId}/chapters/${chapterNumber}/generate`, { focus }, (event) => {
      // 解码 AI 输出的 HTML 实体 — 仅在新token到达时运行
      if (event.data?.token || event.data?.content) {
        const raw = sseText.value
        streamedText.value = raw
          .replace(/&#12288;/g, '　')
          .replace(/&#(\d+);/g, (_, code) => String.fromCharCode(parseInt(code, 10)))
      }

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
        if (event.data?.fix_guidance) {
          fixGuidanceData = event.data.fix_guidance
        }
        if (event.data?.adversarial_score != null) {
          adversarialScore = event.data.adversarial_score
        }
        if (event.data?.anti_ai_report) {
          antiAIReport = event.data.anti_ai_report
        }
        if (event.data?.guard_results) {
          guardResults = event.data.guard_results
        }
      }
      if (event.phase === 'preflight' && event.data?.checkpoint) {
        checkpoint.value = event.data.checkpoint
      }
      if (event.phase === 'error') {
        sseStatus.value = 'idle'
        qualityReport.value = { verdict: 'error', score: 0, guardianReport: event.data }
        // 中断时保留已生成的流式内容——用户不至于全部丢失
        injectStreamedContent(novelId, chapterNumber)
      }
      if (event.phase === 'done') {
        sseStatus.value = 'done'
        // 组装 33 维审计报告
        qualityReport.value = {
          verdict: inspectorData?.verdict || guardianData?.verdict || 'pass',
          score: adversarialScore || inspectorData?.score || guardianData?.score || 0,
          guardianReport: guardianData,
          inspectorReport: inspectorData,
          deaiResult: deaiData,
          fix_guidance: fixGuidanceData,
          anti_ai_report: antiAIReport,
          guard_results: guardResults,
        }
        // 重置累积数据
        guardianData = null
        inspectorData = null
        deaiData = null
        fixGuidanceData = null
        adversarialScore = 0
        antiAIReport = null
        guardResults = null
        // 立即将流式文本注入当前章节（不等服务端保存完成）
        injectStreamedContent(novelId, chapterNumber)
        if (currentChapter.value) {
          currentChapter.value.status = 'GENERATED'
          const idx = chapters.value.findIndex(c => c.chapterNumber === (currentChapter.value?.chapterNumber || chapterNumber))
          if (idx >= 0) chapters.value[idx].status = 'GENERATED'
        }
        // 后台静默刷新章节列表（不覆盖编辑器已显示的本地内容）
        fetchChapters(novelId)
      }
    })

    // 超时保护：5 分钟后自动恢复 idle
    sseTimeoutId = setTimeout(() => {
      if (sseStatus.value === 'generating' || sseStatus.value === 'reviewing') {
        sseStatus.value = 'idle'
        // 超时时保留已生成的流式内容
        injectStreamedContent(novelId, chapterNumber)
      }
      sseTimeoutId = null
    }, 300_000)

    const wrappedAbort = () => {
      // 中止前保留已生成内容
      if (streamedText.value) {
        injectStreamedContent(novelId, chapterNumber)
      }
      if (sseTimeoutId) { clearTimeout(sseTimeoutId); sseTimeoutId = null }
      sseAbort()
      sseStatus.value = 'idle'
    }
    return { abort: wrappedAbort }
  }

  /** 将流式文本注入 currentChapter，中断/超时/手动停止时调用 */
  function injectStreamedContent(novelId, chapterNumber) {
    if (!streamedText.value) return
    if (!currentChapter.value) {
      const found = chapters.value.find(c => c.chapterNumber === chapterNumber)
      if (found) currentChapter.value = { ...found }
    }
    if (currentChapter.value) {
      currentChapter.value.content = streamedText.value
      const chineseChars = (streamedText.value.match(/[一-鿿]/g) || []).length
      const englishWords = (streamedText.value.match(/[a-zA-Z]+/g) || []).length
      currentChapter.value.wordCount = chineseChars + englishWords
      // 同步更新章节列表
      const idx = chapters.value.findIndex(c => c.chapterNumber === chapterNumber)
      if (idx >= 0) {
        chapters.value[idx] = { ...chapters.value[idx], content: streamedText.value, wordCount: chineseChars + englishWords }
      }
    }
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
      console.error('章节保存失败:', e)
      throw e
    }
  }

  return {
    chapters, currentChapter, sseStatus, streamedText, qualityReport, checkpoint,
    chapterControlCard, antiAIDiagnosis, hookSelection,
    fetchChapters, loadChapter, generateChapter, saveChapter,
  }
})
