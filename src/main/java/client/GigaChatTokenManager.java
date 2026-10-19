package client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Менеджер для получения и кэширования access token GigaChat.
 * Использует OAuth flow: POST к /api/v2/oauth с Authorization: Basic.
 */
public class GigaChatTokenManager {

    private static final String AUTHORIZER_PATH = "/api/v2/oauth";
    private static final int TOKEN_TTL_SECONDS = 5 * 60; // 5 минут
    private static final int CACHE_PADDING_SECONDS = 30; // обновляем за 30 сек до истечения

    private final String basicAuthKey;
    private final String baseUrl;
    private final AtomicReference<String> cachedToken = new AtomicReference<>();
    private volatile long tokenExpirationTime = 0;

    // Кэш SSLContext для переиспользования
    private static final SSLContext SSL_CONTEXT;

    static {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[0];
                        }

                        public void checkClientTrusted(
                                java.security.cert.X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(
                                java.security.cert.X509Certificate[] certs, String authType) {
                        }
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            SSL_CONTEXT = sc;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize trust-all SSLContext", e);
        }
    }

    public GigaChatTokenManager(String basicAuthKey, String baseUrl) {
        this.basicAuthKey = basicAuthKey;
        this.baseUrl = baseUrl;
    }

    /**
     * Возвращает актуальный access token. Использует кэш, при необходимости обновляет.
     */
    public String getAccessToken() {
        long now = System.currentTimeMillis() / 1000;
        if (cachedToken.get() != null && now < tokenExpirationTime - CACHE_PADDING_SECONDS) {
            return cachedToken.get();
        }
        return refreshToken();
    }

    private synchronized String refreshToken() {
        String token = fetchToken();
        cachedToken.set(token);
        tokenExpirationTime = System.currentTimeMillis() / 1000 + TOKEN_TTL_SECONDS;
        return token;
    }

    private String fetchToken() {
        String url = baseUrl.replaceAll("/$", "");
        String rquid = java.util.UUID.randomUUID().toString();

        HttpClient client = HttpClient.newBuilder()
                .sslContext(SSL_CONTEXT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("RqUID", rquid)
                .header("Authorization", "Basic " + basicAuthKey)
                .POST(HttpRequest.BodyPublishers.ofString("scope=GIGACHAT_API_PERS"))
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException("GigaChat auth request failed: " + e.getMessage(), e);
        }

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "GigaChat auth failed: HTTP " + response.statusCode() + " - " + response.body());
        }

        try {
            JsonNode json = new ObjectMapper().readTree(response.body());
            JsonNode tokenNode = json.path("access_token");
            if (tokenNode.isMissingNode() || tokenNode.asText().isBlank()) {
                throw new RuntimeException(
                        "GigaChat auth response has no access_token: " + response.body());
            }
            return tokenNode.asText();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse GigaChat auth response: " + response.body(), e);
        }
    }
}
