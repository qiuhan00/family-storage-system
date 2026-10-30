// src/types/index.ts —— 与后端 spec §5 对齐的请求/响应契约类型（后续任务复用）。

/** 用户角色。 */
export type Role = 'ADMIN' | 'MEMBER';

/** 登录用户响应（spec §5.1/§5.2；enabled 供启停回显）。 */
export interface UserResponse {
  id: number;
  username: string;
  displayName: string;
  role: Role;
  enabled: boolean;
}

/** 认证响应：JWT + 用户信息。 */
export interface AuthResponse {
  token: string;
  user: UserResponse;
}

/** 登录请求。 */
export interface LoginRequest {
  username: string;
  password: string;
}

/** 开放注册请求（仅系统为空时可用，首个注册者为 ADMIN）。 */
export interface RegisterRequest {
  username: string;
  password: string;
  displayName: string;
}

/** 修改本人密码请求。 */
export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}

/** 统一分页响应（spec §5）。 */
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

/** 后端统一错误体 {code, message}。 */
export interface ApiErrorBody {
  code: string;
  message: string;
}

/** 物品状态。 */
export type ItemStatus = 'ACTIVE' | 'ARCHIVED';

/** 物品（spec §5.4 ItemResponse）。 */
export interface Item {
  id: number;
  name: string;
  categoryId: number;
  locationId: number;
  locationDetail: string | null;
  lowStockThreshold: number | null;
  quantity: number;
  unit: string;
  purchasePrice: number;
  purchaseDate: string | null;
  expiryDate: string | null;
  photoUrl: string | null;
  status: ItemStatus;
  remark: string | null;
  createdBy: number;
  createdAt: string;
}

/** 出入库记录（spec §5.4 StockChangeResponse）。 */
export interface StockChange {
  id: number;
  itemId: number;
  changeQty: number;
  type: 'INITIAL' | 'IN' | 'OUT' | 'ADJUST';
  note: string | null;
  createdBy: number;
  createdAt: string;
}

/** 分类（spec §5.3）。 */
export interface Category {
  id: number;
  name: string;
  sortOrder: number;
}

/** 收纳位置树节点（spec §5.3，parentId 为 null 表示根节点）。 */
export interface Location {
  id: number;
  name: string;
  parentId: number | null;
  sortOrder: number;
  description?: string | null;
  imageUrl?: string | null;
  children: Location[];
}

/** 购物清单项状态。 */
export type ShoppingStatus = 'PENDING' | 'BOUGHT' | 'CANCELLED';

/** 购物清单项（spec §5.5 ShoppingItemResponse）。 */
export interface ShoppingItem {
  id: number;
  name: string;
  quantity: number;
  unit: string;
  categoryId: number | null;
  remark: string | null;
  status: ShoppingStatus;
  createdBy: number;
  createdAt: string;
  completedAt: string | null;
}

/** 通知类型。 */
export type NotificationType = 'EXPIRY' | 'LOW_STOCK' | 'OTHER';

/** 站内通知（spec §5.7）。 */
export interface Notification {
  id: number;
  userId: number;
  title: string;
  body: string;
  type: NotificationType;
  refId: number | null;
  reminderKey: string | null;
  readFlag: boolean;
  createdAt: string;
}

/** 未读数响应。 */
export interface UnreadCountResponse {
  count: number;
}

/** 统计总览（spec §5.8 SummaryResponse，仅统计 ACTIVE 物品）。 */
export interface Summary {
  totalItems: number;
  totalQuantity: number;
  totalValue: number;
  expiringSoon: number;
  pendingShopping: number;
}

/** 分类/位置维度聚合行（spec §5.8）。 */
export interface ItemCountByDimension {
  dimensionId: number;
  dimensionName: string;
  itemCount: number;
  totalQuantity: number;
  totalValue: number;
}

/** 文件上传响应（spec §5.6）。 */
export interface FileUploadResponse {
  url: string;
}

/** 物品创建/更新请求体（spec §5.4 ItemRequest，status 不随请求变更）。 */
export interface ItemPayload {
  name: string;
  categoryId: number;
  locationId: number;
  locationDetail: string | null;
  lowStockThreshold: number | null;
  quantity: number;
  unit: string | null;
  purchasePrice: number | null;
  purchaseDate: string | null;
  expiryDate: string | null;
  photoUrl: string | null;
  remark: string | null;
}

/** 出入库请求体（spec §5.4 POST /items/{id}/stock；deltaQty 正入负出）。 */
export interface StockAdjustPayload {
  deltaQty: number;
  type: 'IN' | 'OUT' | 'ADJUST';
  note: string | null;
}

/** 分类创建/更新请求体（spec §5.3 CategoryRequest）。 */
export interface CategoryPayload {
  name: string;
  sortOrder?: number | null;
}

/** 位置创建/更新请求体（spec §5.3 LocationRequest；parentId 为 null 表示根节点）。 */
export interface LocationPayload {
  name: string;
  parentId?: number | null;
  sortOrder?: number;
  description?: string | null;
  imageUrl?: string | null;
}