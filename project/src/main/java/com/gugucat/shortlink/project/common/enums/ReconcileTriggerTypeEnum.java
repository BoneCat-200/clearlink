package com.gugucat.shortlink.project.common.enums;

import lombok.Getter;

/**
 * 统计对账触发方式
 */
@Getter
public enum ReconcileTriggerTypeEnum {

    /**
     * 定时任务触发
     */
    SCHEDULE("schedule"),

    /**
     * 人工通过接口触发
     */
    MANUAL("manual");

    private final String type;

    ReconcileTriggerTypeEnum(String type) {
        this.type = type;
    }
}
