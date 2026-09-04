package com.htmlbuilder.project;

import com.htmlbuilder.project.mapper.PageMapper;
import com.htmlbuilder.project.mapper.ProjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.autoconfigure.exclude=com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
})
class ProjectServiceApplicationTest {

    @MockBean
    private ProjectMapper projectMapper;

    @MockBean
    private PageMapper pageMapper;

    @Test
    void contextLoads() {
    }
}