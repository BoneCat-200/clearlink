package com.gugucat.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gugucat.shortlink.project.dao.entity.ShortLinkDO;
import com.gugucat.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.gugucat.shortlink.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import org.apache.ibatis.annotations.Param;

/**
 * 短链接持久层
 */
public interface ShortLinkMapper extends BaseMapper<ShortLinkDO> {

    /**
     * 短链接访问统计自增
     * <p>
     * gid 是 t_link 的分片键，必须带上这个条件，ShardingSphere 才能把更新路由到单张物理表。
     *
     * @param gid          分组标识（分片键）
     * @param fullShortUrl 完整短链接
     * @param totalPv      本次要累加的 PV
     * @param totalUv      本次要累加的 UV
     * @param totalUip     本次要累加的 UIP
     */
    void incrementStats(@Param("gid") String gid,
                        @Param("fullShortUrl") String fullShortUrl,
                        @Param("totalPv") Integer totalPv,
                        @Param("totalUv") Integer totalUv,
                        @Param("totalUip") Integer totalUip);

    /**
     * 分页查询短链接
     *
     * @param requestParam 分页查询参数（含 gid、orderTag 与分页信息）
     * @return 短链接分页结果
     */
    IPage<ShortLinkDO> pageLink(ShortLinkPageReqDTO requestParam);

    /**
     * 分页查询回收站短链接
     *
     * @param requestParam 分页查询参数（含 gidList 与分页信息）
     * @return 回收站短链接分页结果
     */
    IPage<ShortLinkDO> pageRecycleBinLink(ShortLinkRecycleBinPageReqDTO requestParam);
}
