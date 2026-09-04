package com.car.customer.module.complaint.controller;

import com.car.customer.common.result.PageResult;
import com.car.customer.common.result.Result;
import com.car.customer.module.complaint.dto.ComplaintRateDTO;
import com.car.customer.module.complaint.dto.ComplaintSubmitDTO;
import com.car.customer.module.complaint.service.ComplaintService;
import com.car.customer.module.complaint.vo.ComplaintVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 售后投诉接口（需登录）
 * 提交的投诉写入后台共用表 car_rental.after_sales_complaint，
 * 由后台管理系统"售后投诉"页面处理
 */
@RestController
@RequestMapping("/api/complaint")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;

    /** 提交投诉 */
    @PostMapping("/submit")
    public Result<Void> submit(@Valid @RequestBody ComplaintSubmitDTO dto) {
        complaintService.submit(dto);
        return Result.ok();
    }

    /** 我的投诉记录（分页 + 投诉类型筛选） */
    @GetMapping("/mine")
    public Result<PageResult<ComplaintVO>> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String type) {
        return Result.ok(complaintService.myComplaints(page, pageSize, type));
    }

    /** 投诉详情（仅本人） */
    @GetMapping("/{id}")
    public Result<ComplaintVO> detail(@PathVariable Long id) {
        return Result.ok(complaintService.getDetail(id));
    }

    /** 对已处理投诉评分（1-5星，仅本人 + 已解决状态） */
    @PostMapping("/{id}/rate")
    public Result<Void> rate(@PathVariable Long id, @Valid @RequestBody ComplaintRateDTO dto) {
        complaintService.rate(id, dto.getSatisfaction());
        return Result.ok();
    }
}
