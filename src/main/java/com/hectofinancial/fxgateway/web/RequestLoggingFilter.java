package com.hectofinancial.fxgateway.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 인입/종료 요청 로깅 + 상관관계 ID(correlation id) 전파.
 * 운영 통신 추적용. PII(요청 바디: 송금인/수취인 등)는 로깅하지 않는다.
 *
 * - 인입: "→ {cid} {method} {uri}"
 * - 종료: "← {cid} {method} {uri} {status} {elapsed}ms"
 * - cid: 인입 헤더(X-Request-Id) 있으면 전파, 없으면 생성. 응답 헤더로도 반환.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String MDC_KEY = "cid";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String cid = request.getHeader(HEADER_REQUEST_ID);
        if (!StringUtils.hasText(cid)) {
            cid = UUID.randomUUID().toString().substring(0, 8);
        }
        MDC.put(MDC_KEY, cid);
        response.setHeader(HEADER_REQUEST_ID, cid);

        long start = System.nanoTime();
        log.info("→ [{}] {} {}", cid, request.getMethod(), request.getRequestURI());
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.info("← [{}] {} {} status={} {}ms",
                    cid, request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMs);
            MDC.remove(MDC_KEY);
        }
    }
}
