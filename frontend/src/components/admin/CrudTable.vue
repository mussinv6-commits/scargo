<template>
  <div class="crud-table-wrap">
    <div class="crud-toolbar">
      <div class="crud-toolbar-left">
        <h2 class="crud-title">{{ title }} 목록</h2>
        <span v-if="!loading" class="crud-count">총 {{ rows.length }}건</span>
      </div>
      <div class="crud-toolbar-right">
        <slot name="toolbar-extra" />
        <button v-if="creatable" class="btn-admin btn-admin-primary" @click="openCreate">
          + 신규 등록
        </button>
      </div>
    </div>

    <div class="crud-table-scroll">
      <table class="crud-table">
        <thead>
          <tr>
            <th v-if="showIndex" class="is-center" style="width:64px;">{{ indexLabel }}</th>
            <th
              v-for="col in columns"
              :key="col.key"
              :style="col.width ? { width: col.width } : {}"
              :class="alignClass(col)"
            >
              {{ col.label }}
            </th>
            <th v-if="editable || deletable" class="crud-actions-col is-center">관리</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td :colspan="colSpan" class="crud-empty">불러오는 중...</td>
          </tr>
          <tr v-else-if="rows.length === 0">
            <td :colspan="colSpan" class="crud-empty">등록된 데이터가 없습니다.</td>
          </tr>
          <tr v-for="(row, idx) in pagedRows" :key="row[idKey] ?? idx">
            <td v-if="showIndex" class="is-center">{{ rowNo(idx) }}</td>
            <td v-for="col in columns" :key="col.key" :class="alignClass(col)">
              <!-- boolean: col.invert=true 면 true 가 빨간색(예: 위반여부) -->
              <span
                v-if="col.type === 'boolean'"
                :class="['pill', boolTone(col, row[col.key])]"
              >
                {{ row[col.key] ? (col.trueLabel || '예') : (col.falseLabel || '아니오') }}
              </span>
              <!-- badge: col.badge(value,row) => { label, tone: 'on'|'off'|'warn'|'muted' } -->
              <span
                v-else-if="col.type === 'badge'"
                :class="['pill', 'pill-' + (col.badge(row[col.key], row).tone || 'muted')]"
              >
                {{ col.badge(row[col.key], row).label }}
              </span>
              <span v-else-if="col.type === 'seq'">{{ rowNo(idx) }}</span>
              <span v-else-if="col.type === 'date'">{{ formatDate(row[col.key]) }}</span>
              <span v-else-if="col.formatter">{{ col.formatter(row[col.key], row) }}</span>
              <span v-else-if="col.format">{{ col.format(row[col.key], row) }}</span>
              <span v-else>{{ row[col.key] ?? '-' }}</span>
            </td>
            <!-- 26.09.30 수정: td 자체에 display:flex 를 주면 표 칸이 밀려서
                 헤더/데이터 열이 어긋나고 상태 컬럼이 겹쳐 보였다 → 안쪽 div 로 분리 -->
            <td v-if="editable || deletable" class="crud-actions-col is-center">
              <div class="crud-actions">
                <button v-if="editable" class="btn-admin btn-admin-ghost" @click="openEdit(row)">수정</button>
                <button v-if="deletable" class="btn-admin btn-admin-danger" @click="confirmDelete(row)">삭제</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <AdminPager v-model="page" :page-count="pageCount" />

    <!-- 생성/수정 모달 -->
    <div v-if="modalOpen" class="crud-modal-backdrop" @click.self="closeModal">
      <div class="crud-modal" role="dialog" aria-modal="true">
        <div class="crud-modal-header">
          <h3>{{ editingRow ? `${title} 수정` : `${title} 신규 등록` }}</h3>
          <button class="crud-modal-close" aria-label="닫기" @click="closeModal">×</button>
        </div>
        <form class="crud-modal-body" novalidate @submit.prevent="submitForm">
          <template v-for="field in formFields" :key="field.key">
            <div v-if="isFieldVisible(field)" class="crud-field" :class="{ 'has-error': fieldErrors[field.key] }">
              <label :for="'f-' + field.key">{{ field.label }}<span v-if="field.required" class="req">*</span></label>

              <select
                v-if="field.type === 'select'"
                :id="'f-' + field.key"
                v-model="form[field.key]"
                class="crud-input"
                :disabled="isFieldDisabled(field)"
                @change="clearFieldError(field.key)"
              >
                <option value="" :disabled="field.required">{{ field.placeholder || '선택하세요' }}</option>
                <option v-for="opt in resolveOptions(field)" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </option>
              </select>

              <label v-else-if="field.type === 'checkbox'" class="crud-checkbox-row">
                <input
                  :id="'f-' + field.key"
                  type="checkbox"
                  v-model="form[field.key]"
                  :disabled="isFieldDisabled(field)"
                />
                <span>{{ field.checkboxLabel || '예' }}</span>
              </label>

              <textarea
                v-else-if="field.type === 'textarea'"
                :id="'f-' + field.key"
                v-model="form[field.key]"
                class="crud-input"
                rows="3"
                :placeholder="field.placeholder"
                :disabled="isFieldDisabled(field)"
                @input="clearFieldError(field.key)"
              />

              <input
                v-else
                :id="'f-' + field.key"
                :type="field.type === 'number' ? 'number' : 'text'"
                v-model="form[field.key]"
                class="crud-input"
                :placeholder="field.placeholder"
                :disabled="isFieldDisabled(field)"
                :step="field.step"
                :min="field.min"
                @input="clearFieldError(field.key)"
              />
              <p v-if="fieldErrors[field.key]" class="crud-field-error">{{ fieldErrors[field.key] }}</p>
              <p v-else-if="field.hint" class="crud-hint">{{ field.hint }}</p>
            </div>
          </template>

          <!-- 26.09.30 수정: 서버 에러 원문(SQL/스택트레이스)을 보여주지 않고 안내 문장만 표시 -->
          <div v-if="formError" class="crud-form-error">{{ formError }}</div>

          <div class="crud-modal-footer">
            <button type="button" class="btn-admin btn-admin-ghost" @click="closeModal">취소</button>
            <button type="submit" class="btn-admin btn-admin-primary" :disabled="submitting">
              {{ submitting ? '처리 중...' : (editingRow ? '수정 저장' : '등록') }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { friendlyError, emptyToNull } from '@/utils/apiHelpers'
import AdminPager from '@/components/admin/AdminPager.vue'

const props = defineProps({
  title: { type: String, required: true },
  columns: { type: Array, required: true },
  rows: { type: Array, default: () => [] },
  idKey: { type: String, default: 'id' },
  formFields: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  creatable: { type: Boolean, default: true },
  editable: { type: Boolean, default: true },
  deletable: { type: Boolean, default: true },
  onCreate: { type: Function, default: null },
  onUpdate: { type: Function, default: null },
  onDelete: { type: Function, default: null },
  // 삭제 확인창에 보여줄 이름 (없으면 id)
  rowLabel: { type: Function, default: null },
  pageSize: { type: Number, default: 10 },
  showIndex: { type: Boolean, default: false },
  indexLabel: { type: String, default: '번호' },
})

const modalOpen = ref(false)
const editingRow = ref(null)
const submitting = ref(false)
const formError = ref('')
const form = reactive({})
const fieldErrors = reactive({})

const page = ref(1)
const pageCount = computed(() => Math.max(1, Math.ceil((props.rows?.length || 0) / props.pageSize) || 1))
const pagedRows = computed(() => {
  const start = (page.value - 1) * props.pageSize
  return (props.rows || []).slice(start, start + props.pageSize)
})
const colSpan = computed(() => props.columns.length + (props.editable || props.deletable ? 1 : 0) + (props.showIndex ? 1 : 0))

function rowNo(idx) {
  return (page.value - 1) * props.pageSize + idx + 1
}

watch(
  () => props.rows?.length,
  () => {
    if (page.value > pageCount.value) page.value = pageCount.value
  }
)

function alignClass(col) {
  if (col.align === 'text-end' || col.align === 'right') return 'is-right'
  if (col.align === 'text-start' || col.align === 'left') return 'is-left'
  return 'is-center'
}

function boolTone(col, value) {
  const good = col.invert ? !value : !!value
  return good ? 'pill-on' : 'pill-off'
}

function resolveOptions(field) {
  return typeof field.options === 'function' ? field.options() : (field.options || [])
}

function isFieldDisabled(field) {
  return typeof field.disabled === 'function' ? field.disabled(editingRow.value) : !!field.disabled
}

function isFieldVisible(field) {
  return typeof field.showIf === 'function' ? field.showIf(form, editingRow.value) : true
}

function clearFieldError(key) {
  delete fieldErrors[key]
  formError.value = ''
}

function clearAllFieldErrors() {
  Object.keys(fieldErrors).forEach((k) => delete fieldErrors[k])
}

function resetForm() {
  props.formFields.forEach((f) => {
    form[f.key] = f.type === 'checkbox' ? false : ''
  })
}

function openCreate() {
  editingRow.value = null
  formError.value = ''
  clearAllFieldErrors()
  resetForm()
  props.formFields.forEach((f) => {
    if (f.default !== undefined) form[f.key] = f.default
  })
  modalOpen.value = true
}

function openEdit(row) {
  editingRow.value = row
  formError.value = ''
  clearAllFieldErrors()
  props.formFields.forEach((f) => {
    const v = row[f.key]
    if (f.type === 'checkbox') form[f.key] = v === true || v === 'true'
    else if (f.type === 'select' && typeof v === 'boolean') form[f.key] = String(v)
    else form[f.key] = v === null || v === undefined ? '' : v
  })
  modalOpen.value = true
}

function closeModal() {
  modalOpen.value = false
  submitting.value = false
  formError.value = ''
  clearAllFieldErrors()
}

function formatDate(d) {
  if (!d) return '-'
  const date = new Date(d)
  return isNaN(date.getTime()) ? d : date.toLocaleString('ko-KR')
}

// 26.09.30 추가: 서버로 보내기 전에 화면에서 먼저 검사 → 잘못 입력하면 해당 칸 아래에 바로 안내
function validate() {
  clearAllFieldErrors()
  props.formFields.forEach((f) => {
    if (!isFieldVisible(f) || isFieldDisabled(f) || f.type === 'checkbox') return
    const raw = form[f.key]
    const value = typeof raw === 'string' ? raw.trim() : raw
    const empty = value === '' || value === null || value === undefined

    if (empty) {
      if (f.required) fieldErrors[f.key] = `${f.label}을(를) 입력해주세요.`
      return
    }
    if (f.type === 'number') {
      const n = Number(value)
      if (Number.isNaN(n)) fieldErrors[f.key] = '숫자만 입력할 수 있습니다.'
      else if (f.min !== undefined && n < Number(f.min)) fieldErrors[f.key] = `${f.min} 이상으로 입력해주세요.`
      else if (f.max !== undefined && n > Number(f.max)) fieldErrors[f.key] = `${f.max} 이하로 입력해주세요.`
    }
    if (!fieldErrors[f.key] && f.pattern && !f.pattern.test(String(value))) {
      fieldErrors[f.key] = f.patternMessage || '형식이 올바르지 않습니다.'
    }
    if (!fieldErrors[f.key] && typeof f.validate === 'function') {
      const msg = f.validate(value, form)
      if (msg) fieldErrors[f.key] = msg
    }
  })
  return Object.keys(fieldErrors).length === 0
}

async function submitForm() {
  formError.value = ''
  if (!validate()) {
    formError.value = '입력값을 확인해주세요.'
    return
  }
  submitting.value = true
  const actionLabel = editingRow.value ? '수정' : '등록'
  try {
    const payload = emptyToNull({ ...form })
    if (editingRow.value) {
      if (props.onUpdate) await props.onUpdate(editingRow.value[props.idKey], payload, editingRow.value)
    } else if (props.onCreate) {
      await props.onCreate(payload)
    }
    closeModal()
  } catch (err) {
    formError.value = `${props.title} ${actionLabel}에 실패했습니다.\n${friendlyError(err)}`
    submitting.value = false
  }
}

function confirmDelete(row) {
  const label = props.rowLabel ? props.rowLabel(row) : row[props.idKey]
  if (confirm(`[${label}] 항목을 삭제하시겠습니까?\n삭제한 데이터는 되돌릴 수 없습니다.`) && props.onDelete) {
    props.onDelete(row[props.idKey], row)
  }
}
</script>

<style src="@/components/CSS/admin.css"></style>
