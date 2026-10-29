<template>
  <!-- 可拖动悬浮球 -->
  <div
    class="agent-ball"
    :class="{ 'is-dragging': dragging }"
    :style="{ left: pos.x + 'px', top: pos.y + 'px' }"
    title="智能助手"
    @pointerdown="onPointerDown"
    @click="onClick"
  >
    <el-icon :size="24"><ChatDotRound /></el-icon>
  </div>

  <!-- 点击悬浮球后弹出的对话框（v-show 保持组件挂载，关闭再打开不丢失对话记录） -->
  <Teleport to="body">
    <div v-show="open" class="agent-panel">
      <div class="agent-panel__head">
        <div class="agent-panel__title">
          <el-icon><ChatDotRound /></el-icon>
          <span>智能助手</span>
        </div>
        <el-icon class="agent-panel__close" @click="open = false"><Close /></el-icon>
      </div>
      <div class="agent-panel__body">
        <AgentChat :hide-header="true" />
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, reactive, onBeforeUnmount } from 'vue'
import AgentChat from '@/views/agent/AgentChat.vue'

const open = ref(false)

// 悬浮球尺寸与边距（单位 px）
const BALL_SIZE = 56
const MARGIN = 24

// 悬浮球位置，默认吸附在右下角
const pos = reactive({
  x: window.innerWidth - BALL_SIZE - MARGIN,
  y: window.innerHeight - BALL_SIZE - MARGIN,
})

const dragging = ref(false)
let moved = false
let startX = 0
let startY = 0
let originX = 0
let originY = 0

function clamp(v: number, min: number, max: number) {
  return Math.min(Math.max(v, min), max)
}

function onPointerDown(e: PointerEvent) {
  dragging.value = true
  moved = false
  startX = e.clientX
  startY = e.clientY
  originX = pos.x
  originY = pos.y
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}

function onPointerMove(e: PointerEvent) {
  if (!dragging.value) return
  const dx = e.clientX - startX
  const dy = e.clientY - startY
  if (Math.abs(dx) > 3 || Math.abs(dy) > 3) moved = true
  pos.x = clamp(originX + dx, 0, window.innerWidth - BALL_SIZE)
  pos.y = clamp(originY + dy, 0, window.innerHeight - BALL_SIZE)
}

function onPointerUp() {
  dragging.value = false
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
}

function onClick() {
  // 发生过拖拽则不当作点击
  if (moved) return
  open.value = !open.value
}

onBeforeUnmount(() => {
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
})
</script>

<style scoped>
.agent-ball {
  position: fixed;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--med-primary, #1a56db);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(26, 86, 219, 0.35);
  cursor: grab;
  z-index: 1500;
  user-select: none;
  touch-action: none;
  transition: box-shadow 0.2s, transform 0.2s;
}
.agent-ball:hover {
  box-shadow: 0 6px 20px rgba(26, 86, 219, 0.5);
  transform: scale(1.05);
}
.agent-ball.is-dragging {
  cursor: grabbing;
  transform: scale(1.1);
}

.agent-panel {
  position: fixed;
  right: 24px;
  bottom: 96px;
  width: 400px;
  height: 620px;
  max-width: calc(100vw - 48px);
  max-height: calc(100vh - 120px);
  background: var(--med-white, #fff);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18);
  overflow: hidden;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  animation: agent-pop-in 0.2s ease;
}
.agent-panel__head {
  height: 52px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--med-primary, #1a56db);
  color: #fff;
}
.agent-panel__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
}
.agent-panel__close {
  cursor: pointer;
  font-size: 18px;
  transition: opacity 0.2s;
}
.agent-panel__close:hover {
  opacity: 0.7;
}
.agent-panel__body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

/* 打开动画（v-show 切换 display 时会重新触发 animation） */
@keyframes agent-pop-in {
  from { opacity: 0; transform: translateY(12px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
</style>
