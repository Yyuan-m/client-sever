package com.car.customer.config;

import com.car.customer.common.security.JwtTokenFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenFilter jwtTokenFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/sms-code",
                                "/api/auth/forgot-password",
                                "/api/auth/logout",
                                "/api/auth/refresh-token",
                                "/api/feedback/submit",
                                "/uploads/**"
                        ).permitAll()
                        // 车辆/轮播/系统配置仅允许 GET 公开访问，写操作需鉴权
                        .requestMatchers(HttpMethod.GET, "/api/car/**", "/api/carousel/**", "/api/system/**").permitAll()
                        // 公告：GET 公开访问（头部下拉/列表/详情）
                        .requestMatchers(HttpMethod.GET, "/api/announcement/**").permitAll()
                        // 价格计算接口公开访问（车辆详情页未登录也能查看准确价格）
                        .requestMatchers("/api/price/**").permitAll()
                        // 优惠券：可领券列表 + 券详情 公开访问；其他接口（mine/usable/receive/lock/verify/claimed-ids）需鉴权
                        .requestMatchers(HttpMethod.GET, "/api/coupon/available").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/coupon/{id:[0-9]+}").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
