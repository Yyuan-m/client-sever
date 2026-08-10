package com.car.customer.module.feedback.controller;

import com.car.customer.common.result.Result;
import com.car.customer.module.feedback.dto.FeedbackDTO;
import com.car.customer.module.feedback.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/submit")
    public Result<Void> submit(@Valid @RequestBody FeedbackDTO dto) {
        feedbackService.submit(dto);
        return Result.ok();
    }
}
