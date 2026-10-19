package client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import config.ApplicationConfig;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenRouterClient {

        private static final String OPENROUTER_CHAT_COMPLETIONS_PATH = "/api/v1/chat/completions";
        private static final String OLLAMA_CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
        private static final String GIGACHAT_CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
        private static final String GIGACHAT_AUTHORIZER_PATH = "/api/v1/auth/authorize";

        public static LlmResponse call(String prompt) {
            return call(prompt, LlmProfile.DEFAULT);
        }

        public static LlmResponse call(String prompt, String profile) {

            try {
                ApplicationConfig config = ApplicationConfig.load();
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", config.llmModel(profile));
                body.put("messages", List.of(
                                Map.of("role", "system", "content", config.llmSystemPrompt(profile)),
                                Map.of("role", "user", "content", prompt)
                        ));
                body.put("temperature", config.llmTemperature(profile));

                Integer maxTokens = config.llmMaxTokens(profile);
                if (maxTokens != null) {
                    body.put("max_tokens", maxTokens);
                }

                Double topP = config.llmTopP(profile);
                if (topP != null) {
                    body.put("top_p", topP);
                }

                GigaChatTokenManager tokenManager = null;
                if ("gigachat".equals(config.llmProvider())) {
                    String authBaseUrl = config.gigachatAuthBaseUrl().isBlank()
                            ? config.llmBaseUrl().replaceAll("/v1/.*", "").replaceAll("/v1$", "")
                            : config.gigachatAuthBaseUrl();
                    tokenManager = new GigaChatTokenManager(config.gigaChatAuthKey(), authBaseUrl);
                }

                String apiBaseUrl = config.llmBaseUrl().replaceAll("/$", "");

                Response response = RestAssured.given()
                        .baseUri(apiBaseUrl)
                        .contentType(ContentType.JSON)
                        .headers(buildHeaders(config, tokenManager))
                        .body(body)
                        .post(chatCompletionsPath(config));

                String rawBody = response.getBody().asString();
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new RuntimeException("LLM API returned HTTP " + response.statusCode() + ": " + rawBody);
                }

                JsonNode content = new ObjectMapper().readTree(rawBody)
                        .path("choices").path(0).path("message").path("content");
                if (content.isMissingNode() || content.asText().isBlank()) {
                    throw new RuntimeException("OpenRouter API response has no assistant content: " + rawBody);
                }
                return new LlmResponse(rawBody, content.asText());

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private static Map<String, String> buildHeaders(ApplicationConfig config, GigaChatTokenManager tokenManager) {
            if ("openrouter".equals(config.llmProvider())) {
                return Map.of("Authorization", "Bearer " + config.openRouterApiKey());
            }
            if ("gigachat".equals(config.llmProvider())) {
                String accessToken = tokenManager.getAccessToken();
                return Map.of(
                        "Authorization", "Bearer " + accessToken,
                        "Content-Type", "application/json",
                        "Accept", "application/json"
                );
            }
            return Map.of();
        }

        private static String chatCompletionsPath(ApplicationConfig config) {
            if ("openrouter".equals(config.llmProvider())) {
                return OPENROUTER_CHAT_COMPLETIONS_PATH;
            }
            if ("gigachat".equals(config.llmProvider())) {
                return GIGACHAT_CHAT_COMPLETIONS_PATH;
            }
            return OLLAMA_CHAT_COMPLETIONS_PATH;
        }

        public record LlmResponse(String rawBody, String content) {
        }
}
