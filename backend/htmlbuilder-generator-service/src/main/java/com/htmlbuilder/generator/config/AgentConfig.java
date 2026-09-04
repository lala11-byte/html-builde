package com.htmlbuilder.generator.config;

import com.htmlbuilder.generator.agent.DeepSeekChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Value("${deepseek.api-key:${TOKENHUB_API_KEY:}}")
    private String apiKey;

    @Value("${deepseek.base-url:http://192.200.1.213:18086/v1/messages}")
    private String baseUrl;

    @Value("${deepseek.model:deepseek-v4-pro}")
    private String modelName;

    @Bean
    public DeepSeekChatModel deepSeekChatModel() {
        return new DeepSeekChatModel(apiKey, baseUrl, modelName);
    }
}