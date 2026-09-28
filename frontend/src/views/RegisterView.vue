// src/views/RegisterView.vue —— 注册页：仅系统为空时可注册；403「注册已关闭」给出提示。
<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant/es/toast';
import 'vant/es/toast/style';
import { useAuthStore } from '../store/auth';
import type { ApiErrorBody } from '../types';

const router = useRouter();
const auth = useAuthStore();

const form = reactive({
  username: '',
  displayName: '',
  password: '',
  confirmPassword: '',
});
const loading = ref(false);

const rules = {
  username: [{ required: true, message: '请输入用户名' }],
  displayName: [{ required: true, message: '请输入昵称' }],
  password: [
    { required: true, message: '请输入密码（6-32 位）' },
    { pattern: /^.{6,32}$/, message: '密码长度为 6-32 位' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码' },
    {
      validator: (value: string) => value === form.password,
      message: '两次输入的密码不一致',
    },
  ],
};

const onSubmit = async () => {
  loading.value = true;
  try {
    await auth.register({
      username: form.username.trim(),
      password: form.password,
      displayName: form.displayName.trim(),
    });
    showToast('注册成功');
    await router.replace('/');
  } catch (err) {
    const status = (err as { response?: { status?: number } }).response?.status;
    const body = (err as { response?: { data?: ApiErrorBody } }).response?.data;
    if (status === 403) {
      showToast(body?.message || '注册已关闭，请联系管理员创建账号');
    } else {
      showToast(body?.message || '注册失败，请稍后重试');
    }
  } finally {
    loading.value = false;
  }
};

const goLogin = () => router.replace('/login');
</script>

<template>
  <div class="auth-page">
    <div class="auth-title">
      <h1>注册账号</h1>
      <p>首个注册账号将成为管理员</p>
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
          v-model="form.displayName"
          name="displayName"
          label="昵称"
          placeholder="请输入昵称"
          :rules="rules.displayName"
          clearable
          autocomplete="nickname"
        />
        <van-field
          v-model="form.password"
          type="password"
          name="password"
          label="密码"
          placeholder="6-32 位"
          :rules="rules.password"
          autocomplete="new-password"
        />
        <van-field
          v-model="form.confirmPassword"
          type="password"
          name="confirmPassword"
          label="确认密码"
          placeholder="请再次输入密码"
          :rules="rules.confirmPassword"
          autocomplete="new-password"
        />
      </van-cell-group>

      <div class="auth-actions">
        <van-button round block type="primary" native-type="submit" :loading="loading" loading-text="注册中...">
          注册
        </van-button>
        <van-button round block plain class="auth-login" @click="goLogin">
          返回登录
        </van-button>
      </div>
    </van-form>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  padding: 16vh 0 24px;
  box-sizing: border-box;
  background: var(--fs-bg);
}
.auth-title {
  text-align: center;
  margin-bottom: 28px;
}
.auth-title h1 {
  margin: 0 0 8px;
  font-size: 26px;
  color: var(--fs-primary);
}
.auth-title p {
  margin: 0;
  font-size: 14px;
  color: var(--fs-text-3);
}
.auth-actions {
  margin: 24px 16px 0;
}
.auth-login {
  margin-top: 12px;
  border-color: var(--fs-text-3);
  color: var(--fs-text-2);
}
</style>