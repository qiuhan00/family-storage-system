// src/styles/tokens.ts —— 设计令牌（便签纸视觉）：纸张/墨色/陶土/苔绿配色 + Vant 主题映射。
// 单一来源：CSS 变量在 global.css 中镜像这些值；Vant 主题经 App.vue 的 ConfigProvider 下发。
// 无障碍：正文只用 ink / ink2（对 paper/card ≥4.5:1）；ink3 仅作非文本装饰。

/** 便签纸调色板（hex 或 rgba 字符串）。 */
export const designTokens = {
  paper: '#F6F1E7',
  card: '#FFFDF8',
  note: '#FBF3D5',
  ink: '#2B2622',
  ink2: '#6E655A',
  ink3: '#8A8072',
  terracotta: '#C2603C',
  terracottaSoft: '#FBEFE6',
  moss: '#6B7A4F',
  mossSoft: '#E9EEDD',
  amber: '#C99A3B',
  line: 'rgba(43,38,34,.14)',
  dashed: 'rgba(43,38,34,.35)',
  danger: '#A8391F',
  dangerSoft: '#F4E3DC',
} as const;

/** Vant ConfigProvider 主题变量：主色取墨色（主按钮=墨色实心，盖章感）。 */
export const themeVars = {
  primaryColor: designTokens.ink,
  primaryColorLight: '#E9E3D6',
  primaryColorDark: '#1B1714',
  successColor: designTokens.moss,
  warningColor: designTokens.amber,
  dangerColor: designTokens.danger,
  textColor: designTokens.ink,
  textColor2: designTokens.ink2,
  textColor3: designTokens.ink2,
  activeColor: designTokens.ink,
  borderColor: '#E4DDCE',
  background: designTokens.paper,
  background2: designTokens.card,
} as const;
