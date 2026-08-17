package com.car.customer.module.announcement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.car.customer.common.exception.BusinessException;
import com.car.customer.common.result.PageResult;
import com.car.customer.entity.Announcement;
import com.car.customer.mapper.AnnouncementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementMapper announcementMapper;

    /**
     * 获取优先级最高的 3 条已发布公告（priority=high）
     * 按 created_at 倒序取前 3 条
     */
    public List<Announcement> getTopAnnouncements() {
        return announcementMapper.selectList(new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getStatus, 1)
                .eq(Announcement::getPriority, "high")
                .orderByDesc(Announcement::getCreatedAt)
                .last("LIMIT 3"));
    }

    /**
     * 分页查询已发布公告
     *
     * @param page     当前页（从 1 开始）
     * @param pageSize 每页条数
     * @param onlyHigh true=仅看高优先级(priority=high)
     */
    public PageResult<Announcement> getPage(int page, int pageSize, boolean onlyHigh) {
        Page<Announcement> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getStatus, 1)
                .orderByDesc(Announcement::getCreatedAt);
        if (onlyHigh) {
            wrapper.eq(Announcement::getPriority, "high");
        }
        IPage<Announcement> result = announcementMapper.selectPage(p, wrapper);
        return PageResult.of(result);
    }

    /**
     * 获取公告详情（仅返回已发布且未删除的）
     */
    public Announcement getDetail(Long id) {
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null || announcement.getStatus() == null
                || announcement.getStatus() != 1) {
            throw new BusinessException("公告不存在或已下架");
        }
        return announcement;
    }
}
