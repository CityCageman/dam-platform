<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2 class="page-title">AI 工作台</h2>
        <p class="page-desc">任务中心统一管理 AI 注释、分类、血缘与报表生成结果；对话入口保留在右侧页签。</p>
      </div>
    </div>
    <el-tabs v-model="tab">
      <el-tab-pane label="任务中心" name="tasks">
        <AiTaskCenter />
      </el-tab-pane>
      <el-tab-pane label="智能助手" name="chat">
        <div class="chat-wrap"><AgentChat :hide-header="true" /></div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import AiTaskCenter from './AiTaskCenter.vue'
import AgentChat from './AgentChat.vue'

const tab = ref('tasks')
</script>

<style scoped>
.chat-wrap { height: calc(100vh - 250px); }
</style>
