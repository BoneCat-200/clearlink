package com.gugucat.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gugucat.shortlink.project.dao.entity.LinkAccessStatsDO;
import com.gugucat.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 短链接基础访问监控持久层
 * <p>
 * 读取侧的查询统一用文本块写：这些是十几行的聚合语句，拼字符串既难读也没法直接
 * 复制到客户端里排查，而它们恰恰是最需要能单独跑一遍的那类 SQL。
 * 写侧的单条 upsert 仍是紧凑的字符串拼接（见下方 {@code shortLinkStats}）。
 */
public interface LinkAccessStatsMapper extends BaseMapper<LinkAccessStatsDO> {

    /**
     * 累加基础访问监控数据
     * <p>
     * 表上的唯一键是 (full_short_url, date, hour)，重复插入时累加 pv/uv/uip，
     * 所以同一短链接同一天同一小时只会有一行。
     *
     * @param linkAccessStatsDO 本次访问的基础统计数据
     */
    @Insert("INSERT INTO " +
            "t_link_access_stats (full_short_url, date, pv, uv, uip, hour, weekday, create_time, update_time, del_flag) " +
            "VALUES( #{linkAccessStats.fullShortUrl}, #{linkAccessStats.date}, #{linkAccessStats.pv}, #{linkAccessStats.uv}, #{linkAccessStats.uip}, #{linkAccessStats.hour}, #{linkAccessStats.weekday}, NOW(), NOW(), 0) " +
            "ON DUPLICATE KEY UPDATE pv = pv + #{linkAccessStats.pv}, uv = uv + #{linkAccessStats.uv}, uip = uip + #{linkAccessStats.uip};")
    void shortLinkStats(@Param("linkAccessStats") LinkAccessStatsDO linkAccessStatsDO);

    /**
     * 按天汇总指定日期内的基础监控数据
     * <p>
     * 每个自然日每个小时是一行，所以这里要按 date 再 SUM 一次。
     *
     * @param requestParam 监控查询参数（含 gid，用于 t_link 分片路由）
     * @return 每天一条的 pv/uv/uip
     */
    @Select("""
            SELECT
                tlas.date,
                SUM(tlas.pv) AS pv,
                SUM(tlas.uv) AS uv,
                SUM(tlas.uip) AS uip
            FROM
                t_link tl INNER JOIN
                t_link_access_stats tlas ON tl.full_short_url = tlas.full_short_url
            WHERE
                tlas.full_short_url = #{param.fullShortUrl}
                AND tl.gid = #{param.gid}
                AND tl.del_flag = '0'
                AND tl.enable_status = #{param.enableStatus}
                AND tlas.date BETWEEN #{param.startDate} and #{param.endDate}
            GROUP BY
                tlas.full_short_url, tl.gid, tlas.date;
            """)
    List<LinkAccessStatsDO> listStatsByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /**
     * 按小时汇总指定日期内的访问量
     *
     * @param requestParam 监控查询参数
     * @return 有访问的小时及其 pv（没有访问的小时不会出现在结果里，由调用方补 0）
     */
    @Select("""
            SELECT
                tlas.hour,
                SUM(tlas.pv) AS pv
            FROM
                t_link tl INNER JOIN
                t_link_access_stats tlas ON tl.full_short_url = tlas.full_short_url
            WHERE
                tlas.full_short_url = #{param.fullShortUrl}
                AND tl.gid = #{param.gid}
                AND tl.del_flag = '0'
                AND tl.enable_status = #{param.enableStatus}
                AND tlas.date BETWEEN #{param.startDate} and #{param.endDate}
            GROUP BY
                tlas.full_short_url, tl.gid, tlas.hour;
            """)
    List<LinkAccessStatsDO> listHourStatsByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /**
     * 按星期汇总指定日期内的访问量
     *
     * @param requestParam 监控查询参数
     * @return 有访问的星期及其 pv，weekday 取值 1~7（ISO 8601，周一为 1）
     */
    @Select("""
            SELECT
                tlas.weekday,
                SUM(tlas.pv) AS pv
            FROM
                t_link tl INNER JOIN
                t_link_access_stats tlas ON tl.full_short_url = tlas.full_short_url
            WHERE
                tlas.full_short_url = #{param.fullShortUrl}
                AND tl.gid = #{param.gid}
                AND tl.del_flag = '0'
                AND tl.enable_status = #{param.enableStatus}
                AND tlas.date BETWEEN #{param.startDate} and #{param.endDate}
            GROUP BY
                tlas.full_short_url, tl.gid, tlas.weekday;
            """)
    List<LinkAccessStatsDO> listWeekdayStatsByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);
}
