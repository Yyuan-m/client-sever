package com.car.customer.module.announcement.controller;

import com.car.customer.common.result.PageResult;
import com.car.customer.common.result.Result;
import com.car.customer.entity.Announcement;
import com.car.customer.module.announcement.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/announcement")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    /**
     * 头部下拉：获取 3 条优先级高的公告
     */
    @GetMapping("/top")
    public Result<List<Announcement>> top() {
        return Result.ok(announcementService.getTopAnnouncements());
    }

    /**
     * 公告列表分页
     */
    @GetMapping("/page")
    public Result<PageResult<Announcement>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "false") boolean onlyHigh) {
        return Result.ok(announcementService.getPage(page, pageSize, onlyHigh));
    }

    /**
     * 公告详情
     */
    @GetMapping("/{id}")
    public Result<Announcement> detail(@PathVariable Long id) {
        return Result.ok(announcementService.getDetail(id));
    }
}
