package com.back.global.security;

import com.back.global.rsData.RsData;
import com.back.standard.ut.Util;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final CustomAuthenticationFilter customAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(
                        auth -> auth
                                // 공개 API
                                .requestMatchers(
                                        "/api/v1/member/members/login",
                                        "/api/v1/member/members/logout",
                                        "/api/v1/member/members/join",
                                        "/api/v1/member/members/randomSecureTip"
                                ).permitAll()
                                // 인증 필요 API
                                .requestMatchers("/api/v1/member/members/me").authenticated()
                                .requestMatchers("/api/v1/market/orders/**").authenticated()
                                // 나머지 (내부용 API는 CustomAuthenticationFilter에서 systemApiKey 검증)
                                .anyRequest().permitAll()
                )
                .headers(
                        headers -> headers
                                .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                )
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(AbstractHttpConfigurer::disable)
                .addFilterBefore(customAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(
                        exceptionHandling -> exceptionHandling
                                .authenticationEntryPoint(
                                        (request, response, authException) -> {
                                            response.setContentType("application/json;charset=UTF-8");
                                            response.setStatus(401);
                                            response.getWriter().write(
                                                    Util.json.toString(
                                                            new RsData<Void>("401-1", "로그인 후 이용해주세요.")
                                                    )
                                            );
                                        }
                                )
                                .accessDeniedHandler(
                                        (request, response, accessDeniedException) -> {
                                            response.setContentType("application/json;charset=UTF-8");
                                            response.setStatus(403);
                                            response.getWriter().write(
                                                    Util.json.toString(
                                                            new RsData<Void>("403-1", "권한이 없습니다.")
                                                    )
                                            );
                                        }
                                )
                );

        return http.build();
    }
}
