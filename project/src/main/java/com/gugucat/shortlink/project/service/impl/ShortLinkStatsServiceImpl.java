package com.gugucat.shortlink.project.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gugucat.shortlink.project.common.biz.user.UserContext;
import com.gugucat.shortlink.project.common.convention.exception.ClientException;
import com.gugucat.shortlink.project.common.convention.exception.ServiceException;
import com.gugucat.shortlink.project.dao.entity.GroupDO;
import com.gugucat.shortlink.project.dao.entity.LinkAccessStatsDO;
import com.gugucat.shortlink.project.dao.entity.LinkDeviceStatsDO;
import com.gugucat.shortlink.project.dao.entity.LinkLocaleStatsDO;
import com.gugucat.shortlink.project.dao.entity.LinkNetworkStatsDO;
import com.gugucat.shortlink.project.dao.mapper.LinkAccessLogsMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkAccessStatsMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkBrowserStatsMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkDeviceStatsMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkGroupMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkLocaleStatsMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkNetworkStatsMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkOsStatsMapper;
import com.gugucat.shortlink.project.dto.req.ShortLinkGroupStatsReqDTO;
import com.gugucat.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkStatsAccessDailyRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkStatsBrowserRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkStatsDeviceRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkStatsLocaleCNRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkStatsNetworkRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkStatsOsRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkStatsRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkStatsTopIpRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkStatsUvRespDTO;
import com.gugucat.shortlink.project.service.ShortLinkStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

/**
 * 短链接监控接口实现层
 * <p>
 * 一次请求会打十余条 GROUP BY 查询。之所以能接受，是因为它们都落在按 full_short_url
 * 索引的表上，且查询区间通常是小时级；一旦要看「全部短链接」的横向对比，
 * 这个思路就撑不住了（那属于分组维度的统计）。
 * <p>
 * <b>所有涉及去重的指标（总计、按天分布、高频 IP、新老访客）都从 t_link_access_logs
 * 现算，不复用 t_link_access_stats 的累加值</b>：累加表的 uv/uip 是按「天 + 小时」
 * 去重的，去重指标不可加，求和会重复计数。只有可加指标（各维度的 pv/cnt）才用累加表。
 * 这样做的一个附带好处是同一个响应里的数字必然自洽 —— 它们来自同一张表。
 */
@Service
@RequiredArgsConstructor
public class ShortLinkStatsServiceImpl implements ShortLinkStatsService {

    private final LinkGroupMapper linkGroupMapper;
    private final LinkAccessStatsMapper linkAccessStatsMapper;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;
    private final LinkOsStatsMapper linkOsStatsMapper;
    private final LinkDeviceStatsMapper linkDeviceStatsMapper;
    private final LinkNetworkStatsMapper linkNetworkStatsMapper;

    /**
     * 结束日期补上当天最后一刻
     * <p>
     * 库里两类时间列混用：{@code t_link_access_stats.date} 是 DATE，
     * 而 {@code t_link_access_logs.create_time} 是 DATETIME。
     * 拿 {@code 'yyyy-MM-dd'} 这种只有日期的字符串去做 BETWEEN 的右边界时，
     * 对 DATE 列是按当天零点比较（当天算在内），对 DATETIME 列也是按当天零点比较
     * —— 于是<b>当天 00:00 之后的访问全部落在区间外</b>。
     * 结果就是按天分布里有当天的数据、而总计 PV/UV/UIP 里没有，两个数对不上
     * （总计查询带 GROUP BY，命中 0 行还会让 mapper 返回 null）。
     * 把右边界补到 {@code 23:59:59} 之后，两类列的比较都变成闭区间。
     */
    private static final String END_OF_DAY_SUFFIX = " 23:59:59";

    /**
     * 日期参数的格式，只接受 yyyy-MM-dd
     * <p>
     * 必须显式校验格式，不能只靠「解析失败就抛异常」兜着：右边界是在原始字符串后面
     * 直接拼 {@code " 23:59:59"} 的，一个本来就不是日期的串（比如已经带了时分秒，
     * 或干脆是乱填的）拼完会变成一个更离谱的值，最后在 SQL 里比较出莫名其妙的结果。
     */
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    @Override
    public ShortLinkStatsRespDTO oneShortLinkStats(ShortLinkStatsReqDTO requestParam) {
        if (StrUtil.isBlank(requestParam.getFullShortUrl()) || requestParam.getEnableStatus() == null) {
            throw new ClientException("完整短链接与启用标识不能为空");
        }
        // 归属校验必须在查询之前：下面的 SQL 只按 gid 过滤，不校验 gid 是否属于当前用户
        checkGroupBelongToUser(requestParam.getGid());
        requestParam.setEndDate(checkAndNormalizeDateRange(requestParam.getGid(), requestParam.getStartDate(), requestParam.getEndDate()));
        List<String> rangeDates = buildRangeDates(requestParam.getStartDate(), requestParam.getEndDate());
        // 按天分布从明细表算，不用 t_link_access_stats：后者的 uv/uip 是按小时去重的，
        // 求和会重复计数（详见 LinkAccessLogsMapper.listDailyStatsByShortLink 的说明）
        List<LinkAccessStatsDO> dailyStatsByShortLink = linkAccessLogsMapper.listDailyStatsByShortLink(requestParam);
        if (CollUtil.isEmpty(dailyStatsByShortLink)) {
            // 区间内没有任何访问。返回 null 而不是一堆 0：
            // 调用方据此可以把「没数据」和「有数据但都是 0」区分开
            return null;
        }
        // 基础访问数据。与按天分布同源（都读明细表），所以两者必然自洽
        LinkAccessStatsDO totals = linkAccessLogsMapper.findPvUvUidStatsByShortLink(requestParam);
        // 访客访问类型详情。固定返回两条，没有数据时是两条 0，而不是空列表
        // —— 前端的新老访客饼图按固定分类渲染
        HashMap<String, Object> findUvTypeByShortLink = linkAccessLogsMapper.findUvTypeCntByShortLink(requestParam);
        return ShortLinkStatsRespDTO.builder()
                .pv(Optional.ofNullable(totals).map(LinkAccessStatsDO::getPv).orElse(0))
                .uv(Optional.ofNullable(totals).map(LinkAccessStatsDO::getUv).orElse(0))
                .uip(Optional.ofNullable(totals).map(LinkAccessStatsDO::getUip).orElse(0))
                .daily(buildDaily(dailyStatsByShortLink, rangeDates))
                .localeCnStats(buildLocaleStats(linkLocaleStatsMapper.listLocaleByShortLink(requestParam)))
                .hourStats(buildHourStats(linkAccessStatsMapper.listHourStatsByShortLink(requestParam)))
                .topIpStats(buildTopIpStats(linkAccessLogsMapper.listTopIpByShortLink(requestParam)))
                .weekdayStats(buildWeekdayStats(linkAccessStatsMapper.listWeekdayStatsByShortLink(requestParam)))
                .browserStats(buildBrowserStats(linkBrowserStatsMapper.listBrowserStatsByShortLink(requestParam)))
                .osStats(buildOsStats(linkOsStatsMapper.listOsStatsByShortLink(requestParam)))
                .uvTypeStats(buildUvTypeStats(findUvTypeByShortLink))
                .deviceStats(buildDeviceStats(linkDeviceStatsMapper.listDeviceStatsByShortLink(requestParam)))
                .networkStats(buildNetworkStats(linkNetworkStatsMapper.listNetworkStatsByShortLink(requestParam)))
                .build();
    }

    @Override
    public ShortLinkStatsRespDTO groupShortLinkStats(ShortLinkGroupStatsReqDTO requestParam) {
        checkGroupBelongToUser(requestParam.getGid());
        requestParam.setEndDate(checkAndNormalizeDateRange(requestParam.getGid(), requestParam.getStartDate(), requestParam.getEndDate()));
        List<String> rangeDates = buildRangeDates(requestParam.getStartDate(), requestParam.getEndDate());
        List<LinkAccessStatsDO> dailyStatsByGroup = linkAccessLogsMapper.listDailyStatsByGroup(requestParam);
        if (CollUtil.isEmpty(dailyStatsByGroup)) {
            return null;
        }
        LinkAccessStatsDO totals = linkAccessLogsMapper.findPvUvUidStatsByGroup(requestParam);
        return ShortLinkStatsRespDTO.builder()
                .pv(Optional.ofNullable(totals).map(LinkAccessStatsDO::getPv).orElse(0))
                .uv(Optional.ofNullable(totals).map(LinkAccessStatsDO::getUv).orElse(0))
                .uip(Optional.ofNullable(totals).map(LinkAccessStatsDO::getUip).orElse(0))
                .daily(buildDaily(dailyStatsByGroup, rangeDates))
                .localeCnStats(buildLocaleStats(linkLocaleStatsMapper.listLocaleByGroup(requestParam)))
                .hourStats(buildHourStats(linkAccessStatsMapper.listHourStatsByGroup(requestParam)))
                .topIpStats(buildTopIpStats(linkAccessLogsMapper.listTopIpByGroup(requestParam)))
                .weekdayStats(buildWeekdayStats(linkAccessStatsMapper.listWeekdayStatsByGroup(requestParam)))
                .browserStats(buildBrowserStats(linkBrowserStatsMapper.listBrowserStatsByGroup(requestParam)))
                .osStats(buildOsStats(linkOsStatsMapper.listOsStatsByGroup(requestParam)))
                .deviceStats(buildDeviceStats(linkDeviceStatsMapper.listDeviceStatsByGroup(requestParam)))
                .networkStats(buildNetworkStats(linkNetworkStatsMapper.listNetworkStatsByGroup(requestParam)))
                // 刻意不返回 uvTypeStats：分组维度的新老访客需要「按访客在整个分组上的
                // 全部历史」去判断，与单链接的口径不同，参考实现也没有实现
                .build();
    }

    /**
     * 校验当前用户是否拥有该分组
     * <p>
     * 监控查询的所有条件里只有 gid，没有用户。不做这一步的话，
     * 任何登录用户只要猜到 gid 就能看到别人短链接的完整监控数据。
     *
     * @param gid 分组标识
     */
    private void checkGroupBelongToUser(String gid) {
        if (StrUtil.isBlank(gid)) {
            // 这一条必须放在最前面：下面那个查询用的是 `gid = 参数值`，
            // 参数为 null 时永远匹配不上任何一行，于是「没传分组标识」会被报成
            // 「用户信息与分组标识不匹配」—— 调用方会以为自己传错了分组，
            // 而实际上是少传了参数
            throw new ClientException("分组标识不能为空");
        }
        String username = Optional.ofNullable(UserContext.getUsername())
                // 中台只被后管调用，这里拿不到用户名说明请求没经过后管
                //（少了 username 请求头），属于调用方用错了接口
                .orElseThrow(() -> new ServiceException("用户未登录"));
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getGid, gid)
                .eq(GroupDO::getUsername, username);
        List<GroupDO> groupDOList = linkGroupMapper.selectList(queryWrapper);
        if (CollUtil.isEmpty(groupDOList)) {
            throw new ServiceException("用户信息与分组标识不匹配");
        }
    }

    /**
     * 校验日期区间参数并归一化右边界
     * <p>
     * 这些参数缺失时 SQL 并不会报错，只会查不到任何数据 ——
     * 调用方拿到一个空的监控面板，却不知道是「这个短链接没人访问」还是「自己少传了参数」。
     *
     * @param gid       分组标识
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 补到当天最后一刻之后的结束日期
     */
    private String checkAndNormalizeDateRange(String gid, String startDate, String endDate) {
        if (StrUtil.isBlank(gid) || StrUtil.isBlank(startDate) || StrUtil.isBlank(endDate)) {
            throw new ClientException("分组标识、开始日期与结束日期不能为空");
        }
        if (!DATE_PATTERN.matcher(startDate).matches() || !DATE_PATTERN.matcher(endDate).matches()) {
            throw new ClientException("日期格式不正确，应为 yyyy-MM-dd");
        }
        if (startDate.compareTo(endDate) > 0) {
            // yyyy-MM-dd 是定长格式，字典序即时间序，不需要真的解析成日期去比
            throw new ClientException("开始日期不能晚于结束日期");
        }
        // 右边界补到当天最后一刻，否则当天有访问的明细数据会被排除在区间外
        return endDate + END_OF_DAY_SUFFIX;
    }

    /**
     * 把日期区间展开成「区间内的每一天」
     *
     * @param startDate 开始日期
     * @param endDate   结束日期（已归一化，含时分秒）
     * @return 形如 2026-10-16 的日期列表，含首尾
     */
    private List<String> buildRangeDates(String startDate, String endDate) {
        return DateUtil.rangeToList(DateUtil.parse(startDate), DateUtil.parse(endDate), DateField.DAY_OF_MONTH)
                .stream()
                .map(DateUtil::formatDate)
                .toList();
    }

    /**
     * 按天铺开基础访问数据
     * <p>
     * 区间内每一天都要有一条，查不到的那天补 0 —— 前端拿到的日期轴因此是连续的，
     * 不需要自己补空。
     *
     * @param listStats  按天汇总的访问数据
     * @param rangeDates 区间内的每一天
     * @return 每天一条的响应列表
     */
    private List<ShortLinkStatsAccessDailyRespDTO> buildDaily(List<LinkAccessStatsDO> listStats, List<String> rangeDates) {
        List<ShortLinkStatsAccessDailyRespDTO> daily = new ArrayList<>(rangeDates.size());
        rangeDates.forEach(each -> {
            Optional<LinkAccessStatsDO> matched = listStats.stream()
                    .filter(item -> Objects.equals(each, DateUtil.formatDate(item.getDate())))
                    .findFirst();
            daily.add(matched
                    .map(item -> ShortLinkStatsAccessDailyRespDTO.builder()
                            .date(each)
                            .pv(item.getPv())
                            .uv(item.getUv())
                            .uip(item.getUip())
                            .build())
                    .orElseGet(() -> ShortLinkStatsAccessDailyRespDTO.builder()
                            .date(each)
                            .pv(0)
                            .uv(0)
                            .uip(0)
                            .build()));
        });
        return daily;
    }

    /**
     * 把「小时 → 访问量」铺成固定 24 个元素，下标即小时
     *
     * @param listHourStats 有访问的小时及其 pv
     * @return 24 个元素，没有访问的小时为 0
     */
    private List<Integer> buildHourStats(List<LinkAccessStatsDO> listHourStats) {
        List<Integer> hourStats = new ArrayList<>(24);
        for (int i = 0; i < 24; i++) {
            hourStats.add(matchPv(listHourStats, LinkAccessStatsDO::getHour, i));
        }
        return hourStats;
    }

    /**
     * 把「星期 → 访问量」铺成固定 7 个元素，下标 0 对应周一
     *
     * @param listWeekdayStats 有访问的星期及其 pv（weekday 取值 1~7）
     * @return 7 个元素，没有访问的星期为 0
     */
    private List<Integer> buildWeekdayStats(List<LinkAccessStatsDO> listWeekdayStats) {
        List<Integer> weekdayStats = new ArrayList<>(7);
        for (int i = 1; i < 8; i++) {
            weekdayStats.add(matchPv(listWeekdayStats, LinkAccessStatsDO::getWeekday, i));
        }
        return weekdayStats;
    }

    /**
     * 从按维度汇总的结果里取出某一个维度的访问量
     *
     * @param listStats   按维度汇总的访问数据
     * @param dimension   取哪个维度字段（hour / weekday）
     * @param value       维度的取值
     * @return 对应的 pv，取不到时为 0
     */
    private int matchPv(List<LinkAccessStatsDO> listStats, Function<LinkAccessStatsDO, Integer> dimension, int value) {
        return listStats.stream()
                .filter(each -> Objects.equals(dimension.apply(each), value))
                .findFirst()
                .map(LinkAccessStatsDO::getPv)
                .orElse(0);
    }

    /**
     * 高频访问 IP
     *
     * @param listTopIp 每项含 ip 与 count
     * @return 最多 5 条
     */
    private List<ShortLinkStatsTopIpRespDTO> buildTopIpStats(List<HashMap<String, Object>> listTopIp) {
        List<ShortLinkStatsTopIpRespDTO> topIpStats = new ArrayList<>(listTopIp.size());
        listTopIp.forEach(each -> topIpStats.add(ShortLinkStatsTopIpRespDTO.builder()
                .ip(Objects.toString(each.get("ip"), "未知"))
                .cnt(parseCount(each.get("count")))
                .build()));
        return topIpStats;
    }

    /**
     * 地区访问详情（仅国内，按省级聚合）
     *
     * @param listLocale 每省一条的 cnt
     * @return 带占比的地区分布
     */
    private List<ShortLinkStatsLocaleCNRespDTO> buildLocaleStats(List<LinkLocaleStatsDO> listLocale) {
        return buildRatioStats(listLocale, LinkLocaleStatsDO::getCnt, LinkLocaleStatsDO::getProvince,
                (cnt, name, ratio) -> ShortLinkStatsLocaleCNRespDTO.builder()
                        .cnt(cnt)
                        .locale(name)
                        .ratio(ratio)
                        .build());
    }

    /**
     * 浏览器访问详情
     *
     * @param listBrowser 每项含 browser 与 count
     * @return 带占比的浏览器分布
     */
    private List<ShortLinkStatsBrowserRespDTO> buildBrowserStats(List<HashMap<String, Object>> listBrowser) {
        return buildRatioStats(listBrowser, each -> parseCount(each.get("count")), each -> Objects.toString(each.get("browser"), "未知"),
                (cnt, name, ratio) -> ShortLinkStatsBrowserRespDTO.builder()
                        .cnt(cnt)
                        .browser(name)
                        .ratio(ratio)
                        .build());
    }

    /**
     * 操作系统访问详情
     *
     * @param listOs 每项含 os 与 count
     * @return 带占比的操作系统分布
     */
    private List<ShortLinkStatsOsRespDTO> buildOsStats(List<HashMap<String, Object>> listOs) {
        return buildRatioStats(listOs, each -> parseCount(each.get("count")), each -> Objects.toString(each.get("os"), "未知"),
                (cnt, name, ratio) -> ShortLinkStatsOsRespDTO.builder()
                        .cnt(cnt)
                        .os(name)
                        .ratio(ratio)
                        .build());
    }

    /**
     * 访问设备类型详情
     *
     * @param listDevice 每种设备一条的 cnt
     * @return 带占比的设备分布
     */
    private List<ShortLinkStatsDeviceRespDTO> buildDeviceStats(List<LinkDeviceStatsDO> listDevice) {
        return buildRatioStats(listDevice, LinkDeviceStatsDO::getCnt, LinkDeviceStatsDO::getDevice,
                (cnt, name, ratio) -> ShortLinkStatsDeviceRespDTO.builder()
                        .cnt(cnt)
                        .device(name)
                        .ratio(ratio)
                        .build());
    }

    /**
     * 访问网络类型详情
     *
     * @param listNetwork 每种网络一条的 cnt
     * @return 带占比的网络分布
     */
    private List<ShortLinkStatsNetworkRespDTO> buildNetworkStats(List<LinkNetworkStatsDO> listNetwork) {
        return buildRatioStats(listNetwork, LinkNetworkStatsDO::getCnt, LinkNetworkStatsDO::getNetwork,
                (cnt, name, ratio) -> ShortLinkStatsNetworkRespDTO.builder()
                        .cnt(cnt)
                        .network(name)
                        .ratio(ratio)
                        .build());
    }

    /**
     * 访客访问类型详情
     * <p>
     * 判断口径是「这个访客在全部历史里是不是只出现过一天」：
     * 只出现过一天算新访客，跨天出现过算老访客。
     *
     * @param uvTypeCount 含 oldUserCnt 与 newUserCnt，查询无结果时可能为 null
     * @return 固定两条：newUser 与 oldUser，没有数据时计数都是 0
     */
    private List<ShortLinkStatsUvRespDTO> buildUvTypeStats(HashMap<String, Object> uvTypeCount) {
        int oldUserCnt = Optional.ofNullable(uvTypeCount).map(each -> parseCount(each.get("oldUserCnt"))).orElse(0);
        int newUserCnt = Optional.ofNullable(uvTypeCount).map(each -> parseCount(each.get("newUserCnt"))).orElse(0);
        int uvSum = oldUserCnt + newUserCnt;
        List<ShortLinkStatsUvRespDTO> uvTypeStats = new ArrayList<>(2);
        uvTypeStats.add(ShortLinkStatsUvRespDTO.builder()
                .uvType("newUser")
                .cnt(newUserCnt)
                .ratio(calcRatio(newUserCnt, uvSum))
                .build());
        uvTypeStats.add(ShortLinkStatsUvRespDTO.builder()
                .uvType("oldUser")
                .cnt(oldUserCnt)
                .ratio(calcRatio(oldUserCnt, uvSum))
                .build());
        return uvTypeStats;
    }

    /**
     * 把「维度名 + 计数」的汇总结果换算成带占比的响应列表
     * <p>
     * 单链接与分组两个查询入口各有 5 个这种维度的分布（地区/浏览器/操作系统/设备/网络），
     * 换算是完全一样的：求和 → 各项除以合计 → 保留两位。
     * 抽成一个方法是因为两个入口各写 5 遍会让「占比怎么算」这件事散在 10 个地方。
     * <p>
     * 占比的分母是**各项的合计**，不是总 PV —— 两者在写库不完整时可能不一致
     * （见开发文档「已知问题」）。
     *
     * @param rawStats  按维度汇总的原始数据
     * @param cntGetter 从一条原始数据里取计数
     * @param nameGetter 从一条原始数据里取维度名
     * @param builder   把 (计数, 维度名, 占比) 组装成响应对象
     * @param <S>       原始数据类型
     * @param <T>       响应类型
     * @return 带占比的响应列表
     */
    private <S, T> List<T> buildRatioStats(List<S> rawStats,
                                           ToIntFunction<S> cntGetter,
                                           Function<S, String> nameGetter,
                                           RatioStatsBuilder<T> builder) {
        int sum = rawStats.stream().mapToInt(cntGetter).sum();
        List<T> result = new ArrayList<>(rawStats.size());
        for (S each : rawStats) {
            int cnt = cntGetter.applyAsInt(each);
            result.add(builder.build(cnt, nameGetter.apply(each), calcRatio(cnt, sum)));
        }
        return result;
    }

    /**
     * 计算占比
     *
     * @param cnt 该项的计数
     * @param sum 各项的合计
     * @return 0~1 之间保留两位小数的占比；合计为 0 时返回 0
     */
    private double calcRatio(int cnt, int sum) {
        if (sum == 0) {
            // 参考实现这里会算出 0/0 = NaN，再被 Math.round(NaN) 兜成 0。
            // 结果虽然一样，但显式短路让意图清楚，也免得将来换了取整方式就漏出 NaN
            return 0.0;
        }
        return Math.round((double) cnt / sum * 100.0) / 100.0;
    }

    /**
     * 把 SQL 返回的计数转成 int
     * <p>
     * 计数走 HashMap 返回时是 Object（MySQL 的 COUNT/SUM 可能是 Long、BigDecimal 或 null），
     * 直接 toString 再 parse 是最省事也最不容易出错的做法。
     *
     * @param value SQL 返回的计数
     * @return 计数，null 或非法值时返回 0
     */
    private int parseCount(Object value) {
        return Integer.parseInt(Objects.toString(value, "0"));
    }

    /**
     * 把 (计数, 维度名, 占比) 组装成某个具体的响应对象
     * <p>
     * 五个维度的响应 DTO 之间没有继承关系，只能用函数式接口把「怎么组装」交给调用方。
     *
     * @param <T> 响应类型
     */
    @FunctionalInterface
    private interface RatioStatsBuilder<T> {

        /**
         * 组装一条响应
         *
         * @param cnt   该项计数
         * @param name  维度名
         * @param ratio 占比
         * @return 响应对象
         */
        T build(int cnt, String name, double ratio);
    }
}
