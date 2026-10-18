package com.gugucat.shortlink.project.common.enums;

import lombok.Getter;

/**
 * 统计对账状态
 */
@Getter
public enum ReconcileStatusEnum {

    /**
     * 执行中：报告先以该状态落库，任务结束后再更新
     */
    RUNNING(0, "执行中"),

    /**
     * 对账完成
     */
    SUCCESS(1, "成功"),

    /**
     * 对账过程中出现异常
     */
    FAILED(2, "失败");

    private final Integer status;

    private final String description;

    ReconcileStatusEnum(Integer status, String description) {
        this.status = status;
        this.description = description;
    }

    /**
     * 按状态值取枚举
     *
     * @param status 状态值
     * @return 对应枚举；未知值返回 null
     */
    public static ReconcileStatusEnum of(Integer status) {
        for (ReconcileStatusEnum each : values()) {
            if (each.status.equals(status)) {
                return each;
            }
        }
        return null;
    }
}
