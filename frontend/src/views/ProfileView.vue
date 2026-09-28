<!-- src/views/ProfileView.vue —— 个人中心：当前用户信息（displayName/username/role）、修改密码
     （旧+新+确认，客户端校验；PUT /api/auth/password）、退出登录（清 token 回 /login）；
     ADMIN 专属入口（用户管理 / 应用设置）。 -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant/es/toast';
import 'vant/es/toast/style';
import type { FieldRule } from 'vant';
import { apiErrorMessage } from '../api/http';
import { authApi } from '../api/auth';
import { useAuthStore } from '../store/auth';
import type { Role } from '../types';

const router = useRouter();
const auth = useAuthStore();

const roleLabel: Record<Role, string> = { ADMIN: '管理员', MEMBER: '成员' };

const displayName = computed(() => auth.user?.displayName ?? '未登录');
const username = computed(() => auth.user?.username ?? '');
const role = computed<Role | ''>(() => auth.user?.role ?? '');
const isAdmin = computed(() => auth.user?.role === 'ADMIN');

// --- 修改密码 ---
const showPassword = ref(false);
const submitting = ref(false);
const pwdForm = ref({ oldPassword: '', newPassword: '', confirm: '' });

const oldPwdRule: FieldRule = { required: true, message: '请输入原密码' };
const newPwdRule: FieldRule = {
  required: true,
  message: '请输入新密码',
  validator: (v: string) => (v.length >= 6 ? true : '新密码至少 6 位'),
};
const confirmRule: FieldRule = {
  required: true,
  message: '请再次输入新密码',
  validator: (v: string) => (v === pwdForm.value.newPassword ? true : '两次输入的密码不一致'),
};

async function openPassword() {
  pwdForm.value = { oldPassword: '', newPassword: '', confirm: '' };
  showPassword.value = true;
}

async function submitPassword() {
  if (submitting.value) return;
  if (pwdForm.value.newPassword === pwdForm.value.oldPassword) {
    showToast('新密码不能与原密码相同');
    return;
  }
  submitting.value = true;
  try {
    await authApi.changePassword({
      oldPassword: pwdForm.value.oldPassword,
      newPassword: pwdForm.value.newPassword,
    });
    showPassword.value = false;
    showToast('密码已修改');
  } catch (e) {
    showToast(apiErrorMessage(e, '密码修改失败'));
  } finally {
    submitting.value = false;
  }
}

// --- 退出登录 ---
function logout() {
  auth.logout();
  showToast('已退出登录');
  void router.replace('/login');
}

onMounted(async () => {
  // 刷新页面后 store 中 user 为空（仅 token 已恢复），从 /auth/me 回填当前用户。
  if (auth.token && !auth.user) {
    try {
      auth.user = await authApi.me();
    } catch {
      // 401 由 http 拦截器统一处理跳登录页
    }
  }
});
</script>

<template>
  <div class="profile-page">
    <!-- 用户信息卡 -->
    <div class="profile-card">
      <div class="profile-card__avatar">
        <van-icon name="user-circle-o" size="48" color="var(--fs-primary)" aria-hidden="true" />
      </div>
      <div class="profile-card__info">
        <div class="profile-card__name">{{ displayName }}</div>
        <div class="profile-card__meta">@{{ username }} · {{ role ? roleLabel[role] : '' }}</div>
      </div>
    </div>

    <van-cell-group inset class="profile-group">
      <van-cell title="修改密码" is-link @click="openPassword" />
      <van-cell title="位置管理" label="新增位置 / 填写说明与照片" is-link to="/locations" />
    </van-cell-group>

    <van-cell-group v-if="isAdmin" inset class="profile-group profile-group--admin">
      <van-cell title="用户管理" label="新增成员 / 启停 / 重置密码" is-link to="/users" />
      <van-cell title="应用设置" label="临期提醒天数阈值" is-link to="/settings" />
    </van-cell-group>

    <div class="profile-logout">
      <van-button round block type="danger" plain @click="logout">退出登录</van-button>
    </div>

    <!-- 修改密码弹层 -->
    <van-popup v-model:show="showPassword" position="bottom" round>
      <div class="pwd-panel">
        <div class="pwd-panel__title">修改密码</div>
        <van-form validate-first @submit="submitPassword">
          <van-field
            v-model="pwdForm.oldPassword"
            name="oldPassword"
            type="password"
            label="原密码"
            placeholder="请输入原密码"
            :maxlength="32"
            :rules="[oldPwdRule]"
          />
          <van-field
            v-model="pwdForm.newPassword"
            name="newPassword"
            type="password"
            label="新密码"
            placeholder="至少 6 位"
            :maxlength="32"
            :rules="[newPwdRule]"
          />
          <van-field
            v-model="pwdForm.confirm"
            name="confirm"
            type="password"
            label="确认密码"
            placeholder="再次输入新密码"
            :maxlength="32"
            :rules="[confirmRule]"
          />
          <div class="pwd-panel__footer">
            <van-button size="small" plain type="default" @click="showPassword = false">取消</van-button>
            <van-button size="small" type="primary" native-type="submit" :loading="submitting">
              确认修改
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.profile-page {
  padding: 16px 0 24px;
}

.profile-card {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0 12px 12px;
  padding: 20px 16px;
  border-radius: 8px;
  background: var(--fs-surface);
  box-shadow: var(--fs-shadow-sm);
}

.profile-card__info {
  min-width: 0;
}

.profile-card__name {
  font-size: 18px;
  font-weight: 600;
  color: var(--fs-text);
}

.profile-card__meta {
  margin-top: 4px;
  font-size: 13px;
  color: var(--fs-text-3);
}

.profile-group {
  margin-bottom: 12px;
}

.profile-group--admin .van-cell {
  color: var(--fs-primary);
}

.profile-logout {
  padding: 16px;
}

.pwd-panel__title {
  padding: 16px 16px 4px;
  font-size: 16px;
  font-weight: 600;
  text-align: center;
  color: var(--fs-text);
}

.pwd-panel__footer {
  display: flex;
  gap: 12px;
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
}

.pwd-panel__footer .van-button {
  flex: 1;
}
</style>