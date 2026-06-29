<script setup>
/**
 * Agent 模型配置面板
 * 允许用户为每个 Agent 角色选择不同的 LLM 提供商和模型
 */
import { ref, onMounted, computed } from 'vue'
import { useApi } from '@/composables/useApi'
import { message } from 'ant-design-vue'

const api = useApi()

const configs = ref([])
const loading = ref(false)
const saving = ref(false)

/** Agent 角色元数据 */
const AGENT_META = {
  WRITER: { name: '写手', desc: '主笔创作，生成章节正文', icon: '✍️', defaultProvider: 'deepseek', defaultModel: 'deepseek-v4-pro' },
  ARCHITECT: { name: '架构师', desc: '情节预检，因果链分析', icon: '🏗️', defaultProvider: 'deepseek', defaultModel: 'deepseek-v4-pro' },
  SUPERVISOR: { name: '主编', desc: '统筹全局，协调各 Agent', icon: '📋', defaultProvider: 'deepseek', defaultModel: 'deepseek-v4-pro' },
  INSPECTOR: { name: '质检官', desc: '33 维质量审计', icon: '🔍', defaultProvider: 'kimi', defaultModel: 'kimi-k2-0719' },
  GUARDIAN: { name: '守卫', desc: '禁止术语扫描', icon: '🛡️', defaultProvider: 'qwen', defaultModel: 'qwen-plus' },
  CUSTODIAN: { name: '守护者', desc: '角色一致性检查', icon: '👤', defaultProvider: 'qwen', defaultModel: 'qwen-plus' },
  PLEASURE_BEAT: { name: '爽点设计师', desc: '情绪节奏结构设计', icon: '⚡', defaultProvider: 'deepseek', defaultModel: 'deepseek-v4-pro' },
  OUTLINER: { name: '细纲设计师', desc: '章节大纲拆解', icon: '📝', defaultProvider: 'deepseek', defaultModel: 'deepseek-v4-pro' },
  POLISHER: { name: '润色师', desc: '去 AI 味，增加人味', icon: '💎', defaultProvider: 'qwen', defaultModel: 'qwen-max' },
  EDITOR: { name: '对抗编辑', desc: '严苛挑错，提出修改建议', icon: '⚔️', defaultProvider: 'qwen', defaultModel: 'qwen-max' },
  READER: { name: '老书虫', desc: '读者视角评分', icon: '📚', defaultProvider: 'qwen', defaultModel: 'qwen-max' },
}

/** 预设模型选项 */
const MODEL_OPTIONS = {
  deepseek: [
    { value: 'deepseek-v4-pro', label: 'DeepSeek V4 Pro' },
    { value: 'deepseek-chat', label: 'DeepSeek Chat' },
  ],
  kimi: [
    { value: 'kimi-k2-0719', label: 'Kimi K2 (128K)' },
    { value: 'moonshot-v1-8k', label: 'Moonshot V1 (8K)' },
  ],
  qwen: [
    { value: 'qwen-max', label: 'Qwen Max (高质量)' },
    { value: 'qwen-plus', label: 'Qwen Plus (性价比)' },
    { value: 'qwen-turbo', label: 'Qwen Turbo (快速)' },
  ],
  openai: [
    { value: 'gpt-4o', label: 'GPT-4o' },
    { value: 'gpt-4o-mini', label: 'GPT-4o Mini' },
  ],
}

const PROVIDER_OPTIONS = Object.keys(MODEL_OPTIONS).map(k => ({
  value: k,
  label: { deepseek: 'DeepSeek', kimi: 'Kimi', qwen: '通义千问', openai: 'OpenAI' }[k] || k,
}))

/** 当前配置的 agent type 列表 */
const configMap = computed(() => {
  const map = {}
  for (const c of configs.value) {
    map[c.agentType] = c
  }
  return map
})

/** 获取某个 Agent 的模型选项 */
function getModelOptions(provider) {
  return MODEL_OPTIONS[provider] || []
}

/** 加载配置 */
async function loadConfigs() {
  loading.value = true
  try {
    configs.value = await api.agentModels.list()
    // 如果数据库为空，用默认值填充
    if (configs.value.length === 0) {
      configs.value = Object.entries(AGENT_META).map(([type, meta], idx) => ({
        agentType: type,
        provider: meta.defaultProvider,
        model: meta.defaultModel,
        enabled: true,
        sortOrder: idx,
      }))
    }
  } catch (e) {
    console.error('加载模型配置失败:', e)
    // 用默认值填充
    configs.value = Object.entries(AGENT_META).map(([type, meta], idx) => ({
      agentType: type,
      provider: meta.defaultProvider,
      model: meta.defaultModel,
      enabled: true,
      sortOrder: idx,
    }))
  } finally {
    loading.value = false
  }
}

/** 保存配置 */
async function saveConfigs() {
  saving.value = true
  try {
    await api.agentModels.update(configs.value)
    message.success('模型配置已保存')
  } catch (e) {
    message.error('保存失败: ' + (e.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

/** 恢复默认 */
async function resetConfigs() {
  try {
    await api.agentModels.reset()
    await loadConfigs()
    message.success('已恢复默认配置')
  } catch (e) {
    message.error('重置失败')
  }
}

/** 更新 provider 时自动选择第一个模型 */
function onProviderChange(config, newProvider) {
  config.provider = newProvider
  const options = MODEL_OPTIONS[newProvider]
  if (options && options.length > 0) {
    config.model = options[0].value
  }
}

onMounted(loadConfigs)
</script>

<template>
  <div class="model-config-panel">
    <div class="flex items-center justify-between mb-4">
      <div>
        <h3 class="text-base font-semibold" style="color:var(--yunmo-accent)">Agent 模型配置</h3>
        <p class="text-xs mt-1" style="color:var(--yunmo-text-caption)">为每个 AI 角色选择不同的大模型，优化创作质量与成本</p>
      </div>
      <div class="flex gap-2">
        <a-button size="small" @click="resetConfigs" :disabled="saving">恢复默认</a-button>
        <a-button type="primary" size="small" @click="saveConfigs" :loading="saving">保存配置</a-button>
      </div>
    </div>

    <a-spin :spinning="loading">
      <div class="space-y-3">
        <div
          v-for="type in Object.keys(AGENT_META)"
          :key="type"
          class="yunmo-card p-3 rounded-lg"
          :class="{ 'opacity-50': configMap[type] && !configMap[type].enabled }"
        >
          <div class="flex items-start gap-3">
            <!-- Agent 图标和信息 -->
            <div class="flex-shrink-0 w-8 text-center text-xl pt-0.5">
              {{ AGENT_META[type].icon }}
            </div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 mb-1">
                <span class="text-sm font-semibold" style="color:var(--yunmo-text-primary)">{{ AGENT_META[type].name }}</span>
                <a-tag size="small" class="text-[10px]">{{ type }}</a-tag>
              </div>
              <p class="text-xs mb-2" style="color:var(--yunmo-text-caption)">{{ AGENT_META[type].desc }}</p>

              <!-- 模型选择 -->
              <div class="flex items-center gap-2">
                <a-select
                  v-if="configMap[type]"
                  :value="configMap[type].provider"
                  size="small"
                  style="width: 120px"
                  @change="(v) => onProviderChange(configMap[type], v)"
                >
                  <a-select-option v-for="opt in PROVIDER_OPTIONS" :key="opt.value" :value="opt.value">
                    {{ opt.label }}
                  </a-select-option>
                </a-select>
                <a-select
                  v-if="configMap[type]"
                  v-model:value="configMap[type].model"
                  size="small"
                  style="width: 200px"
                >
                  <a-select-option
                    v-for="opt in getModelOptions(configMap[type]?.provider)"
                    :key="opt.value"
                    :value="opt.value"
                  >
                    {{ opt.label }}
                  </a-select-option>
                </a-select>
                <a-switch
                  v-if="configMap[type]"
                  v-model:checked="configMap[type].enabled"
                  size="small"
                  checked-children="启用"
                  un-checked-children="禁用"
                />
              </div>
            </div>
          </div>
        </div>
      </div>
    </a-spin>

    <div class="mt-4 p-3 rounded-lg text-xs" style="background:var(--yunmo-paper-dark); color:var(--yunmo-text-caption)">
      <p class="mb-1"><strong>提示：</strong></p>
      <ul class="list-disc pl-4 space-y-0.5">
        <li>写手/架构师/主编推荐使用 DeepSeek（长文本生成质量高）</li>
        <li>质检官推荐使用 Kimi（128K 上下文，适合长文审计）</li>
        <li>润色/编辑/评分推荐使用 Qwen Max（推理能力强）</li>
        <li>守卫/守护者可用 Qwen Plus（成本低，任务简单）</li>
        <li>如需使用 Ollama 等本地模型，请先在后端配置对应的 Provider</li>
      </ul>
    </div>
  </div>
</template>
