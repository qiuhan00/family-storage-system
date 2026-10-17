import { describe, it, expect } from 'vitest';
import { designTokens } from './tokens';

function srgb(c: number) {
  const s = c / 255;
  return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
}
function lum(hex: string) {
  const n = hex.replace('#', '');
  const r = parseInt(n.slice(0, 2), 16);
  const g = parseInt(n.slice(2, 4), 16);
  const b = parseInt(n.slice(4, 6), 16);
  return 0.2126 * srgb(r) + 0.7152 * srgb(g) + 0.0722 * srgb(b);
}
function contrast(a: string, b: string) {
  const L1 = lum(a);
  const L2 = lum(b);
  const hi = Math.max(L1, L2);
  const lo = Math.min(L1, L2);
  return (hi + 0.05) / (lo + 0.05);
}

describe('paper design tokens', () => {
  it('defines required color tokens', () => {
    const keys = [
      'paper',
      'card',
      'note',
      'ink',
      'ink2',
      'ink3',
      'terracotta',
      'terracottaSoft',
      'moss',
      'mossSoft',
      'amber',
      'line',
      'dashed',
      'danger',
    ] as const;
    for (const k of keys) {
      expect(designTokens[k]).toMatch(/^#[0-9A-Fa-f]{6}$|^rgba\(/);
    }
  });

  it('body text meets 4.5:1 on paper and card', () => {
    for (const bg of [designTokens.paper, designTokens.card]) {
      expect(contrast(designTokens.ink, bg)).toBeGreaterThanOrEqual(4.5);
      expect(contrast(designTokens.ink2, bg)).toBeGreaterThanOrEqual(4.5);
    }
  });

  it('ink3 is reserved for non-text (under 4.5:1)', () => {
    expect(contrast(designTokens.ink3, designTokens.paper)).toBeLessThan(4.5);
  });
});
