package config;

import util.FilesUtil;

import java.util.Properties;

public record ApplicationConfig(
        String url,
        String llmProvider,
        String llmBaseUrl,
        String llmModel,
        String llmSystemPrompt,
        String openRouterApiKey,
        String gigaChatAuthKey,
        String gigachatAuthBaseUrl,
        Properties properties
) {

    private static final String DEFAULT_LLM_PROVIDER = "openrouter";
    private static final String DEFAULT_OPENROUTER_BASE_URL = "https://openrouter.ai";
    private static final String DEFAULT_OPENROUTER_MODEL = "deepseek/deepseek-v4.1-flash";
    private static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_OLLAMA_MODEL = "gemma4:latest";
    private static final double DEFAULT_TEMPERATURE = 0.2;
    private static final String DEFAULT_LLM_SYSTEM_PROMPT =
            "You are a QA engineer. Return structured output.";

    public static ApplicationConfig load() {
        try {
            Properties properties = new Properties();
            properties.load(new java.io.StringReader(FilesUtil.read("application.properties")));
            String url = properties.getProperty("application.url", "").trim();
            if (url.isBlank()) {
                throw new RuntimeException("Не задано свойство application.url");
            }
            String llmProvider = properties.getProperty("llm.provider", DEFAULT_LLM_PROVIDER).trim().toLowerCase();
            if (!"openrouter".equals(llmProvider) && !"ollama".equals(llmProvider) && !"gigachat".equals(llmProvider)) {
                throw new RuntimeException("llm.provider должен быть openrouter или ollama или gigachat");
            }
            String defaultBaseUrl = "ollama".equals(llmProvider) ? DEFAULT_OLLAMA_BASE_URL : DEFAULT_OPENROUTER_BASE_URL;
            String defaultModel = "ollama".equals(llmProvider) ? DEFAULT_OLLAMA_MODEL : DEFAULT_OPENROUTER_MODEL;
            String llmBaseUrl = properties.getProperty("llm.baseUrl", defaultBaseUrl).trim();
            String llmModel = properties.getProperty("llm.model", defaultModel).trim();
            String llmSystemPrompt = properties.getProperty("llm.systemPrompt", DEFAULT_LLM_SYSTEM_PROMPT).trim();
            String openRouterApiKey = System.getenv("OPENROUTER_API_KEY");
            String gigaChatAuthKey = System.getenv("GIGACHAT_AUTH_KEY");
            String gigachatAuthBaseUrl = properties.getProperty("llm.gigachatAuthBaseUrl", "").trim();
            if (llmBaseUrl.isBlank()) {
                throw new RuntimeException("Не задано свойство llm.baseUrl");
            }
            if (llmModel.isBlank()) {
                throw new RuntimeException("Не задано свойство llm.model");
            }
            if (llmSystemPrompt.isBlank()) {
                throw new RuntimeException("Не задано свойство llm.systemPrompt");
            }
            if ("openrouter".equals(llmProvider) && (openRouterApiKey == null || openRouterApiKey.isBlank())) {
                throw new RuntimeException("Для llm.provider=openrouter должна быть установлена переменная OPENROUTER_API_KEY");
            }
            if ("gigachat".equals(llmProvider) && (gigaChatAuthKey == null || gigaChatAuthKey.isBlank())) {
                throw new RuntimeException("Для llm.provider=gigachat должна быть установлена переменная GIGACHAT_AUTH_KEY");
            }
            return new ApplicationConfig(
                    url,
                    llmProvider,
                    llmBaseUrl,
                    llmModel,
                    llmSystemPrompt,
                    openRouterApiKey,
                    gigaChatAuthKey,
                    gigachatAuthBaseUrl,
                    properties
            );
        } catch (Exception e) {
            throw new RuntimeException("Не удалось загрузить application.properties", e);
        }
    }

    public String llmModel(String profile) {
        return profileProperty(profile, "model", llmModel).trim();
    }

    public String llmSystemPrompt(String profile) {
        return profileProperty(profile, "systemPrompt", llmSystemPrompt).trim();
    }

    public double llmTemperature(String profile) {
        return parseDouble(profileProperty(profile, "temperature", String.valueOf(DEFAULT_TEMPERATURE)),
                "temperature", 0.0, 2.0);
    }

    public Integer llmMaxTokens(String profile) {
        String value = profileProperty(profile, "maxTokens", "").trim();
        if (value.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new RuntimeException("maxTokens должен быть больше 0");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new RuntimeException("Некорректное значение maxTokens: " + value, e);
        }
    }

    public Double llmTopP(String profile) {
        String value = profileProperty(profile, "topP", "").trim();
        if (value.isBlank()) {
            return null;
        }
        return parseDouble(value, "topP", 0.0, 1.0);
    }

    private String profileProperty(String profile, String name, String fallback) {
        String normalizedProfile = profile == null || profile.isBlank() ? "default" : profile.trim();
        String profileKey = "llm." + normalizedProfile + "." + name;
        String defaultKey = "llm.default." + name;
        String legacyKey = "llm." + name;

        return properties.getProperty(
                profileKey,
                properties.getProperty(defaultKey, properties.getProperty(legacyKey, fallback))
        );
    }

    private double parseDouble(String value, String name, double min, double max) {
        try {
            double parsed = Double.parseDouble(value.trim());
            if (parsed < min || parsed > max) {
                throw new RuntimeException(name + " должен быть в диапазоне " + min + ".." + max);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new RuntimeException("Некорректное значение " + name + ": " + value, e);
        }
    }
}
