<template>
  <div class="schedule-board">
    <div v-for="item in items" :key="`${item.date}-${item.staffName}-${item.source ?? 'o'}`" class="shift-card"
      :class="{ absent: item.absent }" :style="{ borderLeftColor: item.color }">
      <div class="date">
        {{ item.date }}
        <el-tag v-if="item.absent" type="danger" size="small">缺岗</el-tag>
        <el-tag v-else-if="item.source === '补位'" type="success" size="small">补位</el-tag>
      </div>
      <strong :class="{ 'absent-name': item.absent }">{{ item.staffName }}</strong>
      <span>{{ item.position }} · {{ item.shift }}</span>
      <el-tag v-if="item.holiday" type="danger" size="small">特殊日期</el-tag>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ScheduleItem } from '../types/schedule';

defineProps<{ items: ScheduleItem[] }>();
</script>

<style scoped>
.absent {
  background: #fef0f0;
  border-left-color: #f56c6c !important;
}

.absent-name {
  text-decoration: line-through;
  color: #c45656;
}
</style>
