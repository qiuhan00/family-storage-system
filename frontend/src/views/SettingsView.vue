<!-- src/views/SettingsView.vue（ADMIN）—— 应用设置：临期提醒天数阈值（1-90，Stepper），
     保存调 PUT /api/settings（{key:"expiry.remind.days", value}）；读取失败/非 ADMIN 均有妥善提示。 -->
<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant/es/toast';
import 'vant/es/toast/style';
import { apiErrorMessage } from '../api/http';
import { settingsApi } from '../api/settings';
import { authApi } from '../api/auth';
import { useAuthStore } from '../store/auth';

const MIN_DAYS = 1;
const MAX_DAYS = 90;

const router = useRouter();
const auth = useAuthStore();

const loading = ref(true);
const loadError = ref(false);
const days = ref(7);
const saving = ref(false);

async function load() {
  loading.value = true;
  loadError.value = false;
  try {
    const data = await settingsApi.get();
    const v = Number(data.expiry_remind_days);
    days.value = Number.isInteger(v) && v >= MIN_DAYS && v <= MAX_DAYS ? v : 7;
  } catch (e) {
    loadError.value = true;
    showToast(apiErrorMessage(e, '设置读取失败'));
  } finally {
    loading.value = false;
  }
}

async function save() {
  if (saving.value) return;
  if (!Number.isInteger(days.value) || days.value < MIN_DAYS || days.value > MAX_DAYS) {
    showToast(`临期提醒天数需为 ${MIN_DAYS}-${MAX_DAYS} 的整数`);
    return;
  }
  saving.value = true;
  try {
    const data = await settingsApi.update('expiry.remind.days', String(days.value));
    days.value = Number(data.expiry_remind_days);
    showToast('已保存');
  } catch (e) {
    showToast(apiErrorMessage(e, '保存失败'));
  } finally {
    saving.value = false;
  }
}

async function ensureAdmin() {
  try {
    if (!auth.user && auth.token) {
      auth.user = await authApi.me();
    }
    if (auth.user?.role !== 'ADMIN') {
      showToast('无权限访问应用设置');
      void router.replace('/profile');
      return;
    }
    void load();
  } catch {
    // 401 由 http 拦截器统一处理跳登录页
  }
}

onMounted(() => {
  void ensureAdmin();
});
</script>

<template>
  <div class="settings-page">
    <div class="settings-loading" v-if="loading && !loadError">
      <van-loading size="24">加载中…</van-loading>
    </div>

    <van-empty
      v-else-if="loadError"
      description="设置读取失败，请稍后重试"
      image-size="80"
    >
      <van-button size="small" type="primary" plain @click="load">重试</van-button>
    </van-empty>

    <div v-else>
      <van-cell-group inset class="settings-group">
        <van-field name="stepper" label="临期提醒天数" :border="false">
          <template #input>
            <van-stepper
              v-model="days"
              :min="MIN_DAYS"
              :max="MAX_DAYS"
              integer
              input-width="48px"
              button-size="28px"
            />
          </template>
        </van-field>
        <van-cell
          :title="`临期物品在到期日前 ${days} 天收到站内提醒`"
          :border="false"
          class="settings-hint"
        />
      </van-cell-group>

      <div class="settings-save">
        <van-button round block type="primary" :loading="saving" :disabled="loadError" @click="save">
          保存设置
        </van-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.settings-page {
  padding: 12px 0 24px;
}

.settings-loading {
  display: flex;
  justify-content: center;
  padding: 48px 0;
}

.settings-group {
  margin-bottom: 12px;
}

.settings-hint {
  font-size: 12px;
  color: var(--ink-2);
}

.settings-save {
  padding: 16px;
}

.settings-save .van-button {
  height: 44px;
  border-radius: 2px;
}
</style>