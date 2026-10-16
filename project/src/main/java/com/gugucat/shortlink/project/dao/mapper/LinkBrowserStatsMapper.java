package com.gugucat.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gugucat.shortlink.project.dao.entity.LinkBrowserStatsDO;
import com.gugucat.shortlink.project.dto.req.ShortLinkGroupStatsReqDTO;
import com.gugucat.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.HashMap;
import java.util.List;

/**
 * 浏览器统计访问持久层
 */
public interface LinkBrowserStatsMapper extends BaseMapper<LinkBrowserStatsDO> {

    /**
     * 累加浏览器访问监控数据
     * <p>
     * 唯一键是 (full_short_url, date, browser)，重复插入时累加 cnt。
     *
     * @param linkBrowserStatsDO 本次访问的浏览器统计数据
     */
    @Insert("INSERT INTO " +
            "t_link_browser_stats (full_short_url, date, cnt, browser, create_time, update_time, del_flag) " +
            "VALUES( #{linkBrowserStats.fullShortUrl}, #{linkBrowserStats.date}, #{linkBrowserStats.cnt}, #{linkBrowserStats.browser}, NOW(), NOW(), 0) " +
            "ON DUPLICATE KEY UPDATE cnt = cnt + #{linkBrowserStats.cnt};")
    void shortLinkBrowserState(@Param("linkBrowserStats") LinkBrowserStatsDO linkBrowserStatsDO);

    /**
     * 按浏览器汇总指定日期内的访问量
     *
     * @param requestParam 监控查询参数
     * @return 每项含 browser 与 count 两个键
     */
    @Select("""
            SELECT
                tlbs.browser,
                SUM(tlbs.cnt) AS count
            FROM
                t_link tl INNER JOIN
                t_link_browser_stats tlbs ON tl.full_short_url = tlbs.full_short_url
            WHERE
                tlbs.full_short_url = #{param.fullShortUrl}
                AND tl.gid = #{param.gid}
                AND tl.del_flag = '0'
                AND tl.enable_status = #{param.enableStatus}
                AND tlbs.date BETWEEN #{param.startDate} and #{param.endDate}
            GROUP BY
                tlbs.full_short_url, tl.gid, tlbs.browser;
            """)
    List<HashMap<String, Object>> listBrowserStatsByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /**
     * 按浏览器汇总指定分组在指定日期内的访问量
     *
     * @param requestParam 分组监控查询参数
     * @return 每项含 browser 与 count 两个键
     */
    @Select("""
            SELECT
                tlbs.browser,
                SUM(tlbs.cnt) AS count
            FROM
                t_link tl INNER JOIN
                t_link_browser_stats tlbs ON tl.full_short_url = tlbs.full_short_url
            WHERE
                tl.gid = #{param.gid}
                AND tl.del_flag = '0'
                AND tl.enable_status = '0'
                AND tlbs.date BETWEEN #{param.startDate} and #{param.endDate}
            GROUP BY
                tl.gid, tlbs.browser;
            """)
    List<HashMap<String, Object>> listBrowserStatsByGroup(@Param("param") ShortLinkGroupStatsReqDTO requestParam);
}
