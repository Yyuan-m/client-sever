package com.car.customer.module.feedback.service;

import com.car.customer.entity.Feedback;
import com.car.customer.mapper.FeedbackMapper;
import com.car.customer.module.feedback.dto.FeedbackDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackMapper feedbackMapper;

    public void submit(FeedbackDTO dto) {
        Feedback fb = new Feedback();
        fb.setType(dto.getType() != null ? dto.getType() : "feedback");
        fb.setName(dto.getName());
        fb.setPhone(dto.getPhone());
        fb.setContent(dto.getContent());
        fb.setCarType(dto.getCarType());
        if (dto.getRentDate() != null && !dto.getRentDate().isBlank()) {
            fb.setRentDate(LocalDate.parse(dto.getRentDate()));
        }
        fb.setStatus("pending");
        feedbackMapper.insert(fb);
        log.info("收到用户反馈: type={}, name={}, phone={}", fb.getType(), fb.getName(), fb.getPhone());
    }
}
