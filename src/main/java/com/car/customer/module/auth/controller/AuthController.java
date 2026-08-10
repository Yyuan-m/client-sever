package com.car.customer.module.auth.controller;

import com.car.customer.common.result.Result;
import com.car.customer.module.auth.dto.ForgotPasswordDTO;
import com.car.customer.module.auth.dto.LoginDTO;
import com.car.customer.module.auth.dto.RegisterDTO;
import com.car.customer.module.auth.dto.SmsCodeDTO;
import com.car.customer.module.auth.service.AuthService;
import com.car.customer.module.auth.vo.LoginVO;
import com.car.customer.module.auth.vo.MemberVO;
import com.car.customer.module.auth.vo.RefreshTokenVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterDTO dto) {
        Long id = authService.register(dto);
        return Result.ok(Map.of("id", id));
    }

    @PostMapping("/sms-code")
    public Result<Void> sendSmsCode(@Valid @RequestBody SmsCodeDTO dto) {
        authService.sendSmsCode(dto);
        return Result.ok();
    }

    @PostMapping("/forgot-password")
    public Result<Void> forgotPassword(@Valid @RequestBody ForgotPasswordDTO dto) {
        authService.forgotPassword(dto);
        return Result.ok();
    }

    @GetMapping("/user-info")
    public Result<MemberVO> userInfo() {
        return Result.ok(authService.getCurrentUserInfo());
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, @RequestBody(required = false) Map<String, String> body) {
        String accessToken = request.getHeader("Authorization");
        String refreshToken = body == null ? null : body.get("refreshToken");
        authService.logout(accessToken, refreshToken);
        return Result.ok();
    }

    /**
     * 刷新 access token：客户端在 access 过期前/后用 refresh token 换取新的 access + refresh
     * 请求体：{ "refreshToken": "..." }
     */
    @PostMapping("/refresh-token")
    public Result<RefreshTokenVO> refreshToken(@RequestBody Map<String, String> body) {
        String refreshToken = body == null ? null : body.get("refreshToken");
        return Result.ok(authService.refreshToken(refreshToken));
    }
}
