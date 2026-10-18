package com.gugucat.shortlink.project.dto.biz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 缓存失效消息
 * <p>
 * 写操作提交后发送，消费端延时再删一次跳转缓存，用于清掉竞态窗口里被读请求回填的旧值。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheInvalidateMessageDTO {

    /**
     * 完整短链接，缓存 key 由它拼接
     */
    private String fullShortUrl;

    /**
     * 触发原因，仅用于日志排查
     */
    private String reason;

    /**
     * 链路 ID
     */
    private String traceId;
}
