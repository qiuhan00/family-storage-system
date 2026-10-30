// src/views/LoginView.vue —— 登录页：Vant Form/Field 校验，成功后写入 token 并跳转首页。
<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant/es/toast';
import 'vant/es/toast/style';
import { useAuthStore } from '../store/auth';
import type { ApiErrorBody } from '../types';

const router = useRouter();
const auth = useAuthStore();

const form = reactive({ username: '', password: '' });
const loading = ref(false);
/** 7 天免密登录：勾选则 token 持久化到 localStorage，不勾选仅本次会话（sessionStorage）。 */
const remember = ref(false);

const rules = {
  username: [{ required: true, message: '请输入用户名' }],
  password: [{ required: true, message: '请输入密码' }],
};

const onSubmit = async () => {
  loading.value = true;
  try {
    await auth.login(
      {
        username: form.username.trim(),
        password: form.password,
      },
      remember.value,
    );
    await router.replace('/');
  } catch (err) {
    const body = (err as { response?: { data?: ApiErrorBody } }).response?.data;
    showToast(body?.message || '登录失败，请稍后重试');
  } finally {
    loading.value = false;
  }
};

const goRegister = () => router.push('/register');
</script>

<template>
  <div class="auth-page">
    <div class="auth-title">
      <h1>家庭收纳</h1>
      <p>记录家中每一件物品的位置与数量</p>
    </div>

    <van-form @submit="onSubmit">
      <van-cell-group inset>
        <van-field
          v-model="form.username"
          name="username"
          label="用户名"
          placeholder="请输入用户名"
          :rules="rules.username"
          clearable
          autocomplete="username"
        />
        <van-field
          v-model="form.password"
          type="password"
          name="password"
          label="密码"
          placeholder="请输入密码"
          :rules="rules.password"
          autocomplete="current-password"
        />
      </van-cell-group>

      <div class="auth-remember">
        <van-checkbox v-model="remember" shape="square" icon-size="16">7 天内免密登录</van-checkbox>
      </div>

      <div class="auth-actions">
        <van-button round block type="primary" native-type="submit" :loading="loading" loading-text="登录中...">
          登录
        </van-button>
        <van-button round block plain class="auth-register" @click="goRegister">
          注册新账号
        </van-button>
      </div>
    </van-form>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  padding: 20vh 0 24px;
  box-sizing: border-box;
  background-color: var(--paper);
  background-image: radial-gradient(rgba(43, 38, 34, 0.05) 1px, transparent 1px);
  background-size: 7px 7px;
}
.auth-title {
  text-align: center;
  margin-bottom: 28px;
}
.auth-title h1 {
  margin: 0 0 8px;
  font-family: var(--fs-font-display);
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--ink);
}
.auth-title p {
  margin: 0;
  font-size: 14px;
  color: var(--ink-2);
}
.auth-actions {
  margin: 24px 16px 0;
}
.auth-remember {
  margin: 12px 16px 0;
}
.auth-remember :deep(.van-checkbox__label) {
  font-size: 13px;
  color: var(--ink-2);
}
.auth-register {
  margin-top: 12px;
  border-color: var(--ink);
  color: var(--ink);
  border-radius: 2px;
}
</style>