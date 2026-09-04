package com.htmlbuilder.project;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.autoconfigure.exclude=com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
})
class ProjectServiceApplicationTest {

    @Test
    void contextLoads() {
    }
}