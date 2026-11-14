package com.projecthk5.sellerservice.voucher;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Seller-only C1 CRUD. Every service call binds Store to a verified JWT owner; not the request body. */
@RestController
@RequestMapping("/api/stores/me/vouchers")
public class VoucherController {
    private final VoucherService service;
    private final VoucherJson json = new VoucherJson();

    public VoucherController(VoucherService service) { this.service = service; }

    @GetMapping
    public List<VoucherService.View> list(@RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return service.list(status, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VoucherService.View create(@RequestBody String body) {
        return service.create(json.create(body));
    }

    @GetMapping("/{id}")
    public VoucherService.View get(@PathVariable UUID id) { return service.get(id); }

    @PutMapping("/{id}")
    public VoucherService.View edit(@PathVariable UUID id, @RequestBody String body) {
        VoucherJson.Edit edit = json.edit(body);
        return service.edit(id, edit.expectedVersion(), edit.spec());
    }

    @PostMapping("/{id}/{action}")
    public VoucherService.View act(@PathVariable UUID id, @PathVariable String action,
                                    @RequestBody String body) {
        return service.act(id, action, json.action(body));
    }
}
