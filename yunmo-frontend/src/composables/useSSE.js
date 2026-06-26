import { ref } from 'vue'

const SSE_TIMEOUT_MS = 300_000 // 5 分钟超时

export function useSSE() {
  const streamedText = ref('')
  const phase = ref('')
  const isStreaming = ref(false)
  const error = ref(null)
  let abortController = null
  let reader = null

  async function connect(url, body, onEvent) {
    // 中止上一次连接
    abort()

    streamedText.value = ''
    phase.value = 'connecting'
    isStreaming.value = true
    error.value = null
    abortController = new AbortController()

    // 超时保护
    const timeoutId = setTimeout(() => {
      abortController?.abort()
      error.value = '连接超时，请重试'
    }, SSE_TIMEOUT_MS)

    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('yunmo_token') || ''}`,
        },
        body: JSON.stringify(body),
        signal: abortController.signal,
      })

      if (!res.ok || !res.body) {
        throw new Error(`SSE 连接失败: HTTP ${res.status}`)
      }

      reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('data:')) {
            try {
              // Spring WebFlux 输出 'data:' 不带空格，trim() 兼容两种格式
              const event = JSON.parse(line.slice(5).trim())
              phase.value = event.phase
              if (event.data?.content) streamedText.value += event.data.content
              if (event.data?.token) streamedText.value += event.data.token
              onEvent?.(event)
            } catch { /* 跳过格式异常的数据行 */ }
          }
        }
      }
    } catch (e) {
      if (e.name !== 'AbortError') {
        error.value = '连接异常，请检查网络后重试'
      }
    } finally {
      clearTimeout(timeoutId)
      if (reader) {
        try { reader.releaseLock() } catch { /* reader 可能已被释放 */ }
        reader = null
      }
      abortController = null
      isStreaming.value = false
    }
  }

  function abort() {
    if (reader) {
      try { reader.cancel() } catch { /* reader 可能已关闭 */ }
    }
    abortController?.abort()
    isStreaming.value = false
  }

  return { streamedText, phase, isStreaming, error, connect, abort }
}
