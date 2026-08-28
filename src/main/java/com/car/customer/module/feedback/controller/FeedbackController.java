package com.car.customer.module.feedback.controller;

import com.car.customer.common.result.PageResult;
import com.car.customer.common.result.Result;
import com.car.customer.module.feedback.dto.FeedbackDTO;
import com.car.customer.module.feedback.service.FeedbackService;
import com.car.customer.module.feedback.vo.AppointmentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    /** 提交预约咨询/留言反馈（公开接口，登录态自动绑定会员） */
    @PostMapping("/submit")
    public Result<Void> submit(@Valid @RequestBody FeedbackDTO dto) {
        feedbackService.submit(dto);
        return Result.ok();
    }

    /** 我的预约列表（分页 + 状态筛选，需登录） */
    @GetMapping("/appointments")
    public Result<PageResult<AppointmentVO>> myAppointments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.ok(feedbackService.getMyAppointments(page, pageSize, status));
    }

    /** 取消预约（仅待处理/已确认状态，需登录） */
    @PostMapping("/appointments/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        feedbackService.cancelAppointment(id);
        return Result.ok();
    }
}
