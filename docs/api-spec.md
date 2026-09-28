# 家庭收纳物品管理系统 — 接口规范（api-spec）

- 版本 / 基线：对应实现 HEAD `0273c5b`（分支 `feat/family-storage-system`）
- **权威来源**：`docs/superpowers/specs/2026-09-24-家庭收纳管理系统-design.md` **§5 接口规范**。
  本文件逐端点与 §5.1–§5.9 对齐（共 42 个端点，按「方法与路径」计），并补充请求 / 响应关键字段与错误码；不臆造任何字段。
- 验收依据：后端实现经接口自动化验收 **79/79 通过**（`docs/reports/api-test-report.md`）。

## 通用约定

- 统一前缀 `/api`，请求与响应均为 JSON，中文 message。
- **认证**：无状态 JWT。除 `POST /api/auth/login`、`POST /api/auth/register` 与静态 `/uploads/**` 外，所有端点需携带请求头 `Authorization: Bearer <token>`。
- **统一错误结构**：`{ "code": string, "message": string }`。
  - `401 UNAUTHORIZED`（未登录）/ `403 FORBIDDEN`（无权限，如 MEMBER 访问 ADMIN 端点）
  - `400` 校验失败或业务冲突（`code` 见各节与错误码表）
  - `404` 资源不存在，`500 INTERNAL_ERROR` 服务端兜底
- **分页结构**（`PageResponse<T>`）：`{ "content": T[], "totalElements": long, "totalPages": int, "size": int, "number": int }`。
- **文件上传规则**：`POST /api/files`，multipart 字段名 `file`；仅允许 `jpg / jpeg / png / webp`，单文件 `≤5MB`（Nginx 侧 `client_max_body_size 6m` 承载 multipart 边界）；成功返回 `{ "url": string }`。
- 列表查询分页参数走 Spring `Pageable` 约定：`page`（0 起）、`size`。

---

## 5.1 认证 Auth

| 方法 | 路径 | 说明 | 关键字段 | 业务错误码 |
|---|---|---|---|---|
| POST | `/api/auth/register` | **仅系统为空时**注册，首个账号为 ADMIN；非空返回 403 | 请求 `{username, password(6-32), displayName}`；响应 `{token, user}` | 403 `REGISTRATION_CLOSED`；400 `USERNAME_TAKEN`、`VALIDATION_ERROR` |
| POST | `/api/auth/login` | 登录 | 请求 `{username, password}` → `{token, user}` | 401 `BAD_CREDENTIALS` |
| GET | `/api/auth/me` | 当前登录用户信息 | 响应 `UserResponse`：`{id, username, displayName, role, enabled}` | 401 |
| PUT | `/api/auth/password` | 修改本人密码 | `{oldPassword, newPassword(6-32)}` | 400 `WRONG_PASSWORD`、`VALIDATION_ERROR`；401 |

## 5.2 用户 Users（仅 ADMIN）

| 方法 | 路径 | 说明 | 关键字段 | 业务错误码 |
|---|---|---|---|---|
| GET | `/api/users` | 成员列表（分页） | 响应 `PageResponse<UserResponse>`，列表项含 `enabled` 供启停回显 | 401 / 403 |
| POST | `/api/users` | 创建成员；`password` 缺省随机生成 8 位 | 请求 `{username, displayName, role, email?, phone?, password?}` → `UserResponse` | 400 `USERNAME_TAKEN`、`VALIDATION_ERROR` |
| PUT | `/api/users/{id}` | 更新成员（昵称 / 角色 / 联系方式）；`username` 不可改 | 请求同创建，`password` 忽略 | 404 `USER_NOT_FOUND` |
| PATCH | `/api/users/{id}/status` | 启用 / 停用（成员无删除端点，以停用代替） | `{enabled: boolean}` → 更新后 `UserResponse` | 404 `USER_NOT_FOUND`；403（停用自身/他人保护） |
| POST | `/api/users/{id}/reset-password` | 重置指定用户密码 | `{newPassword(6-32)}` | 404 `USER_NOT_FOUND` |

## 5.3 分类 / 位置 Categories & Locations

分类：

| 方法 | 路径 | 说明 | 关键字段 | 业务错误码 |
|---|---|---|---|---|
| GET | `/api/categories` | 列表（含 `GET /api/categories/{id}` 详情） | `Category`：`{id, name, sortOrder}` | 404 `CATEGORY_NOT_FOUND` |
| POST | `/api/categories` | 新增，返回新 id | `{name, sortOrder?}` | 400 `CATEGORY_NAME_TAKEN` |
| PUT | `/api/categories/{id}` | 更新 | 请求同新增 | 400 `CATEGORY_NAME_TAKEN`（忽略同名自身）；404 `CATEGORY_NOT_FOUND` |
| DELETE | `/api/categories/{id}` | 删除；有绑定物品时拒绝 400 | — | 400 `CATEGORY_IN_USE`；404 `CATEGORY_NOT_FOUND` |

位置（支持层级）：

| 方法 | 路径 | 说明 | 关键字段 | 业务错误码 |
|---|---|---|---|---|
| GET | `/api/locations` | **树形**列表（含 `GET /api/locations/{id}` 详情） | `LocationResponse`：`{id, name, parentId, sortOrder, description?, imageUrl?, children[]}` | 404 `LOCATION_NOT_FOUND` |
| POST | `/api/locations` | 新增；`parentId` 为 null 表示根节点 | `{name, parentId?, sortOrder, description?, imageUrl?}`（`description` ≤500 字，`imageUrl` 由 `POST /api/files` 上传后回填） | 404 `LOCATION_NOT_FOUND`（父位置不存在）；400 `LOCATION_CYCLE` |
| PUT | `/api/locations/{id}` | 更新 | 请求同新增 | 400 `LOCATION_CYCLE`；404 `LOCATION_NOT_FOUND` |
| DELETE | `/api/locations/{id}` | 删除；有物品引用或子级时拒绝 | — | 400 `LOCATION_IN_USE`、`LOCATION_HAS_CHILDREN`；404 `LOCATION_NOT_FOUND` |

> 图片口径（code-review Y3/Y4）：`imageUrl` 服务端校验格式 `^/(?!/)(?!.*\.\.)[A-Za-z0-9._/-]{1,254}$`（即仅接受 `POST /api/files` 返回的站内路径如 `/uploads/202609/uuid.jpg`，拒绝外链、`//` 协议相对 URL 与 `..` 路径段），违反返回 400 `VALIDATION_ERROR`；**删除位置不级联删除其图片文件**（与物品 `photoUrl` 口径一致），未被引用的上传文件由前端在保存成功/取消关闭表单时调用 `DELETE /api/files/{name}` 清理，清理失败容忍为孤悬文件。

## 5.4 物品 Items

| 方法 | 路径 | 说明 | 关键字段 | 业务错误码 |
|---|---|---|---|---|
| GET | `/api/items` | 分页检索：`keyword`（对 `name` LIKE 模糊）、`categoryId`、`locationId`、`status`(ACTIVE/ARCHIVED)、`expiring=1` | 响应 `PageResponse<ItemResponse>` | 400 `INVALID_STATUS`；401 |
| GET | `/api/items/expiring` | 临期物品列表（默认 7 天，可用 `days` 覆盖） | 响应 `ItemResponse[]` | — |
| GET | `/api/items/{id}` | 详情 | `ItemResponse`：`{id, name, categoryId, locationId, quantity, unit, purchasePrice, purchaseDate, expiryDate, photoUrl, status, remark, createdBy, createdAt}` | 404 `ITEM_NOT_FOUND` |
| POST | `/api/items` | 新增；`quantity>0` 时写 INITIAL 出入库记录，创建人取当前登录者 | 请求 `{name, categoryId, locationId, quantity, unit?, purchasePrice?, purchaseDate?, expiryDate?, photoUrl?, remark?}` | 400 `NEGATIVE_QUANTITY`；404 `CATEGORY_NOT_FOUND`、`LOCATION_NOT_FOUND` |
| PUT | `/api/items/{id}` | 更新 | 请求同新增 | 同上 + 404 `ITEM_NOT_FOUND` |
| DELETE | `/api/items/{id}` | 删除（级联删 `stock_changes`，历史硬删） | — | 404 `ITEM_NOT_FOUND` |
| POST | `/api/items/{id}/stock` | 出入库：`deltaQty` 正入负出，`type` ∈ IN/OUT/ADJUST（INITIAL 为内部类型，接口拒绝） | `{deltaQty, type, note?}` → `StockChangeResponse`：`{id, itemId, changeQty, type, note, createdBy, createdAt}` | 400 `INSUFFICIENT_STOCK`、`INVALID_STOCK_TYPE`、`TYPE_DELTA_MISMATCH`；404 `ITEM_NOT_FOUND` |
| GET | `/api/items/{id}/stock-changes` | 变更历史 | 响应 `StockChangeResponse[]` | 404 `ITEM_NOT_FOUND` |

## 5.5 缺耗品 Shopping

| 方法 | 路径 | 说明 | 关键字段 | 业务错误码 |
|---|---|---|---|---|
| GET | `/api/shopping` | 清单；`status` 为空返回全部（按创建倒序），BOUGHT/CANCELLED 写 `completed_at`，回到 PENDING 清空 | 响应 `ShoppingItemResponse[]`：`{id, name, quantity, unit, categoryId, remark, status, createdBy, createdAt, completedAt}` | 400 `INVALID_STATUS` |
| POST | `/api/shopping` | 新增缺耗品 | 请求 `{name, quantity, unit?, categoryId?, remark?}` | 400 `INVALID_QUANTITY`（quantity≤0）；404 `CATEGORY_NOT_FOUND` |
| PUT | `/api/shopping/{id}` | 更新 | 请求同新增 | 400 `INVALID_QUANTITY`；404 `SHOPPING_ITEM_NOT_FOUND`、`CATEGORY_NOT_FOUND` |
| PATCH | `/api/shopping/{id}/status` | 状态流转 | `{status: "BOUGHT" | "CANCELLED"}`，回 PENDING 即置回 | 400 `INVALID_STATUS`；404 `SHOPPING_ITEM_NOT_FOUND` |

## 5.6 文件 Files

| 方法 | 路径 | 说明 | 关键字段 | 业务错误码 |
|---|---|---|---|---|
| POST | `/api/files` | multipart 上传（字段 `file`），限 jpg/jpeg/png/webp ≤5MB | 响应 `{url}`，如 `/uploads/202609/uuid.png`（nginx 托管 `/uploads`） | 400 `EMPTY_FILE`、`INVALID_FILE_TYPE`、`FILE_TOO_LARGE`；500 `FILE_STORE_FAILED` |
| DELETE | `/api/files/{name}` | 删除已上传文件，`name` 为相对路径（如 `202609/uuid.png`）；目标不存在时按**幂等语义返回 204**（不报错） | — | 400 `INVALID_FILE_PATH`；500 `FILE_DELETE_FAILED` |

## 5.7 通知 Notifications

| 方法 | 路径 | 说明 | 关键字段 | 业务错误码 |
|---|---|---|---|---|
| GET | `/api/notifications` | 我的通知（分页），`unread=1` 仅未读；他人通知一律 404 | 响应 `PageResponse<Notification>`：`{id, userId, title, body, type, refId, reminderKey, readFlag, createdAt}` | 401 |
| GET | `/api/notifications/unread-count` | 未读数 | 响应 `{count}` | — |
| POST | `/api/notifications/{id}/read` | 标记已读 | 返回该通知 | 404 `NOTIFICATION_NOT_FOUND` |
| POST | `/api/notifications/read-all` | 全部已读 | — | — |

> 通知生成：每日 09:00 定时扫描临期物品（`expiry_date <= today + 阈值` 且 ACTIVE），经 `NotificationChannel` 写站内通知；`reminder_key=EXPIRY:{itemId}:{yyyy-MM-dd}` 幂等去重。

## 5.8 统计 Statistics

| 方法 | 路径 | 说明 | 关键字段 | 业务错误码 |
|---|---|---|---|---|
| GET | `/api/statistics/summary` | 总览（总数项仅计 ACTIVE） | `{totalItems, totalQuantity, totalValue, expiringSoon, pendingShopping}` | — |
| GET | `/api/statistics/by-category` | 分类维度聚合 | `ItemCountByDimension[]`：`{dimensionId, dimensionName, itemCount, totalQuantity, totalValue}`（无物品维度不返回） | — |
| GET | `/api/statistics/by-location` | 位置维度聚合 | 同上 | — |

## 5.9 设置 Settings

| 方法 | 路径 | 说明 | 关键字段 | 业务错误码 |
|---|---|---|---|---|
| GET | `/api/settings` | 读取公共设置（登录成员可读） | 响应 `{expiry_remind_days: 7}` | 401 |
| PUT | `/api/settings` | 更新（仅 ADMIN），键白名单 + 正整数校验 | 请求 `{key: "expiry.remind.days", value: "7"}` → 更新后全量设置 | 400 `INVALID_SETTING_KEY`、`INVALID_SETTING_VALUE`；403 | 

---

## 错误码表（汇总）

| code | HTTP | 触发场景 |
|---|---|---|
| `UNAUTHORIZED` | 401 | 未登录 / token 无效或过期 |
| `FORBIDDEN` | 403 | 已登录但无权限（如 MEMBER 访问用户管理 / 改设置） |
| `BAD_CREDENTIALS` | 401 | 登录用户名或密码错误；停用用户登录 |
| `REGISTRATION_CLOSED` | 403 | 系统非空时调用注册 |
| `WRONG_PASSWORD` | 400 | 修改密码时原密码错误 |
| `USERNAME_TAKEN` | 400 | 注册 / 创建成员用户名已存在 |
| `VALIDATION_ERROR` | 400 | 参数校验失败（Bean Validation，message 含具体字段） |
| `USER_NOT_FOUND` | 404 | 成员不存在 |
| `CATEGORY_NOT_FOUND` | 404 | 分类 / 物品关联分类不存在 |
| `CATEGORY_NAME_TAKEN` | 400 | 分类重名（更新时忽略同名自身） |
| `CATEGORY_IN_USE` | 400 | 删除仍绑定物品的分类 |
| `LOCATION_NOT_FOUND` | 404 | 位置 / 父位置不存在 |
| `LOCATION_CYCLE` | 400 | 位置层级成环或挂到自身下 |
| `LOCATION_IN_USE` | 400 | 删除仍绑定物品的位置 |
| `LOCATION_HAS_CHILDREN` | 400 | 删除仍有子级的位置 |
| `ITEM_NOT_FOUND` | 404 | 物品不存在 |
| `NEGATIVE_QUANTITY` | 400 | 物品数量为负 |
| `INVALID_STATUS` | 400 | 物品 / 购物清单非法状态值 |
| `INSUFFICIENT_STOCK` | 400 | 出库数量超出当前存量 |
| `INVALID_STOCK_TYPE` | 400 | 出入库类型非法（含手动提交 INITIAL） |
| `TYPE_DELTA_MISMATCH` | 400 | 出入库类型与增减符号不一致 |
| `INVALID_QUANTITY` | 400 | 购物清单数量 ≤0 |
| `SHOPPING_ITEM_NOT_FOUND` | 404 | 购物清单项不存在 |
| `EMPTY_FILE` | 400 | 上传文件内容为空 |
| `INVALID_FILE_TYPE` | 400 | 非 jpg/jpeg/png/webp |
| `FILE_TOO_LARGE` | 400 | 超过 5MB |
| `INVALID_FILE_PATH` | 400 | 文件路径非法 / 目录穿越 |
| `FILE_STORE_FAILED` | 500 | 文件保存失败 |
| `FILE_DELETE_FAILED` | 500 | 文件删除失败 |
| `NOTIFICATION_NOT_FOUND` | 404 | 通知不存在（含他人通知） |
| `INVALID_SETTING_KEY` | 400 | 不支持的设置键 |
| `INVALID_SETTING_VALUE` | 400 | 设置值非正整数 |
| `INTERNAL_ERROR` | 500 | 服务端内部错误（兜底） |