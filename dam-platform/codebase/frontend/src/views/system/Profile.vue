<template>
  <div class="page-container">
    <div class="page-header"><h2>个人中心</h2></div>
    <el-card style="max-width: 600px;">
      <el-descriptions :column="1" border size="large">
        <el-descriptions-item label="用户名">{{ userStore.userInfo?.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ userStore.userInfo?.realName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ userStore.userInfo?.role || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '@/store/user'
const userStore = useUserStore()
</script>
