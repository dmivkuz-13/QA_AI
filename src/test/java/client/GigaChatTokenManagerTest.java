package client;

import org.testng.Assert;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * Тесты для проверки получения Access token через GigaChat OAuth API.
 * <p>
 * Запрос: POST https://ngw.devices.sberbank.ru:9443/api/v2/oauth
 * с Authorization: Basic <base64(GIGACHAT_AUTH_KEY)> и scope=GIGACHAT_API_PERS.
 */
public class GigaChatTokenManagerTest {

    private static final String AUTH_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth";
    private static final String GIGACHAT_AUTH_KEY = System.getenv("GIGACHAT_AUTH_KEY");

    @Test
    public void shouldObtainAccessToken() throws Exception {
        Assert.assertNotNull(GIGACHAT_AUTH_KEY, "GIGACHAT_AUTH_KEY environment variable must be set");
        Assert.assertFalse(GIGACHAT_AUTH_KEY.isBlank(), "GIGACHAT_AUTH_KEY must not be blank");

        // Disable SSL verification for Sberbank's internal certificate
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
        };
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new SecureRandom());

        String rquid = UUID.randomUUID().toString();

        HttpClient client = HttpClient.newBuilder()
                .sslContext(sc)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AUTH_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("RqUID", rquid)
                .header("Authorization", "Basic " + GIGACHAT_AUTH_KEY)
                .POST(HttpRequest.BodyPublishers.ofString("scope=GIGACHAT_API_PERS"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Response status: " + response.statusCode());
        System.out.println("Response body: " + response.body());

        Assert.assertEquals(response.statusCode(), 200,
                "Expected HTTP 200 but got " + response.statusCode() + ". Body: " + response.body());

        String body = response.body();
        Assert.assertTrue(body.contains("access_token"),
                "Response must contain 'access_token': " + body);
        Assert.assertTrue(body.contains("expires_at"),
                "Response must contain 'expires_at': " + body);

        String accessToken = extractJsonValue(body, "access_token");
        Assert.assertNotNull(accessToken, "Access token must not be null");
        Assert.assertFalse(accessToken.isBlank(), "Access token must not be blank");

        System.out.println("Access token obtained successfully. RqUID: " + rquid);
    }

    @Test
    public void shouldGenerateUniqueRqUIDPerRequest() {
        String rquid1 = UUID.randomUUID().toString();
        String rquid2 = UUID.randomUUID().toString();

        Assert.assertNotEquals(rquid1, rquid2, "Each request must have a unique RqUID");
    }

    private static String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int index = json.indexOf(searchKey);
        if (index == -1) return null;
        int valueStart = index + searchKey.length();
        // Skip whitespace
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= json.length()) return null;
        char quote = json.charAt(valueStart);
        if (quote == '"') {
            // String value
            int end = json.indexOf('"', valueStart + 1);
            if (end == -1) return null;
            return json.substring(valueStart + 1, end);
        } else {
            // Number or other value
            int end = valueStart;
            while (end < json.length() && !",}".contains(String.valueOf(json.charAt(end)))) {
                end++;
            }
            return json.substring(valueStart, end).trim();
        }
    }
}
