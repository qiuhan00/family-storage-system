# 证据索引 · 「位置管理」E2E（2026-09-28 15:57 运行）

- 结果：**1 passed / 0 failed / 0 skipped（27.8s）**，16/16 步标记 PASS（E-LM-01 .. E-LM-10）
- 视口 375×667 · Chrome headless · 脚本 `docs/e2e/scripts/family-storage-locations.spec.js`
- 完整报告：`docs/reports/e2e-report-2026-09-28-location-management.md`

## 内容

| 文件 | 说明 |
|---|---|
| `lstep-01..28-*.png` | 28 张步骤截图（按执行顺序） |
| `recording.webm` | 全流程录像 |
| `trace.zip` | Playwright trace（`show trace` 可回放 DOM/网络） |
| `run3-playwright.log` | 控制台输出：STEP_RESULTS / 写请求足迹 / 4xx 放行清单 |
| `test-results/` | `.last-run.json`（status=passed）+ trace/video 副本 |
| `obs-LM04-inline-error-labeled.png` | E-LM-04 内联错误展示（带文件名标签，供人工复核） |
| `obs-LM05-toast-after-close-labeled.png` | E-LM-05 保存后弹层已关闭 + toast（正常态） |
| `obs-DEF-LOC-01-toast-title-overlap-labeled.png` | **缺陷 DEF-LOC-01**：toast 与弹层标题叠字（带标签） |
| `obs-DEF-LOC-01-zoom-title.png` | DEF-LOC-01 标题区 6× 放大 |
| `probe-popup-timeline-after-edit-save.png` | 16:30 时序探针截图（编辑保存后过渡态） |

## 已知非阻塞缺陷

- **DEF-LOC-01（低）**：保存成功后 toast「已保存」与底部弹层标题「新增位置」在关闭动画期（~450–550ms）重叠出现叠字；探针采样 `ms=497 overlap=true`，弹层约 800ms 内正常关闭。详见主报告。
