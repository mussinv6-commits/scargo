<template>
  <div class="crud-table-wrap">
    <div class="crud-toolbar">
      <div class="crud-toolbar-left">
        <h2 class="crud-title">{{ title }}</h2>
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
            <th v-for="col in columns" :key="col.key" :style="col.width ? { width: col.width } : {}">
              {{ col.label }}
            </th>
            <th v-if="editable || deletable" class="crud-actions-col">관리</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td :colspan="columns.length + 1" class="crud-empty">불러오는 중...</td>
          </tr>
          <tr v-else-if="rows.length === 0">
            <td :colspan="columns.length + 1" class="crud-empty">등록된 데이터가 없습니다.</td>
          </tr>
          <tr v-for="(row, idx) in rows" :key="row[idKey] ?? idx">
            <td v-for="col in columns" :key="col.key">
              <span v-if="col.type === 'boolean'" :class="['pill', row[col.key] ? 'pill-on' : 'pill-off']">
                {{ row[col.key] ? (col.trueLabel || '예') : (col.falseLabel || '아니오') }}
              </span>
              <span v-else-if="col.type === 'date'">{{ formatDate(row[col.key]) }}</span>
              <span v-else-if="col.format">{{ col.format(row[col.key], row) }}</span>
              <span v-else>{{ row[col.key] ?? '-' }}</span>
            </td>
            <td v-if="editable || deletable" class="crud-actions-col">
              <button v-if="editable" class="btn-admin btn-admin-ghost" @click="openEdit(row)">수정</button>
              <button v-if="deletable" class="btn-admin btn-admin-danger" @click="confirmDelete(row)">삭제</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 생성/수정 모달 -->
    <div v-if="modalOpen" class="crud-modal-backdrop" @click.self="closeModal">
      <div class="crud-modal">
        <div class="crud-modal-header">
          <h3>{{ editingRow ? `${title} 수정` : `${title} 신규 등록` }}</h3>
          <button class="crud-modal-close" @click="closeModal">×</button>
        </div>
        <form class="crud-modal-body" @submit.prevent="submitForm">
          <div v-for="field in formFields" :key="field.key" class="crud-field">
            <label :for="field.key">{{ field.label }}<span v-if="field.required" class="req">*</span></label>

            <select
              v-if="field.type === 'select'"
              :id="field.key"
              v-model="form[field.key]"
              class="crud-input"
              :required="field.required"
              :disabled="isFieldDisabled(field)"
            >
              <option value="" disabled>{{ field.placeholder || '선택하세요' }}</option>
              <option v-for="opt in resolveOptions(field)" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>

            <label v-else-if="field.type === 'checkbox'" class="crud-checkbox-row">
              <input
                :id="field.key"
                type="checkbox"
                v-model="form[field.key]"
                :disabled="isFieldDisabled(field)"
              />
              <span>{{ field.checkboxLabel || '예' }}</span>
            </label>

            <textarea
              v-else-if="field.type === 'textarea'"
              :id="field.key"
              v-model="form[field.key]"
              class="crud-input"
              rows="3"
              :placeholder="field.placeholder"
              :disabled="isFieldDisabled(field)"
            />

            <input
              v-else
              :id="field.key"
              :type="field.type === 'number' ? 'number' : 'text'"
              v-model="form[field.key]"
              class="crud-input"
              :placeholder="field.placeholder"
              :required="field.required"
              :disabled="isFieldDisabled(field)"
              :step="field.step"
            />
            <p v-if="field.hint" class="crud-hint">{{ field.hint }}</p>
          </div>

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
import { reactive, ref } from 'vue'

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
})

const modalOpen = ref(false)
const editingRow = ref(null)
const submitting = ref(false)
const formError = ref('')
const form = reactive({})

function resolveOptions(field) {
  return typeof field.options === 'function' ? field.options() : (field.options || [])
}

function isFieldDisabled(field) {
  return typeof field.disabled === 'function' ? field.disabled(editingRow.value) : !!field.disabled
}

function resetForm() {
  props.formFields.forEach((f) => {
    form[f.key] = f.type === 'checkbox' ? false : ''
  })
}

function openCreate() {
  editingRow.value = null
  formError.value = ''
  resetForm()
  props.formFields.forEach((f) => {
    if (f.default !== undefined) form[f.key] = f.default
  })
  modalOpen.value = true
}

function openEdit(row) {
  editingRow.value = row
  formError.value = ''
  props.formFields.forEach((f) => {
    const v = row[f.key]
    form[f.key] = v === null || v === undefined ? (f.type === 'checkbox' ? false : '') : v
  })
  modalOpen.value = true
}

function closeModal() {
  modalOpen.value = false
  submitting.value = false
  formError.value = ''
}

function formatDate(d) {
  if (!d) return '-'
  try {
    return new Date(d).toLocaleString('ko-KR')
  } catch {
    return d
  }
}

async function submitForm() {
  submitting.value = true
  formError.value = ''
  try {
    const payload = { ...form }
    if (editingRow.value) {
      if (props.onUpdate) await props.onUpdate(editingRow.value[props.idKey], payload, editingRow.value)
    } else {
      if (props.onCreate) await props.onCreate(payload)
    }
    closeModal()
  } catch (err) {
    formError.value = err?.response?.data?.message || err?.message || '처리 중 오류가 발생했습니다.'
    submitting.value = false
  }
}

function confirmDelete(row) {
  if (confirm(`정말 삭제하시겠습니까? (${row[props.idKey]})`) && props.onDelete) {
    props.onDelete(row[props.idKey], row)
  }
}
</script>

<style src="@/components/CSS/admin.css"></style>
