package com.codepilot.ai.provider;

import com.codepilot.ai.prompting.PromptBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Component("siliconflow")
public class SiliconFlowProvider extends AbstractAiProvider {

    private final String apiUrl;
    private final String apiKey;
    private final String model;

    public SiliconFlowProvider(WebClient webClient,
                               PromptBuilder promptBuilder,
                               @Value("${ai.providers.siliconflow.api-url}") String apiUrl,
                               @Value("${ai.providers.siliconflow.api-key}") String apiKey,
                               @Value("${ai.providers.siliconflow.model}") String model) {
        super(webClient, promptBuilder);
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String getProviderName() {
        return "siliconflow";
    }

    @Override
    protected String getApiUrl() {
        return apiUrl;
    }

    @Override
    protected String getApiKey() {
        return apiKey;
    }

    @Override
    protected String getModel() {
        return model;
    }

    @Override
    protected Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Content-Type", "application/json");
        return headers;
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }
}
