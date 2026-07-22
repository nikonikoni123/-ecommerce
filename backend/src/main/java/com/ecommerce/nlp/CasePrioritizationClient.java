package com.ecommerce.nlp;

import com.ecommerce.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente del microservicio BERT que prioriza los casos de atencion.
 *
 * <p>El panel de casos no puede quedarse en blanco porque un servicio auxiliar no responda, asi que
 * ante cualquier fallo se degrada a una heuristica local basada en vencimiento, antiguedad y
 * palabras clave. El consumidor sabe si el resultado vino degradado.
 */
@Component
public class CasePrioritizationClient {

    private static final Logger log = LoggerFactory.getLogger(CasePrioritizationClient.class);

    private static final Pattern URGENCY_TERMS = Pattern.compile(
            "urgent|inmediat|reembols|devoluc|cancel|fraud|estaf|rob|roto|rota|danad|defectuos"
                    + "|no funciona|no lleg|perdid|extravi|demanda|abogad|legal|queja|reclam|denunc",
            Pattern.CASE_INSENSITIVE);

    private final RestClient restClient;

    public CasePrioritizationClient(AppProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.nlp().timeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.nlp().timeoutMs()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.nlp().url())
                .requestFactory(factory)
                .build();
    }

    public PrioritizeResponse prioritize(List<CaseItem> items) {
        if (items.isEmpty()) {
            return new PrioritizeResponse(List.of(), "none", false);
        }
        try {
            var response = restClient.post()
                    .uri("/prioritize")
                    .body(new PrioritizeRequest(items))
                    .retrieve()
                    .body(PrioritizeResponse.class);
            if (response != null) {
                return response;
            }
            log.warn("El servicio de priorizacion devolvio un cuerpo vacio");
        } catch (Exception e) {
            log.warn("El servicio de priorizacion no respondio ({}), se usa la heuristica local",
                    e.getMessage());
        }
        return fallback(items);
    }

    /** Heuristica local: sin lectura del tono, solo senales objetivas y palabras clave. */
    private PrioritizeResponse fallback(List<CaseItem> items) {
        Instant now = Instant.now();
        var scored = items.stream()
                .map(item -> {
                    double deadline = deadlinePressure(item, now);
                    double age = agePressure(item, now);
                    boolean keyword = item.text() != null
                            && URGENCY_TERMS.matcher(item.text().toLowerCase(Locale.ROOT)).find();

                    double score = 0.50 * deadline + 0.25 * age + 0.25 * (keyword ? 1 : 0);
                    score = Math.round(Math.min(Math.max(score, 0), 1) * 10000d) / 10000d;

                    String priority = score >= 0.65 ? "HIGH" : score >= 0.35 ? "MEDIUM" : "LOW";
                    return new PrioritizedCase(item.id(), priority, score, "NEUTRAL",
                            "Prioridad estimada sin el modelo de lenguaje");
                })
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .toList();

        return new PrioritizeResponse(scored, "heuristica-local", true);
    }

    private double deadlinePressure(CaseItem item, Instant now) {
        if (item.dueDate() == null) {
            return 0;
        }
        double hoursLeft = Duration.between(now, item.dueDate()).toMinutes() / 60d;
        if (hoursLeft <= 0) {
            return 1;
        }
        if (hoursLeft >= 72) {
            return 0;
        }
        return 1 - (hoursLeft / 72);
    }

    private double agePressure(CaseItem item, Instant now) {
        if (item.createdAt() == null) {
            return 0;
        }
        double days = Duration.between(item.createdAt(), now).toHours() / 24d;
        return Math.min(Math.max(days, 0) / 7, 1);
    }

    // ------------------------------------------------------------------ contrato

    public record CaseItem(String id, String text, Instant createdAt, Instant dueDate) {
    }

    public record PrioritizeRequest(List<CaseItem> items) {
    }

    public record PrioritizedCase(String id, String priority, double score, String sentiment,
                                  String reason) {
    }

    public record PrioritizeResponse(List<PrioritizedCase> items, String model, boolean degraded) {
    }
}
