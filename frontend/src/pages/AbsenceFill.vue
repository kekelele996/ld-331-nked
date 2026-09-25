<template>
  <el-container class="page">
    <el-header class="topbar">
      <h1>
        {{ APP_TITLE }} · 缺岗补位
        <el-link href="/" class="back-link">返回排班看板</el-link>
      </h1>
      <el-select v-model="department" @change="onDepartmentChange" style="width: 160px">
        <el-option label="急诊科" value="急诊科" />
        <el-option label="心内科" value="心内科" />
      </el-select>
    </el-header>

    <el-main class="main">
      <el-alert
        title="临时缺岗不改动原班表：从日期和班次发起缺岗后，系统列出同科室、同岗位、技能匹配且当天休息或未排班的候选人；补位时校验同日重复排班、夜班后禁接白班和连续工作上限。"
        type="info"
        show-icon :closable="false" />

      <el-card shadow="never">
        <template #header>① 从日期和班次发起缺岗</template>
        <el-form :inline="true" class="absence-form">
          <el-form-item label="日期">
            <el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" :clearable="false"
              :disabled-date="outOfRange" @change="loadDuty" />
          </el-form-item>
          <el-form-item label="班次">
            <el-select v-model="form.shift" @change="loadDuty" style="width: 110px">
              <el-option v-for="shift in SHIFT_TYPES_WORK" :key="shift" :label="shift" :value="shift" />
            </el-select>
          </el-form-item>
          <el-form-item label="原安排人员">
            <el-select v-model="form.staffName" placeholder="先按班表选择缺勤人" style="width: 200px">
              <el-option v-for="item in dutyItems" :key="`${item.date}-${item.staffName}`"
                :label="`${item.staffName}（${item.position}）`" :value="item.staffName" />
            </el-select>
          </el-form-item>
          <el-form-item label="发起人">
            <el-input v-model="form.initiatedBy" style="width: 130px" />
          </el-form-item>
          <el-form-item label="缺岗原因">
            <el-input v-model="form.reason" placeholder="如：突发疾病" style="width: 200px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="creating" @click="onCreateAbsence">发起缺岗</el-button>
          </el-form-item>
        </el-form>
        <div class="hint">
          班表范围 {{ scheduleStart || '加载中' }} ~ {{ scheduleEnd }}；已缺岗的安排不会重复出现。
          <span v-if="dutyLoaded && dutyItems.length === 0" class="warn">该班次当天无在岗安排，无法发起缺岗。</span>
        </div>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-head">
            <span>② 缺岗记录与补位</span>
            <el-button text @click="loadAbsences">刷新</el-button>
          </div>
        </template>
        <el-table :data="absences" size="small">
          <el-table-column prop="id" label="#" width="60" />
          <el-table-column prop="date" label="日期" width="110" />
          <el-table-column prop="position" label="岗位" width="90" />
          <el-table-column prop="shift" label="班次" width="80" />
          <el-table-column label="原安排" width="100">
            <template #default="{ row }">{{ row.staffName }}</template>
          </el-table-column>
          <el-table-column label="技能要求" min-width="160">
            <template #default="{ row }">
              <el-tag v-for="skill in row.requiredSkills" :key="skill" size="small" class="skill-tag">{{ skill }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" min-width="120" />
          <el-table-column prop="initiatedBy" label="发起人" width="90" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.status === '已补位' ? 'success' : 'warning'" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="补位人 / 处理时间" min-width="180">
            <template #default="{ row }">
              <template v-if="row.status === '已补位'">
                {{ row.replacementStaffName }} · {{ row.processedAt }}
              </template>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === '待补位'" type="primary" size="small" @click="openFill(row)">
                选候补位
              </el-button>
              <el-tooltip v-else content="已处理的缺岗不能重复补位" placement="top">
                <el-button size="small" disabled>已处理</el-button>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never">
        <template #header>③ 补位审计（发起人 / 原安排 / 补位人 / 处理时间）</template>
        <el-table :data="audits" size="small">
          <el-table-column prop="processedAt" label="处理时间" width="170" />
          <el-table-column prop="operator" label="发起人(主管)" width="110" />
          <el-table-column label="原安排" min-width="200">
            <template #default="{ row }">
              {{ row.originalDate }} {{ row.originalShift }} · {{ row.originalPosition }} · {{ row.originalStaff }}
            </template>
          </el-table-column>
          <el-table-column prop="replacementStaff" label="补位人" width="100" />
          <el-table-column prop="remark" label="校验结果" min-width="260" />
        </el-table>
      </el-card>
    </el-main>

    <!-- 补位对话框 -->
    <el-dialog v-model="dialogVisible" title="选择补位人员" width="760px" @closed="onDialogClosed">
      <el-descriptions v-if="current" :column="3" border size="small" class="absence-desc">
        <el-descriptions-item label="日期班次">{{ current.date }} {{ current.shift }}</el-descriptions-item>
        <el-descriptions-item label="原安排">{{ current.staffName }}（{{ current.position }}）</el-descriptions-item>
        <el-descriptions-item label="缺岗发起人">{{ current.initiatedBy }}</el-descriptions-item>
      </el-descriptions>

      <div class="candidate-toolbar">
        <span>候选人（同科室 · 同岗位 · 技能匹配 · 当天休息或未排班）</span>
        <el-select v-model="skillFilter" placeholder="按技能标签筛选" clearable size="small" style="width: 170px"
          @change="loadCandidates">
          <el-option v-for="skill in current?.requiredSkills ?? []" :key="skill" :label="skill" :value="skill" />
        </el-select>
      </div>

      <el-table :data="candidates" size="small" highlight-current-row @current-change="onCandidateChange"
        empty-text="没有符合条件的候选人">
        <el-table-column width="55">
          <template #default="{ row }">
            <el-radio v-model="selectedStaffId" :label="row.staffId" :value="row.staffId">&nbsp;</el-radio>
          </template>
        </el-table-column>
        <el-table-column prop="staffName" label="姓名" width="100" />
        <el-table-column prop="availability" label="当天状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.availability === '休息' ? 'info' : 'success'" size="small">{{ row.availability }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="技能标签" min-width="220">
          <template #default="{ row }">
            <el-tag v-for="skill in row.skills" :key="skill" size="small"
              :type="row.matchedSkills.includes(skill) ? 'primary' : 'info'" class="skill-tag">
              {{ skill }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-input v-model="fillOperator" size="small" class="operator-input" placeholder="处理主管" />
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="filling" :disabled="!selectedStaffId" @click="onFill">
          校验并补位
        </el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  createAbsence,
  fillAbsence,
  fetchAbsences,
  fetchAudits,
  fetchCandidates,
  fetchDutyStaff,
} from '../api/absence';
import { APP_TITLE } from '../constants/app';
import type { Absence, AbsenceCandidate, FillAudit, ScheduleItem } from '../types/schedule';

const SHIFT_TYPES_WORK = ['白班', '中班', '夜班'];

const department = ref('急诊科');
const scheduleStart = ref('');
const scheduleEnd = ref('');
const dutyItems = ref<ScheduleItem[]>([]);
const dutyLoaded = ref(false);
const absences = ref<Absence[]>([]);
const audits = ref<FillAudit[]>([]);
const creating = ref(false);

const firstOfMonth = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`;
};

const form = reactive({
  date: firstOfMonth(),
  shift: '夜班',
  staffName: '',
  reason: '',
  initiatedBy: '张主管',
});

async function loadDuty() {
  if (!form.date) return;
  dutyLoaded.value = false;
  const data = await fetchDutyStaff({ date: form.date, department: department.value, shift: form.shift });
  scheduleStart.value = data.scheduleStart;
  scheduleEnd.value = data.scheduleEnd;
  dutyItems.value = data.items;
  dutyLoaded.value = true;
  if (!data.items.some((item) => item.staffName === form.staffName)) {
    form.staffName = data.items[0]?.staffName ?? '';
  }
}

function outOfRange(d: Date) {
  if (!scheduleStart.value) return false;
  const pick = d.toISOString().slice(0, 10);
  return pick < scheduleStart.value || pick > scheduleEnd.value;
}

async function loadAbsences() {
  absences.value = await fetchAbsences(department.value);
}

async function loadAudits() {
  audits.value = await fetchAudits(department.value);
}

async function onDepartmentChange() {
  form.staffName = '';
  await Promise.all([loadDuty(), loadAbsences(), loadAudits()]);
}

async function onCreateAbsence() {
  if (!form.date || !form.shift || !form.staffName) {
    ElMessage.warning('请先选择日期、班次和原安排人员');
    return;
  }
  creating.value = true;
  try {
    const absence = await createAbsence({
      date: form.date,
      department: department.value,
      shift: form.shift,
      staffName: form.staffName,
      reason: form.reason,
      initiatedBy: form.initiatedBy,
    });
    ElMessage.success(`缺岗 #${absence.id} 已登记，可继续选择补位人`);
    form.reason = '';
    await Promise.all([loadDuty(), loadAbsences()]);
    openFill(absence);
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '发起缺岗失败');
  } finally {
    creating.value = false;
  }
}

// ---------- 补位对话框 ----------

const dialogVisible = ref(false);
const current = ref<Absence | null>(null);
const candidates = ref<AbsenceCandidate[]>([]);
const selectedStaffId = ref<number | null>(null);
const skillFilter = ref('');
const fillOperator = ref('张主管');
const filling = ref(false);

async function openFill(row: Absence) {
  current.value = row;
  selectedStaffId.value = null;
  skillFilter.value = '';
  fillOperator.value = '张主管';
  dialogVisible.value = true;
  await loadCandidates();
}

async function loadCandidates() {
  if (!current.value) return;
  try {
    candidates.value = await fetchCandidates(current.value.id, skillFilter.value || undefined);
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '候选人加载失败');
  }
}

function onCandidateChange(row: AbsenceCandidate | null) {
  if (row) selectedStaffId.value = row.staffId;
}

async function onFill() {
  if (!current.value || !selectedStaffId.value) return;
  const candidate = candidates.value.find((c) => c.staffId === selectedStaffId.value);
  try {
    await ElMessageBox.confirm(
      `确认由 ${candidate?.staffName} 补 ${current.value.date} ${current.value.shift}？提交后服务端将校验三项排班规则。`,
      '补位确认',
      { type: 'warning', confirmButtonText: '确认补位', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  filling.value = true;
  try {
    const result = await fillAbsence(current.value.id, {
      replacementStaffId: selectedStaffId.value,
      operator: fillOperator.value || '张主管',
    });
    ElMessage.success(`补位成功：${result.audit.replacementStaff} 已排入 ${result.absence.date} ${result.absence.shift}`);
    dialogVisible.value = false;
    await Promise.all([loadAbsences(), loadAudits()]);
  } catch (error: any) {
    ElMessage({ type: 'error', duration: 6000, message: `补位被拒绝：${error?.response?.data?.message ?? '规则校验未通过'}` });
    await loadCandidates();
  } finally {
    filling.value = false;
  }
}

function onDialogClosed() {
  current.value = null;
  candidates.value = [];
  selectedStaffId.value = null;
  skillFilter.value = '';
}

onMounted(async () => {
  await Promise.all([loadDuty(), loadAbsences(), loadAudits()]);
});
</script>

<style scoped>
.back-link {
  margin-left: 12px;
  font-size: 13px;
}

.absence-form {
  display: flex;
  flex-wrap: wrap;
}

.hint {
  color: #66768a;
  font-size: 13px;
}

.warn {
  color: #e6a23c;
  margin-left: 8px;
}

.muted {
  color: #9aa5b1;
}

.skill-tag {
  margin: 2px 4px 2px 0;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.absence-desc {
  margin-bottom: 12px;
}

.candidate-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  color: #4a5a6e;
  font-size: 13px;
}

.operator-input {
  width: 140px;
  margin-right: 8px;
}
</style>
