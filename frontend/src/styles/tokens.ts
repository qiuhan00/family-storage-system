// src/styles/tokens.ts —— 设计令牌（来源：ui-ux-pro-max 技能 color 域「Family Calendar & Chores」配色 + Minimalism 风格）。
// 调色板：family blue #2563EB + chore green #059669 + amber accent #D97706，浅底 #F8FAFC。

/** Vant ConfigProvider 主题变量：键名经机械转换为 --van-* 变量，未命中的键无副作用。 */
export const themeVars = {
  primaryColor: '#2563EB',
  primaryColorLight: '#DBEAFE',
  primaryColorDark: '#1D4ED8',
  successColor: '#059669',
  warningColor: '#D97706',
  dangerColor: '#DC2626',
  textColor: '#0F172A',
  textColor2: '#475569',
  textColor3: '#64748B',
  activeColor: '#2563EB',
  borderColor: '#E4ECFC',
  background: '#F8FAFC',
  background2: '#FFFFFF',
} as const;
