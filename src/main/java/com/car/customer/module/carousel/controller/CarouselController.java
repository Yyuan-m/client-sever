package com.car.customer.module.carousel.controller;

import com.car.customer.common.result.Result;
import com.car.customer.entity.Carousel;
import com.car.customer.module.carousel.service.CarouselService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/carousel")
@RequiredArgsConstructor
public class CarouselController {

    private final CarouselService carouselService;

    @GetMapping("/active")
    public Result<List<Carousel>> active() {
        return Result.ok(carouselService.getActiveCarousel());
    }
}
