<script setup>
import { ref, computed, watch } from 'vue'
import { useApi } from '@/composables/useApi'
import OutlineNodeEditor from './OutlineNodeEditor.vue'
import OutlineCompletionPanel from './OutlineCompletionPanel.vue'

const props = defineProps({
  novelId: String,
  chapters: Array,
})
const emit = defineEmits(['selectChapter'])

const api = useApi()

// 大纲树数据
const nodes = ref([])
const loading = ref(false)
const selectedKeys = ref([])
const expandedKeys = ref([])

// 新建/编辑弹窗
const editorOpen = ref(false)
const editingNode = ref(null)
const parentForNew = ref(null)
const newLevel = ref(2)

// AI补全面板
const completionOpen = ref(false)
const completionParent = ref(null)

// 拉取大纲树
async function fetchOutline() {
  loading.value = true
  try {
    nodes.value = await api.outline.list(props.novelId) || []
  } catch (e) {
    console.error('加载大纲失败:', e)
  } finally {
    loading.value = false
  }
}

// 构建 Ant Design Tree 数据
const treeData = computed(() => {
  return buildTree(nodes.value, null)
})

function buildTree(list, parentId) {
  const children = list
    .filter(n => (parentId === null ? !n.parentId : n.parentId === parentId))
    .sort((a, b) => (a.sequenceOrder || 0) - (b.sequenceOrder || 0))
  return children.map(n => ({
    key: n.id,
    title: n.title,
    level: n.level,
    node: n,
    children: buildTree(list, n.id),
    isLeaf: !list.some(c => c.parentId === n.id),
    // 自定义显示
    slots: { title: 'nodeTitle' },
  }))
}

const levelBadge = { 0: { text: '总', color: 'var(--yunmo-accent)' }, 1: { text: '卷', color: 'var(--yunmo-amber)' }, 2: { text: '章', color: 'var(--yunmo-green)' }, 3: { text: '节', color: 'var(--yunmo-text-caption)' } }

// 新增子节点
function addChild(parentNode) {
  parentForNew.value = parentNode
  newLevel.value = Math.min(3, (parentNode?.level || 0) + 1)
  editingNode.value = null
  editorOpen.value = true
}

// 新增根节点
function addRootNode() {
  parentForNew.value = null
  newLevel.value = nodes.value.length === 0 ? 0 : 1
  editingNode.value = null
  editorOpen.value = true
}

// 编辑节点
function editNode(node) {
  editingNode.value = node
  parentForNew.value = null
  editorOpen.value = true
}

// AI补全
function openCompletion(node) {
  completionParent.value = node
  completionOpen.value = true
}

// 保存节点
async function handleSave(data) {
  try {
    if (editingNode.value) {
      await api.outline.update(props.novelId, editingNode.value.id, data)
    } else {
      await api.outline.create(props.novelId, {
        parentId: parentForNew.value?.id || null,
        level: newLevel.value,
        ...data,
      })
    }
    await fetchOutline()
  } catch (e) {
    console.error('保存大纲节点失败:', e)
  }
}

// 删除节点
async function deleteNode(node) {
  try {
    await api.outline.delete(props.novelId, node.id)
    await fetchOutline()
  } catch (e) {
    console.error('删除大纲节点失败:', e)
  }
}

// 绑定章节
async function bindChapter(node, chapterNumber) {
  try {
    await api.outline.bindChapter(props.novelId, node.id, chapterNumber)
    await fetchOutline()
  } catch (e) {
    console.error('绑定章节失败:', e)
  }
}

// 点击节点 → 如果已绑章节则跳转
function handleSelect(keys) {
  selectedKeys.value = keys
  if (keys.length > 0) {
    const node = nodes.value.find(n => n.id === keys[0])
    if (node?.chapterNumber) {
      emit('selectChapter', node.chapterNumber)
    }
  }
}

// AI补全完成后刷新
function handleCompletionDone() {
  fetchOutline()
}

watch(() => props.novelId, () => {
  if (props.novelId) fetchOutline()
}, { immediate: true })
</script>

<template>
  <div class="outline-tree flex flex-col h-full">
    <div class="flex items-center justify-between mb-2 px-1">
      <span class="text-sm font-semibold" style="color:var(--yunmo-accent)">大纲</span>
      <div class="flex gap-1">
        <a-button size="small" type="text" @click="addRootNode">
          <span class="text-sm">+ 新增</span>
        </a-button>
        <a-button size="small" type="text" @click="fetchOutline">
          <span class="text-xs">刷新</span>
        </a-button>
      </div>
    </div>

    <a-spin :spinning="loading" class="flex-1 overflow-y-auto">
      <a-tree
        v-if="treeData.length > 0"
        v-model:selectedKeys="selectedKeys"
        v-model:expandedKeys="expandedKeys"
        :tree-data="treeData"
        :default-expand-all="true"
        block-node
        @select="handleSelect"
      >
        <template #nodeTitle="{ level, node, title }">
          <div class="flex items-center gap-1 py-0.5 group">
            <span
              class="text-[10px] px-1 rounded font-semibold shrink-0 leading-tight"
              :style="{ background: levelBadge[level]?.color || 'var(--yunmo-border)', color: level === 0 ? '#fff' : '#fff' }"
            >
              {{ levelBadge[level]?.text || level }}
            </span>
            <span class="text-sm truncate flex-1">{{ title }}</span>
            <span
              v-if="node.chapterNumber"
              class="text-[10px] px-1 rounded shrink-0"
              style="background:var(--yunmo-paper-dark);color:var(--yunmo-text-caption)"
            >
              第{{ node.chapterNumber }}章
            </span>
            <!-- 右键操作按钮 -->
            <span class="hidden group-hover:flex items-center gap-0.5 shrink-0">
              <a-button size="small" type="text" class="!p-0 !h-5 !text-xs" @click.stop="addChild(node)">+子</a-button>
              <a-button size="small" type="text" class="!p-0 !h-5 !text-xs" @click.stop="editNode(node)">✎</a-button>
              <a-button size="small" type="text" class="!p-0 !h-5 !text-xs" @click.stop="openCompletion(node)">🤖</a-button>
              <!-- 绑定章节下拉 -->
              <a-dropdown v-if="chapters?.length">
                <a-button size="small" type="text" class="!p-0 !h-5 !text-xs" @click.stop>🔗</a-button>
                <template #overlay>
                  <a-menu @click="({ key }) => bindChapter(node, parseInt(key))">
                    <a-menu-item v-for="ch in chapters.slice(0, 30)" :key="ch.chapterNumber">
                      第{{ ch.chapterNumber }}章 {{ ch.title || '' }}
                    </a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
              <a-popconfirm
                title="删除该节点及所有子节点？"
                ok-text="删除"
                cancel-text="取消"
                @confirm="deleteNode(node)"
              >
                <a-button size="small" type="text" class="!p-0 !h-5 !text-xs" style="color:var(--yunmo-red)" @click.stop>✕</a-button>
              </a-popconfirm>
            </span>
          </div>
        </template>
      </a-tree>

      <!-- 空状态 -->
      <div v-if="!loading && treeData.length === 0" class="text-center py-8">
        <p class="text-caption mb-2">暂无大纲节点</p>
        <a-button size="small" type="primary" @click="addRootNode">创建总纲</a-button>
      </div>
    </a-spin>

    <!-- 编辑弹窗 -->
    <OutlineNodeEditor
      :open="editorOpen"
      :node="editingNode"
      :novel-id="novelId"
      @update:open="editorOpen = $event"
      @save="handleSave"
    />

    <!-- AI补全面板 -->
    <OutlineCompletionPanel
      :open="completionOpen"
      :parent-node="completionParent"
      @update:open="completionOpen = $event"
      @done="handleCompletionDone"
    />
  </div>
</template>
