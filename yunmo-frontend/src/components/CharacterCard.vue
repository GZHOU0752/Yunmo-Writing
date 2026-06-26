<script setup>
import { ref, computed } from 'vue'
import { useApi } from '@/composables/useApi'

const props = defineProps({
  character: { type: Object, required: true },
  novelId: { type: String, required: true },
  chapterNumber: { type: Number, default: null },
})
const emit = defineEmits(['updated', 'close'])

const api = useApi()
const editing = ref(false)
const saving = ref(false)
const analyzing = ref(false)

async function analyzeCharacter() {
  if (analyzing.value) return
  analyzing.value = true
  try {
    const result = await api.characters.analyze(props.novelId, props.character.id)
    if (result?.status === 'ok') {
      emit('updated', result)
    }
  } catch (e) {
    console.error('角色分析失败:', e)
  } finally {
    analyzing.value = false
  }
}

// 编辑态缓存
const editForm = ref({})

const roleLabels = {
  PROTAGONIST: '主角',
  ANTAGONIST: '反派',
  SUPPORTING: '配角',
  MINOR: '龙套',
}

const roleColors = {
  PROTAGONIST: '#8b3a3a',
  ANTAGONIST: '#b3443a',
  SUPPORTING: '#b8956c',
  MINOR: '#5a7a5a',
}

// 首字头像
const avatarChar = computed(() => props.character.name?.charAt(0) || '?')

const statusLabel = computed(() => {
  const state = props.character.currentState
  if (!state) return ''
  const emotion = state.emotion || state['状态']
  const location = state.location || state['位置']
  return [emotion, location].filter(Boolean).join(' · ')
})

function startEdit() {
  editForm.value = {
    name: props.character.name,
    description: props.character.description || '',
    role: props.character.role || 'SUPPORTING',
    importance: props.character.importance ?? 5,
    currentState: props.character.currentState
      ? JSON.stringify(props.character.currentState, null, 2)
      : '',
  }
  editing.value = true
}

async function saveEdit() {
  saving.value = true
  try {
    const body = {
      name: editForm.value.name,
      description: editForm.value.description,
      role: editForm.value.role,
      importance: Number(editForm.value.importance),
    }
    if (editForm.value.currentState) {
      try {
        body.current_state = JSON.parse(editForm.value.currentState)
      } catch {
        body.current_state = {}
      }
    }
    await api.characters.update(props.novelId, props.character.id, body)
    editing.value = false
    emit('updated')
  } catch (e) {
    console.error('保存角色失败:', e)
  } finally {
    saving.value = false
  }
}

function cancelEdit() {
  editing.value = false
}

async function toggleAppearance() {
  const current = props.character.lastAppearanceChapter
  const newChapter = current === props.chapterNumber
    ? (props.chapterNumber > 1 ? props.chapterNumber - 1 : null)
    : props.chapterNumber
  saving.value = true
  try {
    await api.characters.update(props.novelId, props.character.id, {
      last_appearance_chapter: newChapter,
    })
    emit('updated')
  } catch (e) {
    console.error('更新出场状态失败:', e)
  } finally {
    saving.value = false
  }
}

const isAppeared = computed(() =>
  props.character.lastAppearanceChapter === props.chapterNumber
)
</script>

<template>
  <div class="yunmo-card" :style="{ borderLeft: `3px solid ${roleColors[character.role] || '#888'}` }">
    <!-- 查看模式 -->
    <template v-if="!editing">
      <div class="flex items-center gap-3 mb-3">
        <div
          class="w-10 h-10 rounded-full flex items-center justify-center text-lg font-bold shrink-0"
          :style="{ background: roleColors[character.role] || '#888', color: '#fff' }"
        >
          {{ avatarChar }}
        </div>
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2">
            <span class="font-semibold text-sm truncate">{{ character.name }}</span>
            <a-tag :color="roleColors[character.role] || 'default'" size="small">
              {{ roleLabels[character.role] || character.role }}
            </a-tag>
          </div>
          <div v-if="statusLabel" class="text-xs mt-0.5" style="color:var(--yunmo-text-caption)">
            {{ statusLabel }}
          </div>
        </div>
        <a-button size="small" type="text" class="toolbar-btn" @click="startEdit">编辑</a-button>
        <a-button size="small" type="text" class="toolbar-btn" :loading="analyzing" @click="analyzeCharacter">AI 分析</a-button>
      </div>

      <!-- 描述 -->
      <p
        v-if="character.description"
        class="text-xs leading-relaxed mb-3"
        style="color:var(--yunmo-text-secondary)"
      >{{ character.description }}</p>

      <!-- 6 层认知快览 -->
      <div v-if="character.layer1Worldview || character.layer2Identity || character.layer3Values" class="mb-3">
        <div v-if="character.layer1Worldview" class="flex gap-1 text-xs mb-0.5">
          <span class="text-caption shrink-0">世界观:</span>
          <span class="truncate" style="color:var(--yunmo-text-secondary)">{{ character.layer1Worldview }}</span>
        </div>
        <div v-if="character.layer2Identity" class="flex gap-1 text-xs mb-0.5">
          <span class="text-caption shrink-0">自我:</span>
          <span class="truncate" style="color:var(--yunmo-text-secondary)">{{ character.layer2Identity }}</span>
        </div>
        <div v-if="character.layer3Values" class="flex gap-1 text-xs mb-0.5">
          <span class="text-caption shrink-0">价值观:</span>
          <span class="truncate" style="color:var(--yunmo-text-secondary)">{{ character.layer3Values }}</span>
        </div>
      </div>

      <!-- 操作行 -->
      <div class="flex items-center gap-2">
        <a-tag size="small" class="font-tabular">重要度 {{ character.importance || 5 }}</a-tag>
        <span v-if="character.isDead" class="text-xs" style="color:var(--yunmo-red)">已死亡</span>
        <div class="flex-1" />
        <a-button
          v-if="chapterNumber"
          size="small"
          :type="isAppeared ? 'primary' : 'default'"
          :loading="saving"
          @click="toggleAppearance"
        >
          {{ isAppeared ? '已出场' : '标记出场' }}
        </a-button>
      </div>
    </template>

    <!-- 编辑模式 -->
    <template v-else>
      <h4 class="text-sm font-semibold mb-3" style="color:var(--yunmo-accent)">编辑角色</h4>
      <a-form layout="vertical" size="small">
        <a-form-item label="名称">
          <a-input v-model:value="editForm.name" />
        </a-form-item>
        <a-form-item label="角色">
          <a-select v-model:value="editForm.role">
            <a-select-option v-for="(label, key) in roleLabels" :key="key" :value="key">{{ label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="重要度 (1-10)">
          <a-input-number v-model:value="editForm.importance" :min="1" :max="10" class="w-full" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="editForm.description" :rows="3" placeholder="外貌、性格、背景..." />
        </a-form-item>
        <a-form-item label="当前状态 (JSON)">
          <a-textarea v-model:value="editForm.currentState" :rows="2" placeholder='{"emotion":"愤怒","location":"青云山"}' style="font-family:monospace;font-size:11px" />
        </a-form-item>
      </a-form>
      <div class="flex gap-2 mt-3">
        <a-button size="small" @click="cancelEdit">取消</a-button>
        <a-button size="small" type="primary" :loading="saving" @click="saveEdit">保存</a-button>
      </div>
    </template>
  </div>
</template>
