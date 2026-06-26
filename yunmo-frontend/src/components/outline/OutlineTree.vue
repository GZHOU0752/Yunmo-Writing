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

const levelBadge = {
  0: { text: '全书', color: 'var(--yunmo-accent)' },
  1: { text: '分卷', color: 'var(--yunmo-amber)' },
  2: { text: '章纲', color: 'var(--yunmo-green)' },
  3: { text: '节', color: 'var(--yunmo-text-caption)' },
}

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
          <div class="flex items-start gap-1 py-1 group">
            <span
              class="text-[10px] px-1 rounded font-semibold shrink-0 leading-tight mt-0.5"
              :style="{ background: levelBadge[level]?.color || 'var(--yunmo-border)', color: '#fff' }"
            >
              {{ levelBadge[level]?.text || level }}
            </span>
            <div class="flex-1 min-w-0">
              <div class="text-sm truncate">{{ title }}</div>
              <div
                v-if="node.outlineContent"
                class="text-[11px] truncate mt-0.5 leading-snug"
                style="color:var(--yunmo-text-caption)"
              >{{ node.outlineContent.substring(0, 60) }}{{ node.outlineContent.length > 60 ? '…' : '' }}</div>
            </div>
            <span
              v-if="node.chapterNumber"
              class="text-[10px] px-1.5 py-0.5 rounded shrink-0 mt-0.5"
              style="background:var(--yunmo-accent);color:#fff"
            >
              第{{ node.chapterNumber }}章
            </span>
            <!-- hover 操作按钮 -->
            <span class="hidden group-hover:inline-flex items-center gap-0.5 shrink-0">
              <a-button size="small" type="text" class="!px-1 !h-5 !text-[10px]" @click.stop="addChild(node)">子级</a-button>
              <a-button size="small" type="text" class="!px-1 !h-5 !text-[10px]" @click.stop="editNode(node)">编辑</a-button>
              <a-button size="small" type="text" class="!px-1 !h-5 !text-[10px]" @click.stop="openCompletion(node)">AI</a-button>
              <a-dropdown v-if="chapters?.length">
                <a-button size="small" type="text" class="!px-1 !h-5 !text-[10px]" @click.stop>绑定</a-button>
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
                <a-button size="small" type="text" class="!px-1 !h-5 !text-[10px]" style="color:var(--yunmo-red)" @click.stop>删</a-button>
              </a-popconfirm>
            </span>
          </div>
        </template>
      </a-tree>

      <!-- 底部新增 -->
      <div v-if="treeData.length > 0" class="mt-1 text-center">
        <a-button size="small" type="dashed" block @click="addRootNode" class="text-xs">
          新增节点
        </a-button>
      </div>

      <!-- 空状态 -->
      <div v-if="!loading && treeData.length === 0" class="text-center py-8 px-4">
        <p class="text-sm mb-1" style="color:var(--yunmo-ink)">还没有大纲</p>
        <p class="text-xs mb-4 leading-relaxed" style="color:var(--yunmo-text-caption);max-width:240px;margin:0 auto">
          大纲帮你整理故事走向。从全书大纲开始，然后为每章写章纲，AI 会参照大纲来写正文。
        </p>
        <a-button type="primary" size="small" @click="addRootNode">创建全书大纲</a-button>
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
