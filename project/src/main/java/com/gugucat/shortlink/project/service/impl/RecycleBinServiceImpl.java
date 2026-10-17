package com.gugucat.shortlink.project.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gugucat.shortlink.project.common.biz.user.UserContext;
import com.gugucat.shortlink.project.common.constant.RedisKeyConstant;
import com.gugucat.shortlink.project.common.convention.exception.ClientException;
import com.gugucat.shortlink.project.dao.entity.GroupDO;
import com.gugucat.shortlink.project.dao.entity.ShortLinkDO;
import com.gugucat.shortlink.project.dao.mapper.LinkGroupMapper;
import com.gugucat.shortlink.project.dao.mapper.ShortLinkMapper;
import com.gugucat.shortlink.project.dto.req.RecycleBinRecoverReqDTO;
import com.gugucat.shortlink.project.dto.req.RecycleBinRemoveReqDTO;
import com.gugucat.shortlink.project.dto.req.RecycleBinSaveReqDTO;
import com.gugucat.shortlink.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.gugucat.shortlink.project.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 回收站管理接口实现层
 */
@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements RecycleBinService {

    private final StringRedisTemplate stringRedisTemplate;
    private final LinkGroupMapper linkGroupMapper;

    @Override
    public void saveRecycleBin(RecycleBinSaveReqDTO requestParam) {
        // gid 是 t_link 的分片键，缺了它 ShardingSphere 无法路由
        if (StrUtil.isBlank(requestParam.getGid()) || StrUtil.isBlank(requestParam.getFullShortUrl())) {
            throw new ClientException("完整短链接与分组标识不能为空");
        }
        // 回收站操作同样按 gid 定位记录，必须先确认这个分组属于当前用户，
        // 否则知道 fullShortUrl 就能把别人的链接移进回收站
        checkGroupBelongToUser(requestParam.getGid());
        // 「在不在回收站」用的是 enable_status，不是 del_flag：
        // del_flag 留给「彻底删除」，两者的区别是回收站里的记录还能恢复
        LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getEnableStatus, 0)
                .eq(ShortLinkDO::getDelTime, 0L)
                .eq(ShortLinkDO::getDelFlag, 0);
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .enableStatus(1)
                .build();
        if (baseMapper.update(shortLinkDO, updateWrapper) < 1) {
            // 影响行数为 0 时若静默返回成功，前端会以为操作生效了、但列表毫无变化
            throw new ClientException("短链接不存在或已在回收站中");
        }
        // 移入回收站后这条短链接就不能再跳转了，缓存必须立刻失效，
        // 否则用户点进去还能访问一个「已删除」的链接（缓存的 TTL 可能长达 30 天）
        stringRedisTemplate.delete(String.format(RedisKeyConstant.GOTO_SHORT_LINK_KEY, requestParam.getFullShortUrl()));
    }

    @Override
    public void recoverRecycleBin(RecycleBinRecoverReqDTO requestParam) {
        if (StrUtil.isBlank(requestParam.getGid()) || StrUtil.isBlank(requestParam.getFullShortUrl())) {
            throw new ClientException("完整短链接与分组标识不能为空");
        }
        checkGroupBelongToUser(requestParam.getGid());
        LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getEnableStatus, 1)     // 只有在回收站里的才能被恢复
                .eq(ShortLinkDO::getDelTime, 0L)
                .eq(ShortLinkDO::getDelFlag, 0);
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .enableStatus(0)
                .build();
        if (baseMapper.update(shortLinkDO, updateWrapper) < 1) {
            throw new ClientException("短链接不存在或不在回收站中");
        }
        // 必须删掉空值缓存：这条短链接在回收站期间如果被访问过，跳转链路会写下
        // 一个 30 分钟的「不存在」标记；不删的话，恢复之后它最多还有 30 分钟跳不了
        stringRedisTemplate.delete(String.format(RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY, requestParam.getFullShortUrl()));
    }

    @Override
    public void removeRecycleBin(RecycleBinRemoveReqDTO requestParam) {
        if (StrUtil.isBlank(requestParam.getGid()) || StrUtil.isBlank(requestParam.getFullShortUrl())) {
            throw new ClientException("完整短链接与分组标识不能为空");
        }
        checkGroupBelongToUser(requestParam.getGid());
        // 「回收站移除」也是逻辑删除，不是真删：del_flag 置 1、del_time 记下时间戳。
        // 数据留在库里可追溯，也不去动统计表（那些表按 full_short_url 关联，没有外键，
        // 真删的话漏清理一张就会留下孤儿数据）
        LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getEnableStatus, 1)     // 只有在回收站里的才能被移除
                .eq(ShortLinkDO::getDelTime, 0L)
                .eq(ShortLinkDO::getDelFlag, 0);
        ShortLinkDO delShortLinkDO = ShortLinkDO.builder()
                .delTime(System.currentTimeMillis())
                .build();
        delShortLinkDO.setDelFlag(1);   // delFlag 在父类 BaseDO 上，Lombok @Builder 覆盖不到
        if (baseMapper.update(delShortLinkDO, updateWrapper) < 1) {
            throw new ClientException("短链接不存在或不在回收站中");
        }
        // 不需要清理缓存：跳转缓存在移入回收站时就已经删了；
        // 若之后有人访问过，写下的空值缓存恰好是正确答案（这条短链接确实已经不存在了）
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkRecycleBinPageReqDTO requestParam) {
        // gidList 由后管按当前用户的分组算出，但中台不能直接相信请求参数：
        // 不校验的话，直连中台传别人的 gid 就能翻看对方回收站
        checkGroupsBelongToUser(requestParam.getGidList());
        IPage<ShortLinkDO> resultPage = baseMapper.pageRecycleBinLink(requestParam);
        return resultPage.convert(each -> {
            ShortLinkPageRespDTO result = BeanUtil.toBean(each, ShortLinkPageRespDTO.class);
            // 与正常列表一致：库里存的是不带协议的 domain，前端要直接当链接用，这里补上协议
            result.setDomain("http://" + result.getDomain());
            return result;
        });
    }

    /**
     * 校验单个分组是否属于当前登录用户
     *
     * @param gid 分组标识
     * @throws ClientException 分组标识为空、用户未登录或分组不属于当前用户
     */
    private void checkGroupBelongToUser(String gid) {
        if (StrUtil.isBlank(gid)) {
            throw new ClientException("分组标识不能为空");
        }
        String username = UserContext.getUsername();
        if (StrUtil.isBlank(username)) {
            throw new ClientException("用户未登录");
        }
        Long groupCount = linkGroupMapper.selectCount(Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getGid, gid)
                .eq(GroupDO::getUsername, username)
                .eq(GroupDO::getDelFlag, 0));
        if (groupCount == null || groupCount == 0) {
            throw new ClientException("分组不存在或不属于当前用户");
        }
    }

    /**
     * 校验一组分组是否都属于当前登录用户
     * <p>
     * 回收站是跨分组视图，gidList 可能有多个。这里用一次 IN 查询统计
     * 「属于当前用户的分组数」，与去重后的请求数量比对：少一个就说明混进了别人的分组。
     *
     * @param gidList 分组标识集合
     * @throws ClientException 集合为空、含空白值或包含不属于当前用户的分组
     */
    private void checkGroupsBelongToUser(List<String> gidList) {
        if (CollUtil.isEmpty(gidList) || gidList.stream().anyMatch(StrUtil::isBlank)) {
            // 空集合会让 XML 里的 <foreach> 生成 `IN ()`，直接变成语法错误的 SQL
            throw new ClientException("分组标识不能为空");
        }
        String username = UserContext.getUsername();
        if (StrUtil.isBlank(username)) {
            throw new ClientException("用户未登录");
        }
        List<String> distinctGidList = gidList.stream().distinct().collect(Collectors.toList());
        Long ownedCount = linkGroupMapper.selectCount(Wrappers.lambdaQuery(GroupDO.class)
                .in(GroupDO::getGid, distinctGidList)
                .eq(GroupDO::getUsername, username)
                .eq(GroupDO::getDelFlag, 0));
        if (ownedCount == null || ownedCount < distinctGidList.size()) {
            throw new ClientException("分组标识中包含不属于当前用户的分组");
        }
    }
}
