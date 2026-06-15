<script setup>
import { computed } from 'vue'

const props = defineProps({ report: Object })

/** 分组映射 */
const groupConfig = {
  '情节': { color: 'var(--yunmo-amber)', icon: '📖', order: 1 },
  '角色': { color: 'var(--yunmo-accent)', icon: '👤', order: 2 },
  '文笔': { color: 'var(--yunmo-green)', icon: '✒️', order: 3 },
  '合规': { color: 'var(--yunmo-accent-light)', icon: '🛡️', order: 4 },
}

/** 从 Inspector 报告中提取33维维度并分组 */
const groupedDimensions = computed(() => {
  const raw = props.report?.inspectorReport
  const dims = raw?.dimensions
  if (!Array.isArray(dims)) return {}
  const groups = {}
  for (const d of dims) {
    const cat = d.category || '合规'
    if (!groups[cat]) groups[cat] = { name: cat, dims: [], avgScore: 0, ...groupConfig[cat] }
    groups[cat].dims.push({
      name: d.name || '未命名维度',
      score: typeof d.score === 'number' ? d.score : (parseInt(d.score) || 5),
      comment: d.comment || '',
      severe: d.severe || false,
      fatal: d.fatal || false,
    })
  }
  // 计算每组平均分
  for (const g of Object.values(groups)) {
    g.avgScore = g.dims.length > 0
      ? Math.round(g.dims.reduce((s, d) => s + d.score, 0) / g.dims.length * 10) / 10
      : 0
  }
  return groups
})

/** 分组按 order 排序 */
const sortedGroups = computed(() =>
  Object.values(groupedDimensions.value).sort((a, b) => (a.order || 99) - (b.order || 99))
)

/** Guardian 违规 */
const violations = computed(() => {
  const raw = props.report?.guardianReport
  if (!raw) return []
  const vlist = raw.violations
  if (!Array.isArray(vlist)) return []
  return vlist.map((v, i) => ({
    key: i,
    term: v.term || v.word || v.forbidden_term || '',
    severity: v.severity || 'minor',
    reason: v.reason || v.description || '',
    suggestion: v.suggestion || v.alternative || '',
  }))
})

const guardianPassed = computed(() => {
  const raw = props.report?.guardianReport
  if (!raw) return null
  if (typeof raw.passed === 'boolean') return raw.passed
  return violations.value.length === 0
})

const fatalCount = computed(() =>
  violations.value.filter(v => v.severity === 'fatal').length
)

const severityColor = (s) => {
  if (s === 'fatal') return 'red'
  if (s === 'severe') return 'orange'
  return 'gold'
}

const severityLabel = (s) => {
  if (s === 'fatal') return '致命'
  if (s === 'severe') return '严重'
  return '轻微'
}

const scoreColor = (s) => {
  if (s >= 8) return 'var(--yunmo-green)'
  if (s >= 6) return 'var(--yunmo-amber)'
  return 'var(--yunmo-red)'
}

const verdictLabels = {
  pass: { text: '通过', color: 'green' },
  pass_forced: { text: '强制通过（已达最大重试）', color: 'orange' },
  rewrite: { text: '需重写', color: 'red' },
  regenerate: { text: '需重新生成', color: 'red' },
}

/** 是否展示去AI味详情 */
const deAiResult = computed(() => props.report?.deaiResult || null)
</script>

<template>
  <div class="border-t border-[var(--yunmo-border)] pt-4 mt-2">
    <h4 class="text-sm font-semibold mb-3">33 维质量审计</h4>

    <!-- 总分与判定 -->
    <div class="flex items-center gap-3 mb-4">
      <div class="text-2xl font-bold" :style="{ color: (report.score || 0) >= 70 ? 'var(--yunmo-green)' : 'var(--yunmo-red)' }">
        {{ report.score || 0 }}
      </div>
      <div class="text-caption text-xs">/ 100</div>
      <a-tag :color="verdictLabels[report.verdict]?.color || 'default'" class="ml-auto">
        {{ verdictLabels[report.verdict]?.text || report.verdict }}
      </a-tag>
    </div>

    <!-- 去AI味检测结果 -->
    <div v-if="deAiResult" class="mb-3 p-2 rounded text-xs" style="background:var(--yunmo-paper-dark)">
      <span class="font-semibold" style="color:var(--yunmo-accent)">去AI味：</span>
      <span :style="{ color: scoreColor(deAiResult.score) }">{{ deAiResult.score }} 分</span>
      <span class="text-caption ml-1">{{ deAiResult.analysis }}</span>
      <span v-if="deAiResult.regexMatches?.length > 0" class="text-caption ml-1">
        （检测到 {{ deAiResult.regexMatches.length }} 种AI模板）
      </span>
    </div>

    <!-- 33维分组折叠面板 -->
    <a-collapse v-if="sortedGroups.length > 0" :bordered="false" size="small" class="mb-3">
      <a-collapse-panel
        v-for="group in sortedGroups"
        :key="group.name"
        :header="`${group.icon || ''} ${group.name}组 · 均分 ${group.avgScore}`"
      >
        <div class="space-y-1">
          <div v-for="(d, i) in group.dims" :key="i" class="flex items-center gap-2 text-xs py-0.5">
            <span
              class="w-1.5 h-1.5 rounded-full flex-shrink-0"
              :style="{ background: d.fatal ? 'var(--yunmo-red)' : d.severe ? 'var(--yunmo-amber)' : 'var(--yunmo-green)' }"
            />
            <span class="flex-1 text-[var(--yunmo-text-secondary)] truncate" :title="d.name">{{ d.name }}</span>
            <span v-if="d.comment" class="text-caption truncate max-w-[120px]" :title="d.comment">{{ d.comment }}</span>
            <span class="font-mono text-xs font-semibold w-6 text-right" :style="{ color: scoreColor(d.score) }">{{ d.score }}</span>
          </div>
        </div>
      </a-collapse-panel>
    </a-collapse>

    <!-- 无维度数据时的备用 -->
    <a-collapse v-if="!sortedGroups.length && report.inspectorReport" :bordered="false" size="small">
      <a-collapse-panel key="inspector" header="详细审计数据">
        <pre class="text-xs whitespace-pre-wrap text-[var(--yunmo-text-caption)]">{{ JSON.stringify(report.inspectorReport, null, 2) }}</pre>
      </a-collapse-panel>
    </a-collapse>

    <!-- Guardian 类型检查 -->
    <div v-if="violations.length > 0 || guardianPassed === true" class="mb-2">
      <div class="flex items-center gap-2 mb-2">
        <span class="text-xs font-semibold" style="color:var(--yunmo-accent)">类型合规检查</span>
        <a-tag :color="guardianPassed ? 'green' : 'red'" size="small">
          {{ guardianPassed ? '通过' : `${violations.length} 处违规` }}
        </a-tag>
        <span v-if="fatalCount > 0" class="text-xs" style="color:var(--yunmo-red)">
          {{ fatalCount }} 处致命
        </span>
      </div>
      <div v-if="violations.length > 0" class="yunmo-card p-2 space-y-1.5">
        <div v-for="v in violations" :key="v.key" class="flex items-start gap-2 text-xs py-1">
          <a-tag :color="severityColor(v.severity)" size="small">{{ severityLabel(v.severity) }}</a-tag>
          <span class="font-mono text-[var(--yunmo-text-secondary)]">{{ v.term }}</span>
          <span v-if="v.reason" class="text-caption">{{ v.reason }}</span>
          <span v-if="v.suggestion" class="text-[var(--yunmo-text-caption)] ml-auto flex-shrink-0">
            建议: {{ v.suggestion }}
          </span>
        </div>
      </div>
    </div>

    <!-- Guardian JSON 备用 -->
    <a-collapse v-if="guardianPassed === null && report.guardianReport && violations.length === 0" :bordered="false" size="small">
      <a-collapse-panel key="guardian" header="类型检查数据">
        <pre class="text-xs whitespace-pre-wrap text-[var(--yunmo-text-caption)]">{{ JSON.stringify(report.guardianReport, null, 2) }}</pre>
      </a-collapse-panel>
    </a-collapse>
  </div>
</template>
