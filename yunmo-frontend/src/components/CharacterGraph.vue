<script setup>
import { ref, watch, onBeforeUnmount, nextTick } from 'vue'
import { useApi } from '@/composables/useApi'

const props = defineProps({ novelId: String, open: Boolean })
const emit = defineEmits(['update:open'])

const api = useApi()
const loading = ref(false)
const chartDom = ref(null)
let chart = null
let requestId = 0

const roleColors = {
  PROTAGONIST: '#8b3a3a',
  ANTAGONIST: '#b3443a',
  SUPPORTING: '#b8956c',
  MINOR: '#5a7a5a',
}

async function loadGraph() {
  if (!props.novelId) return
  const myId = ++requestId
  loading.value = true
  try {
    const data = await api.relations.get(props.novelId)
    if (myId !== requestId) return // 过期请求忽略
    await nextTick()
    renderChart(data)
  } catch (e) {
    if (myId !== requestId) return
    console.error('加载角色关系图失败:', e)
  } finally {
    if (myId === requestId) loading.value = false
  }
}

function renderChart(data) {
  if (!chartDom.value) return
  if (chart) chart.dispose()

  const echarts = window.echarts
  if (!echarts) {
    // 动态加载 ECharts CDN（防止重复加载）
    if (document.querySelector('script[data-echarts]')) return
    const script = document.createElement('script')
    script.src = 'https://cdn.jsdelivr.net/npm/echarts@5/dist/echarts.min.js'
    script.setAttribute('data-echarts', '1')
    script.onload = () => renderChart(data)
    script.onerror = () => { loading.value = false; console.error('ECharts CDN 加载失败') }
    document.head.appendChild(script)
    return
  }

  chart = echarts.init(chartDom.value)

  const nodes = (data.nodes || []).map(n => ({
    id: n.id,
    name: n.name,
    symbolSize: n.importance ? Math.max(20, n.importance * 5) : 25,
    itemStyle: { color: roleColors[n.role] || '#888' },
    label: { show: true, fontSize: 12, color: document.documentElement.getAttribute('data-theme') === 'dark' ? '#d4c8b8' : '#1f160c' },
  }))

  const edges = (data.edges || []).map(e => ({
    source: e.source,
    target: e.target,
    label: { show: true, formatter: e.relationType, fontSize: 10, color: '#8b3a3a' },
    lineStyle: { color: '#b8956c', curveness: 0.2 },
  }))

  chart.setOption({
    tooltip: {
      formatter: (p) => {
        if (p.dataType === 'edge') return `${p.data.sourceName} → ${p.data.targetName}<br/>${p.data.relationType}`
        return `${p.name}`
      }
    },
    series: [{
      type: 'graph', layout: 'force', roam: true, draggable: true,
      force: { repulsion: 200, edgeLength: [80, 200], gravity: 0.1 },
      data: nodes, edges: edges,
      emphasis: { focus: 'adjacency' },
    }],
  })
}

watch(() => props.open, (v) => { if (v) loadGraph() })
onBeforeUnmount(() => { chart?.dispose() })
</script>

<template>
  <a-modal :open="open" title="角色关系图谱" @cancel="$emit('update:open', false)" :footer="null" width="700px">
    <a-spin :spinning="loading">
      <div ref="chartDom" style="width:100%;height:450px" />
      <div v-if="loading" class="text-center text-caption py-20">加载中...</div>
    </a-spin>
  </a-modal>
</template>
