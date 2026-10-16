package com.gugucat.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gugucat.shortlink.project.dao.entity.LinkOsStatsDO;
import com.gugucat.shortlink.project.dto.req.ShortLinkGroupStatsReqDTO;
import com.gugucat.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.HashMap;
import java.util.List;

/**
 * 操作系统统计访问持久层
 */
public interface LinkOsStatsMapper extends BaseMapper<LinkOsStatsDO> {

    /**
     * 累加操作系统访问监控数据
     * <p>
     * 唯一键是 (full_short_url, date, os)，重复插入时累加 cnt。
     *
     * @param linkOsStatsDO 本次访问的操作系统统计数据
     */
    @Insert("INSERT INTO " +
            "t_link_os_stats (full_short_url, date, cnt, os, create_time, update_time, del_flag) " +
            "VALUES( #{linkOsStats.fullShortUrl}, #{linkOsStats.date}, #{linkOsStats.cnt}, #{linkOsStats.os}, NOW(), NOW(), 0) " +
            "ON DUPLICATE KEY UPDATE cnt = cnt + #{linkOsStats.cnt};")
    void shortLinkOsState(@Param("linkOsStats") LinkOsStatsDO linkOsStatsDO);

    /**
     * 按操作系统汇总指定日期内的访问量
     *
     * @param requestParam 监控查询参数
     * @return 每项含 os 与 count 两个键
     */
    @Select("""
            SELECT
                tlos.os,
                SUM(tlos.cnt) AS count
            FROM
                t_link tl INNER JOIN
                t_link_os_stats tlos ON tl.full_short_url = tlos.full_short_url
            WHERE
                tlos.full_short_url = #{param.fullShortUrl}
                AND tl.gid = #{param.gid}
                AND tl.del_flag = '0'
                AND tl.enable_status = #{param.enableStatus}
                AND tlos.date BETWEEN #{param.startDate} and #{param.endDate}
            GROUP BY
                tlos.full_short_url, tl.gid, tlos.os;
            """)
    List<HashMap<String, Object>> listOsStatsByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /**
     * 按操作系统汇总指定分组在指定日期内的访问量
     *
     * @param requestParam 分组监控查询参数
     * @return 每项含 os 与 count 两个键
     */
    @Select("""
            SELECT
                tlos.os,
                SUM(tlos.cnt) AS count
            FROM
                t_link tl INNER JOIN
                t_link_os_stats tlos ON tl.full_short_url = tlos.full_short_url
            WHERE
                tl.gid = #{param.gid}
                AND tl.del_flag = '0'
                AND tl.enable_status = '0'
                AND tlos.date BETWEEN #{param.startDate} and #{param.endDate}
            GROUP BY
                tl.gid, tlos.os;
            """)
    List<HashMap<String, Object>> listOsStatsByGroup(@Param("param") ShortLinkGroupStatsReqDTO requestParam);
}
