package com.gugucat.shortlink.project.mq.idempotent;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 消息队列幂等处理器
 * <p>
 * 用一条 Redis 记录承载消息的三个状态，靠 <b>SETNX 的返回值区分「我在处理」和「别人在处理」</b>：
 * <ul>
 *   <li>key 不存在 → SETNX 成功，写 "0"（处理中），说明这条消息归我处理</li>
 *   <li>key 已存在且值为 "0" → 有另一条线程正在处理同一条消息，且没处理完 → 应当重试</li>
 *   <li>key 已存在且值为 "1" → 已经处理完了 → 直接跳过</li>
 * </ul>
 * 处理过程中抛异常时把 key 删掉，是为了让这条消息能被重新处理
 * —— 留着 "0" 的话，重投回来的消息会被误判成「别人正在处理」而永远卡住。
 * <p>
 * key 带 2 分钟过期时间，是给「处理中」这个状态兜底：万一进程在处理途中被杀掉，
 * 没有兜底就会留下一个永不过期的 "0"，这条消息再也消费不了。
 */
@Component
@RequiredArgsConstructor
public class MessageQueueIdempotentHandler {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 幂等标识前缀，形如 clearlink:idempotent:{消息ID}
     */
    private static final String IDEMPOTENT_KEY_PREFIX = "clearlink:idempotent:";

    /**
     * 幂等标识的过期时间，单位分钟
     * <p>
     * 这个值同时决定了「处理中」状态的兜底时长和「已完成」状态的记忆时长。
     * 取 2 分钟：统计写入包含一次高德 HTTP 调用（有 3 秒超时），正常处理在秒级，
     * 2 分钟足够覆盖；而超过 2 分钟还没处理完，基本可以认为那次处理已经失败、
     * 允许这条消息被重新处理，代价是极小概率的重复计数。
     */
    private static final long IDEMPOTENT_KEY_TTL_MINUTES = 2;

    /**
     * 判断当前消息是否正在被消费（即：这条消息本次不该由我再处理）
     * <p>
     * 注意方法名与返回值的方向：<b>返回 true 表示「已被占用」</b>，
     * 首次调用会返回 false（意思是「没人碰过，你可以处理」）。
     *
     * @param messageId 消息唯一标识
     * @return 是否已被占用
     */
    public boolean isMessageBeingConsumed(String messageId) {
        String key = IDEMPOTENT_KEY_PREFIX + messageId;
        return Boolean.FALSE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, "0", IDEMPOTENT_KEY_TTL_MINUTES, TimeUnit.MINUTES));
    }

    /**
     * 判断消息的消费流程是否已执行完成
     *
     * @param messageId 消息唯一标识
     * @return 是否已执行完成
     */
    public boolean isAccomplish(String messageId) {
        String key = IDEMPOTENT_KEY_PREFIX + messageId;
        return Objects.equals(stringRedisTemplate.opsForValue().get(key), "1");
    }

    /**
     * 标记消息的消费流程已执行完成
     *
     * @param messageId 消息唯一标识
     */
    public void setAccomplish(String messageId) {
        String key = IDEMPOTENT_KEY_PREFIX + messageId;
        stringRedisTemplate.opsForValue().set(key, "1", IDEMPOTENT_KEY_TTL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 消息处理遇到异常时删除幂等标识，让这条消息能被重新处理
     *
     * @param messageId 消息唯一标识
     */
    public void delMessageProcessed(String messageId) {
        String key = IDEMPOTENT_KEY_PREFIX + messageId;
        stringRedisTemplate.delete(key);
    }
}
