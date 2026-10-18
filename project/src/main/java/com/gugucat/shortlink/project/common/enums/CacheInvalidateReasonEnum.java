package com.gugucat.shortlink.project.common.enums;

import lombok.Getter;

/**
 * 缓存失效原因
 */
@Getter
public enum CacheInvalidateReasonEnum {

    /**
     * 修改短链接
     */
    UPDATE("update"),

    /**
     * 移入回收站
     */
    RECYCLE_SAVE("recycle-save"),

    /**
     * 从回收站恢复
     */
    RECYCLE_RECOVER("recycle-recover"),

    /**
     * 从回收站移除
     */
    RECYCLE_REMOVE("recycle-remove");

    private final String reason;

    CacheInvalidateReasonEnum(String reason) {
        this.reason = reason;
    }
}
