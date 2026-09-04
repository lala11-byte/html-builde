package com.htmlbuilder.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.main.web-application-type=reactive"
})
class GatewayApplicationTest {

    @Test
    void contextLoads() {
    }
}