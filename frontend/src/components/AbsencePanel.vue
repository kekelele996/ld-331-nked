<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <span>缺岗补位</span>
        <el-button size="small" @click="loadAbsences">刷新</el-button>
      </div>
    </template>

    <el-form class="absence-form" inline @submit.prevent>
      <el-form-item label="日期">
        <el-date-picker
          v-model="form.date"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          style="width: 150px"
          @change="onDatePicked"
        />
      </el-form-item>
      <el-form-item label="班次">
        <el-select v-model="form.shift" style="width: 100px" @change="loadShiftStaff">
          <el-option v-for="shift in WORK_SHIFTS" :key="shift" :label="shift" :value="shift" />
        </el-select>
      </el-form-item>
      <el-form-item label="缺岗人">
        <el-select
          v-model="form.staffName"
          style="width: 150px"
          :placeholder="shiftStaff.length ? '选择当班人员' : '该日期班次暂无排班'"
          no-data-text="该日期班次暂无排班人员"
        >
          <el-option v-for="item in shiftStaff" :key="item.staffName" :label="`${item.staffName}（${item.position}）`" :value="item.staffName" />
        </el-select>
      </el-form-item>
      <el-form-item label="原因">
        <el-input v-model="form.reason" placeholder="如：突发身体不适" style="width: 160px" />
      </el-form-item>
      <el-form-item label="发起人">
        <el-input v-model="form.operator" style="width: 110px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :disabled="!form.staffName" :loading="submitting" @click="submitAbsence">登记缺岗</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="absences" height="280" empty-text="暂无缺岗记录">
      <el-table-column prop="date" label="日期" width="110" />
      <el-table-column prop="shift" label="班次" width="70" />
      <el-table-column prop="staffName" label="缺岗人" width="90" />
      <el-table-column prop="position" label="岗位" width="90" />
      <el-table-column prop="reason" label="原因" min-width="120" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === '待补位' ? 'warning' : 'success'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="补位人" width="90">
        <template #default="{ row }">{{ row.substitute ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === '待补位'" size="small" type="primary" @click="openBackfill(row)">补位</el-button>
          <span v-else>—</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="选择补位人员" width="680px">
      <template v-if="current">
        <el-alert
          :title="`${current.date} ${current.shift}｜缺岗：${current.staffName}（${current.position}）`"
          type="warning"
          :closable="false"
          class="dialog-tip"
        />
        <p class="dialog-hint">候选条件：同科室、同岗位、技能标签匹配，且当天未排班或休息；选定后校验夜班后接白班、连续工作上限与同日重复排班。</p>
        <el-table
          v-loading="loadingCandidates"
          :data="candidates"
          height="260"
          highlight-current-row
          empty-text="暂无符合条件的补位人选"
          @current-change="onCandidateSelect"
        >
          <el-table-column prop="staffName" label="姓名" width="100" />
          <el-table-column prop="position" label="岗位" width="100" />
          <el-table-column label="技能标签" min-width="140">
            <template #default="{ row }">
              <el-tag v-for="skill in row.skills" :key="skill" size="small" class="tag">{{ skill }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="匹配技能" min-width="120">
            <template #default="{ row }">
              <el-tag v-for="skill in row.matchedSkills" :key="skill" size="small" type="success" class="tag">{{ skill }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="当日状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.dayStatus === '休息' ? 'info' : 'warning'">{{ row.dayStatus }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!selected" :loading="submitting" @click="confirmBackfill">确认补位</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { reactive, ref, watch } from 'vue';
import {
  backfillAbsence,
  createAbsence,
  errorMessage,
  fetchAbsences,
  fetchCandidates,
  fetchShiftStaff,
} from '../api/absence';
import type { Absence, BackfillCandidate, ScheduleItem } from '../types/schedule';

const WORK_SHIFTS = ['白班', '中班', '夜班'];

const props = defineProps<{ department: string; defaultDate: string }>();
const emit = defineEmits<{ updated: [] }>();

const form = reactive({
  date: props.defaultDate || new Date().toISOString().slice(0, 10),
  shift: '白班',
  staffName: '',
  reason: '',
  operator: '王主管',
});

const absences = ref<Absence[]>([]);
const shiftStaff = ref<ScheduleItem[]>([]);
const candidates = ref<BackfillCandidate[]>([]);
const current = ref<Absence | null>(null);
const selected = ref<BackfillCandidate | null>(null);
const dialogVisible = ref(false);
const loadingCandidates = ref(false);
const submitting = ref(false);
let dateTouched = false;

async function loadAbsences() {
  absences.value = await fetchAbsences(props.department);
}

async function loadShiftStaff() {
  form.staffName = '';
  shiftStaff.value = form.date ? await fetchShiftStaff(props.department, form.date, form.shift) : [];
}

function onDatePicked() {
  dateTouched = true;
  loadShiftStaff();
}

async function submitAbsence() {
  submitting.value = true;
  try {
    await createAbsence({
      date: form.date,
      department: props.department,
      shift: form.shift,
      staffName: form.staffName,
      reason: form.reason,
      operator: form.operator,
    });
    ElMessage.success('缺岗已登记，等待补位');
    form.reason = '';
    await loadAbsences();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    submitting.value = false;
  }
}

async function openBackfill(absence: Absence) {
  current.value = absence;
  selected.value = null;
  dialogVisible.value = true;
  loadingCandidates.value = true;
  try {
    candidates.value = await fetchCandidates(absence.id);
  } catch (error) {
    ElMessage.error(errorMessage(error));
    dialogVisible.value = false;
  } finally {
    loadingCandidates.value = false;
  }
}

function onCandidateSelect(row: BackfillCandidate | null) {
  selected.value = row;
}

async function confirmBackfill() {
  if (!current.value || !selected.value) return;
  submitting.value = true;
  try {
    await backfillAbsence(current.value.id, { candidate: selected.value.staffName, operator: form.operator });
    ElMessage.success(`补位成功：${selected.value.staffName} 已排入 ${current.value.date} ${current.value.shift}`);
    dialogVisible.value = false;
    await loadAbsences();
    emit('updated');
  } catch (error) {
    ElMessage.error(errorMessage(error));
    await loadAbsences();
    const latest = absences.value.find((item) => item.id === current.value?.id);
    if (!latest || latest.status !== '待补位') {
      dialogVisible.value = false;
      emit('updated');
    } else {
      try {
        candidates.value = await fetchCandidates(latest.id);
        selected.value = null;
      } catch {
        dialogVisible.value = false;
      }
    }
  } finally {
    submitting.value = false;
  }
}

watch(
  () => props.department,
  () => {
    form.staffName = '';
    loadAbsences();
    loadShiftStaff();
  },
);

watch(
  () => props.defaultDate,
  (date) => {
    if (date && !dateTouched) {
      form.date = date;
      loadShiftStaff();
    }
  },
);

loadAbsences();
loadShiftStaff();
</script>

<style scoped>
.absence-form {
  margin-bottom: 8px;
}

.dialog-tip {
  margin-bottom: 8px;
}

.dialog-hint {
  margin: 0 0 12px;
  color: #66768a;
  font-size: 13px;
}
</style>
