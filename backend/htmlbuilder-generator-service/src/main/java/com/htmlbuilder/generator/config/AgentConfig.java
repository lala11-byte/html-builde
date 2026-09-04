package com.htmlbuilder.generator.config;

import com.htmlbuilder.generator.agent.DeepSeekChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Value("${deepseek.api-key:sk-placeholder}")
    private String apiKey;

    @Value("${deepseek.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Bean
    public DeepSeekChatModel deepSeekChatModel() {
        return new DeepSeekChatModel(apiKey, baseUrl);
    }
}