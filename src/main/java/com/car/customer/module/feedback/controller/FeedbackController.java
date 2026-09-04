package com.car.customer.module.feedback.controller;

import com.car.customer.common.result.PageResult;
import com.car.customer.common.result.Result;
import com.car.customer.module.feedback.dto.FeedbackDTO;
import com.car.customer.module.feedback.service.FeedbackService;
import com.car.customer.module.feedback.vo.AppointmentVO;
import com.car.customer.module.feedback.vo.ContactInfoVO;
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

    /** 我的预约/留言列表（分页 + 状态筛选 + 类型筛选，需登录） */
    @GetMapping("/appointments")
    public Result<PageResult<AppointmentVO>> myAppointments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        return Result.ok(feedbackService.getMyAppointments(page, pageSize, status, type));
    }

    /** 取消预约（仅待处理/已确认状态，需登录） */
    @PostMapping("/appointments/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        feedbackService.cancelAppointment(id);
        return Result.ok();
    }

    /** 查看联系人完整信息（仅本人可查，未脱敏，需登录） */
    @GetMapping("/appointments/{id}/contact")
    public Result<ContactInfoVO> contact(@PathVariable Long id) {
        return Result.ok(feedbackService.getContactInfo(id));
    }
}
