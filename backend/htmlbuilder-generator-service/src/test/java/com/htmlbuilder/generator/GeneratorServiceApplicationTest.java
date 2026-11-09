package com.htmlbuilder.generator;

import com.htmlbuilder.generator.agent.AnthropicChatLanguageModel;
import com.htmlbuilder.generator.agent.FastChatLanguageModel;
import com.htmlbuilder.generator.agent.graph.ProgressRegistry;
import com.htmlbuilder.generator.mapper.GenerationTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.autoconfigure.exclude=com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
})
class GeneratorServiceApplicationTest {

    @MockBean
    private GenerationTaskMapper generationTaskMapper;

    @MockBean
    private AnthropicChatLanguageModel anthropicChatLanguageModel;

    @MockBean
    private FastChatLanguageModel fastChatLanguageModel;

    @MockBean
    private ProgressRegistry progressRegistry;

    @Test
    void contextLoads() {
    }
}