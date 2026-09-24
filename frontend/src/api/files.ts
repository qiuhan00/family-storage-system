// src/api/files.ts —— 文件接口封装（spec §5.6：multipart 上传返回 {url}，按相对路径删除）。
import { http } from './http';
import type { FileUploadResponse } from '../types';

export const filesApi = {
  /** POST /api/files（multipart 字段名 file）→ {url}，如 /uploads/202609/uuid.jpg。 */
  upload(file: File) {
    const form = new FormData();
    form.append('file', file);
    return http.post<FileUploadResponse>('/files', form).then((r) => r.data);
  },
  /** DELETE /api/files/{name} → 删除已上传文件；name 为相对路径（202609/uuid.jpg）。 */
  delete(name: string) {
    return http.delete<void>(`/files/${name}`).then((r) => r.data);
  },
};

/**
 * 由上传返回的 URL 推导 DELETE 端点的 {name} 入参：
 * `/uploads/202609/uuid.jpg` → `202609/uuid.jpg`（剥掉 /uploads 前缀，后端按相对路径定位）。
 */
export function toFileDeleteName(url: string): string {
  const matched = /^\/?uploads\/(.+)$/.exec(url);
  if (matched) return matched[1];
  return url.replace(/^[/\\]+/, '');
}