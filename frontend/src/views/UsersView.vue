<!-- src/views/UsersView.vue（ADMIN）—— 成员管理：分页列表（用户名/显示名/角色/状态）、
     新增成员（含密码，可选留空由后端随机生成）、启用/停用 Switch、重置密码（新密码 Dialog）。
     契约 §5.2 无删除端点，故不提供删除。
     状态启用/停用：UserResponse.enabled（R13 后契约已含该字段），Switch 直接读写。 -->
<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant/es/toast';
import 'vant/es/toast/style';
import type { FieldRule } from 'vant';
import { apiErrorMessage } from '../api/http';
import { usersApi, type UserPayload } from '../api/users';
import { authApi } from '../api/auth';
import PaperIcon from '../components/PaperIcon.vue';
import { useAuthStore } from '../store/auth';
import type { Role, UserResponse } from '../types';

const PAGE_SIZE = 20;

const router = useRouter();
const auth = useAuthStore();

// --- ADMIN 守卫（组件内）：刷新后 user 为空则先 /auth/me 回填，非 ADMIN 跳回个人中心 ---
async function ensureAdmin() {
  try {
    if (!auth.user && auth.token) {
      auth.user = await authApi.me();
    }
    if (auth.user?.role !== 'ADMIN') {
      showToast('无权限访问成员管理');
      void router.replace('/profile');
      return;
    }
    void loadFirstPage();
  } catch {
    // 401 由 http 拦截器统一处理跳登录页
  }
}

// --- 分页列表 ---
const users = ref<UserResponse[]>([]);
const loading = ref(false);
const finished = ref(false);
const refreshing = ref(false);
const totalElements = ref(0);
const page = ref(-1);

async function fetchPage(p: number) {
  loading.value = true;
  try {
    const data = await usersApi.list(p, PAGE_SIZE);
    const content = data.content ?? [];
    users.value = p === 0 ? content : [...users.value, ...content];
    page.value = p;
    totalElements.value = data.totalElements ?? 0;
    finished.value = content.length === 0 || data.totalPages <= p + 1;
  } catch (e) {
    showToast(apiErrorMessage(e, '成员列表加载失败'));
    if (p === 0) users.value = [];
  } finally {
    loading.value = false;
  }
}

function onLoad() {
  if (refreshing.value) return;
  void fetchPage(page.value + 1);
}

async function loadFirstPage() {
  finished.value = false;
  users.value = [];
  page.value = -1;
  await fetchPage(0);
}

async function onRefresh() {
  refreshing.value = true;
  finished.value = false;
  try {
    await fetchPage(0);
  } finally {
    refreshing.value = false;
  }
}

// --- 状态（启停）Switch：直接读 u.enabled + PATCH 持久化 ---

const roleLabel: Record<Role, string> = { ADMIN: '管理员', MEMBER: '成员' };

function isEnabled(u: UserResponse): boolean {
  return u.enabled;
}

async function onToggleEnabled(u: UserResponse, checked: boolean) {
  if (u.role === 'ADMIN') {
    showToast('不能停用管理员账户');
    return;
  }
  try {
    await usersApi.setStatus(u.id, checked);
    u.enabled = checked;
    showToast(checked ? '已启用' : '已停用');
  } catch (e) {
    showToast(apiErrorMessage(e, '状态更新失败'));
  }
}

// --- 新增成员 ---
const showCreate = ref(false);
const creating = ref(false);
const createForm = ref<{
  username: string;
  displayName: string;
  role: Role;
  password: string;
  email: string;
  phone: string;
}>({ username: '', displayName: '', role: 'MEMBER', password: '', email: '', phone: '' });

const usernameRule: FieldRule = { required: true, message: '请填写用户名' };
const displayNameRule: FieldRule = { required: true, message: '请填写显示名' };
const passwordRule: FieldRule = {
  validator: (v: string) => {
    if (v === '') return true; // 留空 → 后端随机生成 8 位
    return v.length >= 6 && v.length <= 32 ? true : '密码长度为 6-32 位';
  },
};

function openCreate() {
  createForm.value = { username: '', displayName: '', role: 'MEMBER', password: '', email: '', phone: '' };
  showCreate.value = true;
}

async function submitCreate() {
  if (creating.value) return;
  creating.value = true;
  const payload: UserPayload = {
    username: createForm.value.username.trim(),
    displayName: createForm.value.displayName.trim(),
    role: createForm.value.role,
    password: createForm.value.password || null,
    email: createForm.value.email.trim() || null,
    phone: createForm.value.phone.trim() || null,
  };
  try {
    await usersApi.create(payload);
    showCreate.value = false;
    showToast('已新增成员');
    await loadFirstPage();
  } catch (e) {
    showToast(apiErrorMessage(e, '新增成员失败'));
  } finally {
    creating.value = false;
  }
}

// --- 重置密码 ---
const showReset = ref(false);
const resetTarget = ref<UserResponse | null>(null);
const resetPassword = ref('');
const resetting = ref(false);

function openReset(u: UserResponse) {
  resetTarget.value = u;
  resetPassword.value = '';
  showReset.value = true;
}

function closeReset() {
  resetTarget.value = null;
  showReset.value = false;
}

async function submitReset() {
  if (resetting.value || !resetTarget.value) return;
  if (resetPassword.value.length < 6 || resetPassword.value.length > 32) {
    showToast('新密码长度为 6-32 位');
    return;
  }
  resetting.value = true;
  try {
    await usersApi.resetPassword(resetTarget.value.id, resetPassword.value);
    closeReset();
    showToast('已重置密码');
  } catch (e) {
    showToast(apiErrorMessage(e, '重置密码失败'));
  } finally {
    resetting.value = false;
  }
}

onMounted(() => {
  void ensureAdmin();
});
</script>

<template>
  <div class="users-page">
    <div class="users-toolbar">
      <span class="users-toolbar__title">
        成员管理
        <span class="users-toolbar__count" v-if="totalElements">共 {{ totalElements }} 人</span>
      </span>
      <van-button size="small" type="primary" class="paper-sticker-btn" @click="openCreate">
        <template #icon><PaperIcon name="plus" :size="14" /></template>
        新增成员
      </van-button>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        :immediate-check="false"
        finished-text="没有更多了"
        @load="onLoad"
      >
        <div v-if="users.length" class="user-list">
          <div v-for="u in users" :key="u.id" class="user-card paper-card">
            <div class="user-card__info">
              <div class="user-card__name">
                {{ u.displayName }}
                <span
                  class="paper-chip user-card__role"
                  :class="u.role === 'ADMIN' ? 'user-card__role--admin' : 'user-card__role--member'"
                >
                  {{ roleLabel[u.role] }}
                </span>
              </div>
              <div class="user-card__meta">
                @{{ u.username }} · {{ isEnabled(u) ? '启用中' : '已停用' }}
              </div>
            </div>
            <div class="user-card__ops">
              <van-switch
                :model-value="isEnabled(u)"
                size="20"
                aria-label="启用/停用"
                @update:model-value="(v: boolean) => onToggleEnabled(u, v)"
              />
              <van-button size="mini" plain type="primary" @click="openReset(u)">重置密码</van-button>
            </div>
          </div>
        </div>
        <van-empty v-else-if="!loading && finished" description="暂无成员" image-size="80" />
      </van-list>
    </van-pull-refresh>

    <!-- 新增成员弹层 -->
    <van-popup v-model:show="showCreate" position="bottom" round>
      <div class="panel">
        <div class="panel__title">新增成员</div>
        <van-form validate-first @submit="submitCreate">
          <van-field
            v-model="createForm.username"
            name="username"
            label="用户名"
            placeholder="登录账号（创建后不可改）"
            :maxlength="50"
            :rules="[usernameRule]"
          />
          <van-field
            v-model="createForm.displayName"
            name="displayName"
            label="显示名"
            placeholder="如：老妈"
            :maxlength="50"
            :rules="[displayNameRule]"
          />
          <van-field name="role" label="角色">
            <template #input>
              <van-radio-group v-model="createForm.role" direction="horizontal">
                <van-radio name="MEMBER">成员</van-radio>
                <van-radio name="ADMIN">管理员</van-radio>
              </van-radio-group>
            </template>
          </van-field>
          <van-field
            v-model="createForm.password"
            name="password"
            type="password"
            label="初始密码"
            placeholder="留空则随机生成 8 位"
            :maxlength="32"
            :rules="[passwordRule]"
          />
          <van-field v-model="createForm.email" name="email" type="email" label="邮箱" placeholder="可选" :maxlength="100" />
          <van-field v-model="createForm.phone" name="phone" type="tel" label="手机号" placeholder="可选" :maxlength="20" />
          <div class="panel__footer">
            <van-button size="small" plain type="default" @click="showCreate = false">取消</van-button>
            <van-button size="small" type="primary" native-type="submit" :loading="creating">确认新增</van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <!-- 重置密码弹层 -->
    <van-popup v-model:show="showReset" position="bottom" round>
      <div class="panel">
        <div class="panel__title">重置密码</div>
        <div class="panel__desc">为「{{ resetTarget?.displayName }}（@{{ resetTarget?.username }}）」设置新密码</div>
        <van-form @submit="submitReset">
          <van-field
            v-model="resetPassword"
            name="newPassword"
            type="password"
            label="新密码"
            placeholder="6-32 位"
            :maxlength="32"
          />
          <div class="panel__footer">
            <van-button size="small" plain type="default" @click="closeReset">取消</van-button>
            <van-button size="small" type="primary" native-type="submit" :loading="resetting">确认重置</van-button>
          </div>
        </van-form>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.users-page {
  padding-bottom: 12px;
}

.users-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  background: var(--paper);
}

.users-toolbar__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--fs-text);
}

.users-toolbar__count {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 400;
  color: var(--ink-2);
}

.user-list {
  padding: 0 12px 12px;
}

.user-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
  padding: 12px;
}

.user-card__info {
  min-width: 0;
}

.user-card__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--fs-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-card__role {
  margin-left: 6px;
}

.user-card__role--admin {
  color: var(--terracotta);
  background: var(--terracotta-soft);
  border-color: rgba(194, 96, 60, 0.5);
}

.user-card__role--member {
  color: #4e5a3a;
  background: var(--moss-soft);
  border-color: rgba(107, 122, 79, 0.5);
}

.user-card__meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ink-2);
}

.user-card__ops {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.panel {
  background: var(--card);
}

.panel__title {
  padding: 16px 16px 4px;
  font-family: var(--fs-font-display);
  font-size: 16px;
  font-weight: 700;
  text-align: center;
  color: var(--ink);
}

.panel__desc {
  padding: 4px 16px 8px;
  font-size: 12px;
  color: var(--ink-2);
  text-align: center;
}

.panel__footer {
  display: flex;
  gap: 12px;
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
}

.panel__footer .van-button {
  flex: 1;
}
</style>