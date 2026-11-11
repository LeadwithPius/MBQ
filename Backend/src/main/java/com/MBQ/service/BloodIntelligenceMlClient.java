package com.MBQ.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Optional bridge to the Python/XGBoost forecasting service.
 * A missing or unavailable ML service never prevents the blood-bank API from working.
 */
@Component
public class BloodIntelligenceMlClient {
    private static final Logger log = LoggerFactory.getLogger(BloodIntelligenceMlClient.class);

    private final RestClient client;
    private final String apiKey;

    public BloodIntelligenceMlClient(
            @Value("${app.blood-intelligence.ml-url:}") String baseUrl,
            @Value("${app.blood-intelligence.ml-api-key:}") String apiKey) {
        if (baseUrl == null || baseUrl.isBlank()) {
            this.client = null;
        } else {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3)).build();
            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
            requestFactory.setReadTimeout(Duration.ofSeconds(20));
            this.client = RestClient.builder()
                    .baseUrl(baseUrl.replaceAll("/+$", ""))
                    .requestFactory(requestFactory)
                    .build();
        }
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    public MlResponse forecast(List<SeriesInput> series) {
        if (series == null || series.isEmpty()) return null;
        return post("/forecast", new ForecastRequest(series));
    }

    public MlResponse demoForecast() {
        return post("/demo-forecast", null);
    }

    private MlResponse post(String path, Object body) {
        if (client == null) return null;
        try {
            RestClient.RequestBodySpec request = client.post().uri(path);
            if (!apiKey.isBlank()) request.header("X-API-Key", apiKey);
            if (body == null) return request.retrieve().body(MlResponse.class);
            return request.contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(MlResponse.class);
        } catch (RestClientException ex) {
            log.warn("XGBoost service request {} failed: {}", path, ex.getMessage());
            return null;
        }
    }

    public record ForecastRequest(List<SeriesInput> series) {}
    public record SeriesInput(Long stockId, Long facilityId, String bloodGroup,
            List<DailyDemand> dailyDemand) {}
    public record DailyDemand(LocalDate date, int unitsIssued) {}
    public record MlResponse(String status, String modelVersion, Double validationMae,
            Double baselineMae, String message, List<StockForecast> forecasts,
            Boolean synthetic, String dataSource, Integer seriesCount,
            Integer facilityCount, Integer bloodGroups, Integer historyDays,
            String notice) {}
    public record StockForecast(Long stockId, int predictedDemand7Days,
            int predictedDemand14Days) {}
}
