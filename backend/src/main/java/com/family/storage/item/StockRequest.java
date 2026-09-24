package com.family.storage.item;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 出入库请求体（spec §5.4 POST /api/items/{id}/stock，{deltaQty, type, note}）。
 * deltaQty 正入负出；type 为 IN/OUT/ADJUST（INITIAL 为内部类型，接口拒绝）；
 * 类型与 delta 的符号对齐校验放在 Service 层（400 TYPE_DELTA_MISMATCH），
 * 避免与错误码约定冲突。
 */
public record StockRequest(
        int deltaQty,
        @NotBlank @Size(max = 20) String type,
        @Size(max = 200) String note
) {
}