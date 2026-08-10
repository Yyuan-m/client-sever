package com.car.customer.module.user.controller;

import com.car.customer.common.result.Result;
import com.car.customer.entity.Car;
import com.car.customer.module.auth.vo.MemberVO;
import com.car.customer.module.upload.service.UploadService;
import com.car.customer.module.user.dto.ChangePasswordDTO;
import com.car.customer.module.user.dto.ProfileDTO;
import com.car.customer.module.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UploadService uploadService;

    @Value("${upload.base-url}")
    private String uploadBaseUrl;

    @PutMapping("/profile")
    public Result<MemberVO> updateProfile(@RequestBody ProfileDTO dto) {
        return Result.ok(userService.updateProfile(dto));
    }

    @PostMapping("/avatar")
    public Result<Map<String, Object>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = uploadService.upload(file);
        // 拼接完整外网 URL 写入数据库，确保后台管理系统可跨域访问头像
        String relativePath = result.get("url").toString();
        String fullUrl = uploadBaseUrl + relativePath;
        ProfileDTO profile = new ProfileDTO();
        profile.setAvatar(fullUrl);
        userService.updateProfile(profile);
        result.put("url", fullUrl);
        return Result.ok(result);
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        userService.changePassword(dto);
        return Result.ok();
    }

    @GetMapping("/favorites")
    public Result<List<Car>> favorites() {
        return Result.ok(userService.getFavorites());
    }

    @PostMapping("/favorite")
    public Result<Void> favorite(@RequestBody Map<String, Object> body) {
        Long carId = Long.valueOf(body.get("carId").toString());
        String action = body.get("action") != null ? body.get("action").toString() : null;
        userService.toggleFavorite(carId, action);
        return Result.ok();
    }
}
