package com.htmlbuilder.gateway.config;

import com.htmlbuilder.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关全局降级处理器
 * 下游服务不可用或路由不存在时，统一返回 Result JSON，不暴露堆栈
 */
@Configuration
public class GatewayFallbackConfig {

    @Bean
    @Order(-2)
    public ErrorWebExceptionHandler gatewayErrorHandler() {
        return new GatewayErrorHandler();
    }

    static class GatewayErrorHandler implements ErrorWebExceptionHandler {

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
            ServerHttpResponse response = exchange.getResponse();

            if (response.isCommitted()) {
                return Mono.error(ex);
            }

            response.setStatusCode(HttpStatus.OK);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

            Result<?> result;
            if (ex instanceof ResponseStatusException) {
                ResponseStatusException rse = (ResponseStatusException) ex;
                int status = rse.getStatusCode().value();
                if (status == 404) {
                    result = Result.fail(404, "请求路径不存在");
                } else if (status == 503 || status == 500) {
                    result = Result.fail(5003, "服务暂不可用，请稍后重试");
                } else {
                    result = Result.fail(status, rse.getReason() != null ? rse.getReason() : "网关错误");
                }
            } else {
                result = Result.fail(5003, "服务暂不可用，请稍后重试");
            }

            try {
                byte[] bytes = objectMapper.writeValueAsBytes(result);
                DataBuffer buffer = response.bufferFactory().wrap(bytes);
                return response.writeWith(Mono.just(buffer));
            } catch (Exception e) {
                return Mono.error(e);
            }
        }
    }
}
