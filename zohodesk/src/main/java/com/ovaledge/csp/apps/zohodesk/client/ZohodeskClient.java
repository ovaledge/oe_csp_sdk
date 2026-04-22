package com.ovaledge.csp.apps.zohodesk.client;

import com.ovaledge.csp.apps.zohodesk.constants.ZohodeskConstants;
import com.ovaledge.csp.v3.core.connectionpool.core.ConnectionPoolManager;
import com.ovaledge.csp.v3.core.connectionpool.enums.ResourceType;
import com.ovaledge.csp.v3.core.connectionpool.enums.RestAuthenticationType;
import com.ovaledge.csp.v3.core.connectionpool.rest.RestResource;
import com.ovaledge.csp.v3.core.apps.utils.Utils;
import com.ovaledge.csp.v3.core.model.ConnectionConfig;
import com.ovaledge.csp.v3.core.model.RestConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpStatusCodeException;

public class ZohodeskClient {

    public RestConfig toRestConfig(ConnectionConfig config) {
        String accountsUrl = trimTrailingSlash(getRequired(config, ZohodeskConstants.KEY_ACCOUNTS_URL));
        String tokenEndpoint = accountsUrl + ZohodeskConstants.DEFAULT_TOKEN_ENDPOINT;
        return RestConfig.builder()
                .connectionId(config.getConnectionInfoId())
                .serverType(ZohodeskConstants.SERVER_TYPE)
                .authenticationType(RestAuthenticationType.OAUTH2_REFRESH_TOKEN)
                .tokenEndpoint(tokenEndpoint)
                .clientId(getRequired(config, ZohodeskConstants.KEY_CLIENT_ID))
                .clientSecret(getRequired(config, ZohodeskConstants.KEY_CLIENT_SECRET))
                .refreshToken(getRequired(config, ZohodeskConstants.KEY_REFRESH_TOKEN))
                .scope(getOrDefault(config, ZohodeskConstants.KEY_SCOPE, ZohodeskConstants.DEFAULT_SCOPE))
                .grantType(ZohodeskConstants.DEFAULT_GRANT_TYPE)
                .responseTokenKey("access_token")
                .responseExpiryKey("expires_in")
                .responseRefreshTokenKey("refresh_token")
                .build();
    }

    public boolean validateConnection(ConnectionConfig config) {
        listEntities(config, ZohodeskConstants.ENDPOINT_ORGANIZATIONS, null, null, false);
        listEntities(config, ZohodeskConstants.ENDPOINT_DEPARTMENTS, 1, 0, true);
        return true;
    }

    public List<Map<String, Object>> listEntities(
            ConnectionConfig config,
            String endpoint,
            Integer limit,
            Integer offset,
            boolean includeOrgId) {

        String url = buildApiUrl(config, endpoint);
        Map<String, String> query = new LinkedHashMap<>();
        if (limit != null || offset != null) {
            int resolvedLimit = normalizeLimit(limit);
            int from = toZohoFrom(offset);
            query.put("from", String.valueOf(from));
            query.put("limit", String.valueOf(resolvedLimit));
        }
        Map<String, Object> response = exchangeForMap(config, url, HttpMethod.GET, null, includeOrgId, query);
        return extractDataRows(response);
    }

    public List<Map<String, Object>> listEntitiesWithQuery(
            ConnectionConfig config,
            String endpoint,
            Map<String, String> queryParams,
            boolean includeOrgId) {
        String url = buildApiUrl(config, endpoint);
        Map<String, Object> response = exchangeForMap(
                config,
                url,
                HttpMethod.GET,
                null,
                includeOrgId,
                queryParams != null ? queryParams : Collections.emptyMap());
        return extractDataRows(response);
    }

    private static List<Map<String, Object>> extractDataRows(Map<String, Object> response) {
        Object data = response.get("data");
        if (!(data instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object row : list) {
            if (row instanceof Map<?, ?> rawMap) {
                Map<String, Object> typed = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    typed.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                rows.add(typed);
            }
        }
        return rows;
    }

    public Long fetchCount(ConnectionConfig config, String endpoint, boolean includeOrgId, String countKey) {
        try {
            String url = buildApiUrl(config, endpoint);
            Map<String, Object> response = exchangeForMap(config, url, HttpMethod.GET, null, includeOrgId, Collections.emptyMap());
            Object value = response.get(countKey);
            if (value == null && response.get("data") instanceof Map<?, ?> data) {
                value = data.get(countKey);
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value != null) {
                return Long.parseLong(String.valueOf(value));
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    public Map<String, Object> getObject(ConnectionConfig config, String endpoint, boolean includeOrgId) {
        String url = buildApiUrl(config, endpoint);
        return exchangeForMap(config, url, HttpMethod.GET, null, includeOrgId, Collections.emptyMap());
    }

    public static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return ZohodeskConstants.DEFAULT_PAGE_SIZE;
        }
        if (limit > ZohodeskConstants.MAX_PAGE_SIZE) {
            return ZohodeskConstants.MAX_PAGE_SIZE;
        }
        return Math.max(limit, ZohodeskConstants.MIN_PAGE_SIZE);
    }

    public static int toZohoFrom(Integer offset) {
        int safeOffset = offset != null && offset >= 0 ? offset : 0;
        return safeOffset + 1;
    }

    public static boolean isRetryableStatus(int statusCode) {
        return statusCode == 429 || statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    public static boolean shouldEvictResource(int statusCode) {
        return statusCode == 401;
    }

    private Map<String, Object> exchangeForMap(
            ConnectionConfig config,
            String url,
            HttpMethod method,
            Object body,
            boolean includeOrgId,
            Map<String, String> queryParams) {

        int attempts = 0;
        Exception lastError = null;
        while (attempts < ZohodeskConstants.MAX_RETRIES) {
            attempts++;
            try {
                RestResource resource = getRestResource(config);
                String accessToken = resource.getAccessToken();
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Zoho-oauthtoken " + accessToken);
                if (includeOrgId) {
                    headers.set("orgId", getRequired(config, ZohodeskConstants.KEY_ORG_ID));
                }
                HttpEntity<?> entity = new HttpEntity<>(body, headers);
                Map response = resource.exchange(withQuery(url, queryParams), method, entity, Map.class);
                Map<String, Object> result = new LinkedHashMap<>();
                if (response != null) {
                    for (Object key : response.keySet()) {
                        result.put(String.valueOf(key), response.get(key));
                    }
                }
                return result;
            } catch (HttpStatusCodeException statusEx) {
                HttpStatusCode status = statusEx.getStatusCode();
                if (shouldEvictResource(status.value())) {
                    evict(config);
                }
                if (attempts < ZohodeskConstants.MAX_RETRIES && isRetryableStatus(status.value())) {
                    sleepWithBackoff(resolveRetryDelayMs(statusEx.getResponseHeaders(), attempts));
                    continue;
                }
                lastError = statusEx;
                break;
            } catch (Exception ex) {
                int parsedStatus = parseStatusCode(ex);
                if (parsedStatus > 0 && shouldEvictResource(parsedStatus)) {
                    evict(config);
                }
                if (attempts < ZohodeskConstants.MAX_RETRIES && parsedStatus > 0 && isRetryableStatus(parsedStatus)) {
                    sleepWithBackoff(resolveRetryDelayMs(null, attempts));
                    continue;
                }
                lastError = ex;
                break;
            }
        }
        throw new RuntimeException(lastError != null ? lastError.getMessage() : "Zoho Desk call failed", lastError);
    }

    private RestResource getRestResource(ConnectionConfig config) {
        Object resource = ConnectionPoolManager.getInstance()
                .getOrCreateResource(toRestConfig(config), ResourceType.REST);
        if (!(resource instanceof RestResource restResource)) {
            throw new IllegalStateException("REST resource was not created for Zoho Desk connection");
        }
        return restResource;
    }

    private static int parseStatusCode(Exception ex) {
        if (ex == null || ex.getMessage() == null) {
            return -1;
        }
        String message = ex.getMessage();
        if (message.contains(" 429 ")) return 429;
        if (message.contains(" 401 ")) return 401;
        if (message.contains(" 500 ")) return 500;
        if (message.contains(" 502 ")) return 502;
        if (message.contains(" 503 ")) return 503;
        if (message.contains(" 504 ")) return 504;
        return -1;
    }

    private static long resolveRetryDelayMs(HttpHeaders headers, int attempt) {
        if (headers != null) {
            String retryAfter = headers.getFirst("Retry-After");
            if (Utils.isNotBlank(retryAfter)) {
                try {
                    long seconds = Long.parseLong(retryAfter.trim());
                    return Math.min(seconds * 1000L, ZohodeskConstants.RETRY_MAX_MS);
                } catch (NumberFormatException ignored) {
                    // fallback to exponential backoff
                }
            }
        }
        long delay = ZohodeskConstants.RETRY_BASE_MS * (1L << Math.max(0, attempt - 1));
        return Math.min(delay, ZohodeskConstants.RETRY_MAX_MS);
    }

    private static void sleepWithBackoff(long delayMs) {
        try {
            Thread.sleep(Math.max(delayMs, ZohodeskConstants.RETRY_BASE_MS));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static void evict(ConnectionConfig config) {
        if (config != null && config.getConnectionInfoId() != null) {
            ConnectionPoolManager.getInstance().removeResource(config.getConnectionInfoId());
        }
    }

    private static String buildApiUrl(ConnectionConfig config, String endpoint) {
        String baseUrl = trimTrailingSlash(getOrDefault(config, ZohodeskConstants.KEY_BASE_URL, ZohodeskConstants.DEFAULT_BASE_URL));
        if (!endpoint.startsWith("/")) {
            return baseUrl + "/" + endpoint;
        }
        return baseUrl + endpoint;
    }

    private static String withQuery(String url, Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return url;
        }
        StringBuilder builder = new StringBuilder(url);
        builder.append(url.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (!first) {
                builder.append("&");
            }
            first = false;
            builder.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return builder.toString();
    }

    public static String getRequired(ConnectionConfig config, String key) {
        String value = get(config, key);
        if (Utils.isBlank(value)) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    public static String getOrDefault(ConnectionConfig config, String key, String defaultValue) {
        String value = get(config, key);
        return Utils.isBlank(value) ? defaultValue : value;
    }

    public static String get(ConnectionConfig config, String key) {
        if (config == null || key == null) {
            return null;
        }
        Map<String, String> attrs = config.getAdditionalAttributes();
        if (attrs == null) {
            return null;
        }
        String value = attrs.get(key);
        return value == null ? null : value.trim();
    }

    public static String trimTrailingSlash(String value) {
        if (value == null) return null;
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
