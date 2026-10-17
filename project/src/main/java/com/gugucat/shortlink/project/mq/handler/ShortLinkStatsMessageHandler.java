package com.gugucat.shortlink.project.mq.handler;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.Week;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gugucat.shortlink.project.dao.entity.LinkAccessLogsDO;
import com.gugucat.shortlink.project.dao.entity.LinkAccessStatsDO;
import com.gugucat.shortlink.project.dao.entity.LinkBrowserStatsDO;
import com.gugucat.shortlink.project.dao.entity.LinkDeviceStatsDO;
import com.gugucat.shortlink.project.dao.entity.LinkLocaleStatsDO;
import com.gugucat.shortlink.project.dao.entity.LinkNetworkStatsDO;
import com.gugucat.shortlink.project.dao.entity.LinkOsStatsDO;
import com.gugucat.shortlink.project.dao.entity.LinkStatsTodayDO;
import com.gugucat.shortlink.project.dao.entity.ShortLinkGotoDO;
import com.gugucat.shortlink.project.dao.mapper.LinkAccessLogsMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkAccessStatsMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkBrowserStatsMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkDeviceStatsMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkLocaleStatsMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkNetworkStatsMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkOsStatsMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkStatsTodayMapper;
import com.gugucat.shortlink.project.dao.mapper.ShortLinkGotoMapper;
import com.gugucat.shortlink.project.dao.mapper.ShortLinkMapper;
import com.gugucat.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import com.gugucat.shortlink.project.mq.idempotent.MessageQueueIdempotentHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.gugucat.shortlink.project.common.constant.RedisKeyConstant.LOCK_GID_UPDATE_KEY;
import static com.gugucat.shortlink.project.common.constant.ShortLinkConstant.AMAP_REMOTE_URL;
import static com.gugucat.shortlink.project.common.constant.ShortLinkConstant.AMAP_TIME_OUT;

/**
 * 短链接监控消息处理器
 * <p>
 * 一条消息 = 一次跳转，落库要写 8 张表 + 自增 t_link 的三个总计字段。
 * 这些写操作全部包在同一把「分组变更」读锁里，见 {@link #actualSaveShortLinkStats}。
 * <p>
 * <b>为什么把落库逻辑从「消费者」里抽出来</b>：消息中间件是可切换的
 * （见 {@code short-link.mq.type}），但「怎么解析、怎么幂等、怎么写库」与用哪个
 * 中间件无关。抽成 handler 之后，Redis Stream 和 RocketMQ 各是一个薄薄的适配器，
 * 两边共用同一套落库与幂等逻辑 —— 不会出现「换了 MQ 之后统计口径变了」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShortLinkStatsMessageHandler {

    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkGotoMapper shortLinkGotoMapper;
    private final RedissonClient redissonClient;
    private final LinkAccessStatsMapper linkAccessStatsMapper;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkOsStatsMapper linkOsStatsMapper;
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;
    private final LinkDeviceStatsMapper linkDeviceStatsMapper;
    private final LinkNetworkStatsMapper linkNetworkStatsMapper;
    private final LinkStatsTodayMapper linkStatsTodayMapper;
    private final MessageQueueIdempotentHandler messageQueueIdempotentHandler;

    /**
     * 高德逆地理的 key，默认为空
     * <p>
     * 故意留空默认值：这是个人申请的应用 key，不能写进配置文件与提交历史。
     * 启动时通过环境变量注入（见 application.yaml 该配置项的写法）。
     * 未配置时地区维度会统一记为「未知」，其余维度不受影响。
     */
    @Value("${short-link.stats.locale.amap-key:}")
    private String statsLocaleAmapKey;

    /**
     * 地区未知时的占位值
     */
    private static final String UNKNOWN_LOCALE = "未知";

    /**
     * 处理一条统计消息
     * <p>
     * 幂等与落库都在这里，**与用哪个消息中间件无关**：
     * Redis Stream 的消费者和 RocketMQ 的消费者都只是把消息交进来。
     * <p>
     * 返回值的含义决定了调用方要不要「确认」这条消息：
     * <ul>
     *   <li>{@link HandleResult#SUCCESS} / {@link HandleResult#ALREADY_DONE} ——
     *       处理完了（或之前就处理完了），可以确认并删除</li>
     *   <li>{@link HandleResult#FAILED} —— 处理失败，幂等键已删除，
     *       消息应留在待确认列表里等回收任务重投</li>
     *   <li>{@link HandleResult#IN_PROGRESS} —— 幂等键还停在「处理中」，
     *       可能是另一个实例在处理，也可能是上一轮处理到一半进程挂了。
     *       什么都不做，等幂等键过期后由回收任务重投</li>
     * </ul>
     *
     * @param statsRecordJson 统计记录的 JSON
     * @param messageId       消息唯一标识（Redis Stream 的 recordId / RocketMQ 的 msgId）
     * @return 处理结果
     */
    public HandleResult handle(String statsRecordJson, String messageId) {
        if (messageQueueIdempotentHandler.isMessageBeingConsumed(messageId)) {
            if (messageQueueIdempotentHandler.isAccomplish(messageId)) {
                // 已经处理完并落了库：直接丢弃，避免重复计数
                return HandleResult.ALREADY_DONE;
            }
            // 值为 "0"：上一轮没有正常结束。这里不能删幂等键（可能真的是别人在处理），
            // 也不抛异常（抛出去也不会自动重投）—— 把消息留给回收任务
            log.warn("消息仍在处理中或上一轮未正常结束，暂不重复处理。messageId: {}", messageId);
            return HandleResult.IN_PROGRESS;
        }
        try {
            ShortLinkStatsRecordDTO statsRecord = JSON.parseObject(statsRecordJson, ShortLinkStatsRecordDTO.class);
            actualSaveShortLinkStats(statsRecord);
        } catch (Throwable ex) {
            // 删掉幂等标识，让这条消息能被重新处理；
            // 消息本身不在这里删除，交给回收任务重投
            messageQueueIdempotentHandler.delMessageProcessed(messageId);
            log.error("记录短链接监控消费异常，messageId: {}", messageId, ex);
            return HandleResult.FAILED;
        }
        messageQueueIdempotentHandler.setAccomplish(messageId);
        return HandleResult.SUCCESS;
    }

    /**
     * 消息处理结果
     */
    public enum HandleResult {

        /**
         * 本次处理成功
         */
        SUCCESS,

        /**
         * 之前已经处理成功过（重复投递）
         */
        ALREADY_DONE,

        /**
         * 幂等键停留在「处理中」，本轮跳过
         */
        IN_PROGRESS,

        /**
         * 本次处理失败
         */
        FAILED
    }

    /**
     * 把一次访问的统计信息落到 8 张监控表，并自增短链接的总计字段
     *
     * @param statsRecord 本次访问的统计信息
     */
    public void actualSaveShortLinkStats(ShortLinkStatsRecordDTO statsRecord) {
        String fullShortUrl = statsRecord.getFullShortUrl();
        // 加「分组变更」读锁：短链接换分组时，t_link_goto 里的 gid 会被改写成新值
        // （见 ShortLinkServiceImpl.updateShortLink 加的写锁）。
        // 统计写 t_link 必须带上 gid 作为分片键，如果读到的 gid 与写 t_link 时的
        // 新 gid 不一致，这条更新就会路由到错误的物理表、静默丢数据。
        // 读锁与写锁互斥，因此这里读到的 gid 在整个写入过程中不会被换掉。
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(String.format(LOCK_GID_UPDATE_KEY, fullShortUrl));
        RLock rLock = readWriteLock.readLock();
        rLock.lock();
        try {
            LambdaQueryWrapper<ShortLinkGotoDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                    .eq(ShortLinkGotoDO::getFullShortUrl, fullShortUrl);
            ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(queryWrapper);
            if (shortLinkGotoDO == null) {
                // 跳转表里没有这条记录，说明这个短链接从未建链成功，或数据被人工清理过。
                // 统计是旁路，这种情况直接放弃这条消息：抛异常只会让它无限重试并刷满错误日志，
                // 而重试多少次都不可能查出一条不存在的记录
                log.warn("短链接跳转记录不存在，跳过本次统计，fullShortUrl: {}", fullShortUrl);
                return;
            }
            String gid = shortLinkGotoDO.getGid();
            Date currentDate = statsRecord.getCurrentDate();
            int hour = DateUtil.hour(currentDate, true);
            Week week = DateUtil.dayOfWeekEnum(currentDate);
            int weekValue = week.getIso8601Value();
            LinkAccessStatsDO linkAccessStatsDO = LinkAccessStatsDO.builder()
                    .pv(1)
                    .uv(Boolean.TRUE.equals(statsRecord.getUvFirstFlag()) ? 1 : 0)
                    .uip(Boolean.TRUE.equals(statsRecord.getUipFirstFlag()) ? 1 : 0)
                    .hour(hour)
                    .weekday(weekValue)
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .build();
            linkAccessStatsMapper.shortLinkStats(linkAccessStatsDO);
            LinkLocaleStatsDO linkLocaleStatsDO = buildLocaleStats(fullShortUrl, currentDate, statsRecord.getRemoteAddr());
            linkLocaleStatsMapper.shortLinkLocaleState(linkLocaleStatsDO);
            LinkOsStatsDO linkOsStatsDO = LinkOsStatsDO.builder()
                    .os(statsRecord.getOs())
                    .cnt(1)
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .build();
            linkOsStatsMapper.shortLinkOsState(linkOsStatsDO);
            LinkBrowserStatsDO linkBrowserStatsDO = LinkBrowserStatsDO.builder()
                    .browser(statsRecord.getBrowser())
                    .cnt(1)
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .build();
            linkBrowserStatsMapper.shortLinkBrowserState(linkBrowserStatsDO);
            LinkDeviceStatsDO linkDeviceStatsDO = LinkDeviceStatsDO.builder()
                    .device(statsRecord.getDevice())
                    .cnt(1)
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .build();
            linkDeviceStatsMapper.shortLinkDeviceState(linkDeviceStatsDO);
            LinkNetworkStatsDO linkNetworkStatsDO = LinkNetworkStatsDO.builder()
                    .network(statsRecord.getNetwork())
                    .cnt(1)
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .build();
            linkNetworkStatsMapper.shortLinkNetworkState(linkNetworkStatsDO);
            LinkAccessLogsDO linkAccessLogsDO = LinkAccessLogsDO.builder()
                    .user(statsRecord.getUv())
                    .ip(statsRecord.getRemoteAddr())
                    .browser(statsRecord.getBrowser())
                    .os(statsRecord.getOs())
                    .network(statsRecord.getNetwork())
                    .device(statsRecord.getDevice())
                    // 明细表里的地区是拼好的整串，与 t_link_locale_stats 的省/市分列不同
                    .locale(StrUtil.join("-", "中国", linkLocaleStatsDO.getProvince(), linkLocaleStatsDO.getCity()))
                    .fullShortUrl(fullShortUrl)
                    .build();
            linkAccessLogsMapper.insert(linkAccessLogsDO);
            shortLinkMapper.incrementStats(gid, fullShortUrl, 1,
                    Boolean.TRUE.equals(statsRecord.getUvFirstFlag()) ? 1 : 0,
                    Boolean.TRUE.equals(statsRecord.getUipFirstFlag()) ? 1 : 0);
            LinkStatsTodayDO linkStatsTodayDO = LinkStatsTodayDO.builder()
                    .todayPv(1)
                    .todayUv(Boolean.TRUE.equals(statsRecord.getUvFirstFlag()) ? 1 : 0)
                    .todayUip(Boolean.TRUE.equals(statsRecord.getUipFirstFlag()) ? 1 : 0)
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .build();
            linkStatsTodayMapper.shortLinkTodayState(linkStatsTodayDO);
        } finally {
            rLock.unlock();
        }
    }

    /**
     * 查询本次访问的地区并组装地区统计实体
     * <p>
     * 与参考实现的三处差别，都是为了「地区这一维度不能因为外部服务而失真」：
     * <ol>
     *   <li><b>key 为空时不发请求</b>：key 没配就没有必要发一次注定失败的调用，
     *       直接落到「未知」</li>
     *   <li><b>加超时并降级</b>：高德是外部服务，挂住是常态。调用失败只把地区降级为
     *       「未知」，其余 7 张表和 t_link 的统计照常写入 —— 一个地区维度失败
     *       不该让整次访问的统计全部丢失</li>
     *   <li><b>省、市、编码各自判空</b>：参考实现只看 province 是不是 "[]"，
     *       然后把这个结论套到 city 和 adcode 上。而高德对内网 IP 返回的是
     *       {@code province: "局域网", city: [], adcode: []} —— province 是有效的，
     *       于是 city/adcode 的字面量 "[]" 就被原样存进了库。
     *       三个字段独立判断，任何一个无效都单独记成「未知」</li>
     * </ol>
     *
     * @param fullShortUrl 完整短链接
     * @param currentDate  访问日期
     * @param remoteAddr   访问者真实 IP
     * @return 地区统计实体
     */
    private LinkLocaleStatsDO buildLocaleStats(String fullShortUrl, Date currentDate, String remoteAddr) {
        String province = UNKNOWN_LOCALE;
        String city = UNKNOWN_LOCALE;
        String adcode = UNKNOWN_LOCALE;
        if (StrUtil.isNotBlank(statsLocaleAmapKey)) {
            try {
                Map<String, Object> localeParamMap = new HashMap<>();
                localeParamMap.put("key", statsLocaleAmapKey);
                localeParamMap.put("ip", remoteAddr);
                String localeResultStr = HttpUtil.get(AMAP_REMOTE_URL, localeParamMap, AMAP_TIME_OUT);
                JSONObject localeResultObj = JSON.parseObject(localeResultStr);
                // infocode = 10000 才代表这次查询成功；key 失效是 10001、QPS 超限是 10021，
                // 这几种情况都只会返回空结果，必须显式判断，否则会把空串当成地区写进库
                if (localeResultObj != null && StrUtil.equals(localeResultObj.getString("infocode"), "10000")) {
                    province = normalizeLocaleField(localeResultObj.getString("province"));
                    city = normalizeLocaleField(localeResultObj.getString("city"));
                    adcode = normalizeLocaleField(localeResultObj.getString("adcode"));
                } else {
                    log.warn("高德逆地理查询未成功，地区降级为未知，ip: {}, 响应: {}", remoteAddr, localeResultStr);
                }
            } catch (Exception ex) {
                log.warn("高德逆地理查询异常，地区降级为未知，ip: {}", remoteAddr, ex);
            }
        }
        return LinkLocaleStatsDO.builder()
                .province(province)
                .city(city)
                .adcode(adcode)
                .cnt(1)
                .fullShortUrl(fullShortUrl)
                .country("中国")
                .date(currentDate)
                .build();
    }

    /**
     * 归一化高德返回的单个地区字段
     * <p>
     * 高德在查不到具体地区时返回的不是 null 也不是空串，而是 JSON 空数组 {@code []}
     * —— 序列化成字符串就是两个字面量字符 "[]"，直接入库会让地区分布里
     * 出现一个叫「[]」的省份。
     *
     * @param fieldValue 高德返回的字段原始值
     * @return 有效值，或「未知」
     */
    private String normalizeLocaleField(String fieldValue) {
        if (StrUtil.isBlank(fieldValue) || StrUtil.equals(fieldValue, "[]")) {
            return UNKNOWN_LOCALE;
        }
        return fieldValue;
    }
}
