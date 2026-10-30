// src/store/auth.spec.ts
// auth store 纯逻辑单元测试：token 读写 + Pinia store（localStorage 注入 mock，不触网）。
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { tokenKey, setToken, restoreToken, clearToken, useAuthStore } from './auth';
import { authApi } from '../api/auth';

// --- localStorage mock（vitest node 环境无原生 localStorage） ---
const memStore = new Map<string, string>();
const localStorageMock = {
  getItem: (k: string) => memStore.get(k) ?? null,
  setItem: (k: string, v: string) => void memStore.set(k, v),
  removeItem: (k: string) => void memStore.delete(k),
  clear: () => memStore.clear(),
  key: (i: number) => Array.from(memStore.keys())[i] ?? null,
  get length() {
    return memStore.size;
  },
};

// sessionStorage mock（免密登录未勾选时使用）
const sessionStore = new Map<string, string>();
const sessionStorageMock = {
  getItem: (k: string) => sessionStore.get(k) ?? null,
  setItem: (k: string, v: string) => void sessionStore.set(k, v),
  removeItem: (k: string) => void sessionStore.delete(k),
  clear: () => sessionStore.clear(),
  key: (i: number) => Array.from(sessionStore.keys())[i] ?? null,
  get length() {
    return sessionStore.size;
  },
};

vi.mock('../api/auth', () => ({
  authApi: {
    login: vi.fn(),
    register: vi.fn(),
    me: vi.fn(),
    changePassword: vi.fn(),
  },
}));

beforeEach(() => {
  vi.stubGlobal('localStorage', localStorageMock);
  vi.stubGlobal('sessionStorage', sessionStorageMock);
  memStore.clear();
  sessionStore.clear();
  setActivePinia(createPinia());
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('auth token helpers', () => {
  it('saves and restores token', () => {
    setToken('abc');
    expect(restoreToken()).toBe('abc');
  });

  it('restoreToken returns null when unset', () => {
    expect(restoreToken()).toBeNull();
  });

  it('clearToken removes persisted token', () => {
    setToken('abc');
    clearToken();
    expect(restoreToken()).toBeNull();
  });

  it('persists under fs_token key', () => {
    setToken('abc');
    expect(localStorage.getItem(tokenKey)).toBe('abc');
  });

  it('setToken(false) stores in sessionStorage only', () => {
    setToken('sess', false);
    expect(sessionStorage.getItem(tokenKey)).toBe('sess');
    expect(localStorage.getItem(tokenKey)).toBeNull();
  });

  it('setToken(true) stores in localStorage and clears session token', () => {
    setToken('sess', false);
    setToken('persist', true);
    expect(localStorage.getItem(tokenKey)).toBe('persist');
    expect(sessionStorage.getItem(tokenKey)).toBeNull();
  });

  it('restoreToken falls back to sessionStorage', () => {
    sessionStorage.setItem(tokenKey, 'sess');
    expect(restoreToken()).toBe('sess');
  });

  it('clearToken removes both storages', () => {
    localStorage.setItem(tokenKey, 'a');
    sessionStorage.setItem(tokenKey, 'b');
    clearToken();
    expect(localStorage.getItem(tokenKey)).toBeNull();
    expect(sessionStorage.getItem(tokenKey)).toBeNull();
  });
});

describe('useAuthStore', () => {
  it('init restores token from localStorage into state', () => {
    setToken('persisted-token');
    const store = useAuthStore();
    store.init();
    expect(store.token).toBe('persisted-token');
  });

  it('login stores token + user and persists token', async () => {
    const authResponse = {
      token: 'jwt-x',
      user: { id: 1, username: 'alice', displayName: 'Alice', role: 'ADMIN' as const, enabled: true },
    };
    vi.mocked(authApi.login).mockResolvedValue(authResponse);

    const store = useAuthStore();
    await store.login({ username: 'alice', password: 'secret' });

    expect(authApi.login).toHaveBeenCalledWith({ username: 'alice', password: 'secret' });
    expect(store.token).toBe('jwt-x');
    expect(store.user?.username).toBe('alice');
    expect(store.user?.role).toBe('ADMIN');
    expect(restoreToken()).toBe('jwt-x');
  });

  it('login with remember=false stores session token only', async () => {
    const authResponse = {
      token: 'jwt-s',
      user: { id: 1, username: 'alice', displayName: 'Alice', role: 'ADMIN' as const, enabled: true },
    };
    vi.mocked(authApi.login).mockResolvedValue(authResponse);

    const store = useAuthStore();
    await store.login({ username: 'alice', password: 'secret' }, false);

    expect(sessionStorage.getItem(tokenKey)).toBe('jwt-s');
    expect(localStorage.getItem(tokenKey)).toBeNull();
    expect(restoreToken()).toBe('jwt-s');
  });

  it('login failure keeps token null and nothing persisted', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new Error('401'));

    const store = useAuthStore();
    await expect(store.login({ username: 'alice', password: 'wrong' })).rejects.toThrow('401');

    expect(store.token).toBeNull();
    expect(store.user).toBeNull();
    expect(restoreToken()).toBeNull();
  });

  it('register stores token + user and persists token', async () => {
    const authResponse = {
      token: 'jwt-reg',
      user: { id: 1, username: 'bob', displayName: 'Bob', role: 'ADMIN' as const, enabled: true },
    };
    vi.mocked(authApi.register).mockResolvedValue(authResponse);

    const store = useAuthStore();
    await store.register({ username: 'bob', password: 'secret1', displayName: 'Bob' });

    expect(store.token).toBe('jwt-reg');
    expect(store.user?.displayName).toBe('Bob');
    expect(restoreToken()).toBe('jwt-reg');
  });

  it('register failure does not persist token', async () => {
    vi.mocked(authApi.register).mockRejectedValue(new Error('403'));

    const store = useAuthStore();
    await expect(
      store.register({ username: 'bob', password: 'secret1', displayName: 'Bob' }),
    ).rejects.toThrow('403');

    expect(store.token).toBeNull();
    expect(restoreToken()).toBeNull();
  });

  it('logout clears state and storage', async () => {
    setToken('jwt-x');
    const store = useAuthStore();
    store.init();
    store.user = { id: 1, username: 'alice', displayName: 'Alice', role: 'ADMIN', enabled: true };

    store.logout();

    expect(store.token).toBeNull();
    expect(store.user).toBeNull();
    expect(restoreToken()).toBeNull();
  });
});