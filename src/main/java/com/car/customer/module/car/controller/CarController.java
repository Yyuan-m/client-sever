package com.car.customer.module.car.controller;

import com.car.customer.common.result.PageResult;
import com.car.customer.common.result.Result;
import com.car.customer.entity.Car;
import com.car.customer.module.car.service.CarService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/car")
@RequiredArgsConstructor
public class CarController {

    private final CarService carService;

    @GetMapping("/list")
    public Result<PageResult<Car>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(carService.getCarList(type, keyword, minPrice, maxPrice, status, sort, page, pageSize));
    }

    @GetMapping("/detail/{id}")
    public Result<Car> detail(@PathVariable Long id) {
        return Result.ok(carService.getCarDetail(id));
    }

    @GetMapping("/hot")
    public Result<List<Car>> hot() {
        return Result.ok(carService.getHotCars());
    }

    /**
     * 切换车型推荐状态（后台管理配置，需登录鉴权）
     * @param id 车辆ID
     * @param isRecommend 1推荐 0取消
     */
    @PutMapping("/recommend/{id}")
    public Result<Car> toggleRecommend(@PathVariable Long id, @RequestParam Integer isRecommend) {
        return Result.ok(carService.toggleRecommend(id, isRecommend));
    }
}
