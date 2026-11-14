package com.projecthk5.sellerservice.voucherlimit;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Verified current JWT identity is resolved inside the service, never a request storeId. */
@RestController
public class VoucherLimitController {
    private final VoucherLimitService service;
    private final VoucherLimitJson json=new VoucherLimitJson();
    public VoucherLimitController(VoucherLimitService service){this.service=service;}

    @GetMapping("/api/stores/me/voucher-limit")
    public VoucherLimitService.LimitView limit(){return service.limit();}

    @GetMapping("/api/stores/me/voucher-limit-requests")
    public List<VoucherLimitService.RequestView> mine(
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return service.mine(page,size);
    }

    @PostMapping("/api/stores/me/voucher-limit-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public VoucherLimitService.RequestView request(
            @RequestHeader("Idempotency-Key") String idempotency,@RequestBody String body){
        return service.create(json.request(body),idempotency);
    }

    @PostMapping("/api/stores/me/voucher-limit-requests/{id}/cancel")
    public VoucherLimitService.RequestView cancel(@PathVariable UUID id,@RequestBody String body){
        return service.cancel(id,json.cancel(body));
    }

    @GetMapping("/api/admin/voucher-limit-requests")
    public List<VoucherLimitService.RequestView> queue(
            @RequestParam(defaultValue="PENDING") String status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return service.queue(status,page,size);
    }

    @GetMapping("/api/admin/voucher-limit-requests/{id}")
    public VoucherLimitService.AdminDetail detail(@PathVariable UUID id){
        return service.detail(id);
    }

    @PostMapping("/api/admin/voucher-limit-requests/{id}/{action}")
    public VoucherLimitService.RequestView decide(@PathVariable UUID id,
            @PathVariable String action,@RequestBody String body){
        if("approve".equals(action)) return service.decide(id,action,json.approve(body));
        if("reject".equals(action)) return service.decide(id,action,json.reject(body));
        throw com.projecthk5.security.ApiException.badRequest("VALIDATION_FAILED","Unknown decision");
    }
}
