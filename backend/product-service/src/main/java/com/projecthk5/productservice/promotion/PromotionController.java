package com.projecthk5.productservice.promotion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.projecthk5.productservice.product.ProductPrice;
import com.projecthk5.security.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class PromotionController {
    private final PromotionService service;
    private final ObjectMapper json = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature());

    public PromotionController(PromotionService service) { this.service = service; }

    @GetMapping("/api/seller/promotions")
    List<PromotionService.Campaign> sellerList() { return service.list(false); }

    @GetMapping("/api/admin/promotions")
    List<PromotionService.Campaign> adminList() { return service.list(true); }

    @GetMapping("/api/seller/promotions/{id}/products")
    List<PromotionService.Registration> sellerProducts(@PathVariable UUID id) { return service.listProducts(id, false); }

    @GetMapping("/api/admin/promotions/{id}/products")
    List<PromotionService.Registration> adminProducts(@PathVariable UUID id) { return service.listProducts(id, true); }

    @PostMapping("/api/seller/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    PromotionService.Campaign createClearance(@RequestBody String body) {
        var n = decode(body, "name", "startsAt", "endsAt");
        return service.create(required(n,"name"), PromotionPriceRules.Kind.SELLER_CLEARANCE, null,
                Instant.parse(required(n,"startsAt")), Instant.parse(required(n,"endsAt")));
    }

    @PostMapping("/api/admin/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    PromotionService.Campaign createScheduled(@RequestBody String body) {
        var n = decode(body, "name", "kind", "date");
        var kind = PromotionPriceRules.Kind.valueOf(required(n,"kind"));
        if (kind == PromotionPriceRules.Kind.SELLER_CLEARANCE) throw invalid();
        return service.create(required(n,"name"), kind, LocalDate.parse(required(n,"date")), null, null);
    }

    @PostMapping("/api/seller/promotions/{id}/submit")
    PromotionService.Campaign submit(@PathVariable UUID id, @RequestBody String body) {
        var n = decode(body, "expectedVersion");
        return service.action(id, "submit", version(n,"expectedVersion"), null);
    }

    @PatchMapping("/api/admin/promotions/{id}/schedule")
    PromotionService.Campaign reschedule(@PathVariable UUID id, @RequestBody String body) {
        var n = decode(body, "date", "expectedVersion");
        return service.reschedule(id, LocalDate.parse(required(n,"date")), version(n,"expectedVersion"));
    }

    @PostMapping("/api/admin/promotions/{id}/actions")
    PromotionService.Campaign action(@PathVariable UUID id, @RequestBody String body) {
        var n = decode(body, "action", "expectedVersion", "reason");
        String action = required(n,"action");
        if (!Set.of("approve","pause","resume","cancel").contains(action)) throw invalid();
        return service.action(id, action, version(n,"expectedVersion"), reason(n));
    }

    @PostMapping("/api/seller/promotions/{id}/products")
    PromotionService.Registration register(@PathVariable UUID id, @RequestBody String body) {
        var n = decode(body, "productId", "promotionalUnitPrice", "expectedCampaignVersion", "expectedVersion");
        return service.register(id, UUID.fromString(required(n,"productId")),
                ProductPrice.parseCanonical(required(n,"promotionalUnitPrice"), "VND"),
                version(n,"expectedCampaignVersion"), n.has("expectedVersion") ? version(n,"expectedVersion") : null);
    }

    @PostMapping("/api/seller/promotions/{id}/products/{productId}/withdraw")
    PromotionService.Registration withdraw(@PathVariable UUID id, @PathVariable UUID productId, @RequestBody String body) {
        var n = decode(body, "expectedVersion", "reason");
        return service.decide(id, productId, "withdraw", version(n,"expectedVersion"), reason(n));
    }

    @PostMapping("/api/admin/promotions/{id}/products/{productId}/decision")
    PromotionService.Registration decide(@PathVariable UUID id, @PathVariable UUID productId, @RequestBody String body) {
        var n = decode(body, "decision", "expectedVersion", "reason");
        String action = required(n,"decision");
        if (!Set.of("approve","reject").contains(action)) throw invalid();
        return service.decide(id, productId, action, version(n,"expectedVersion"), reason(n));
    }

    private JsonNode decode(String body, String... allowed) {
        try {
            JsonNode node = json.readTree(body);
            if (node == null || !node.isObject()) throw invalid();
            var fields = node.fields();
            Set<String> keys = Set.of(allowed);
            while (fields.hasNext()) {
                var field = fields.next();
                if (!keys.contains(field.getKey()) || !field.getValue().isTextual()) throw invalid();
            }
            return node;
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw invalid();
        }
    }

    private static String required(JsonNode node, String key) {
        if (!node.has(key)) throw invalid();
        return node.get(key).textValue();
    }

    private static String reason(JsonNode node) {
        if (!node.has("reason")) return null;
        String reason = required(node,"reason").strip();
        if (reason.isEmpty() || reason.length()>500) throw invalid();
        return reason;
    }

    private static long version(JsonNode node, String key) {
        String value = required(node,key);
        if (!value.matches("0|[1-9][0-9]{0,17}")) throw invalid();
        return Long.parseLong(value);
    }

    @ExceptionHandler({IllegalArgumentException.class, java.time.DateTimeException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    java.util.Map<String,String> invalidValue() {
        return java.util.Map.of("code", "VALIDATION_FAILED", "message", "Invalid promotion payload");
    }

    private static ApiException invalid() {
        return ApiException.badRequest("VALIDATION_FAILED", "Invalid promotion payload");
    }
}
