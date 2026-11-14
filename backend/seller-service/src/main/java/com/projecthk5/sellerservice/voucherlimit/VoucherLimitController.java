package com.projecthk5.sellerservice.voucherlimit;

import com.projecthk5.security.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** C1 API: never accepts Store IDs from clients for Seller operations. */
@RestController
@RequestMapping("/api/stores/me")
public class VoucherLimitController {
    private final VoucherLimitService service;
    private final VoucherLimitJson json=new VoucherLimitJson();
    public VoucherLimitController(VoucherLimitService service){this.service=service;}

    @GetMapping("/voucher-limit")
    public VoucherLimitService.LimitView limit(){return service.limit();}

    @GetMapping("/voucher-limit-requests")
    public List<VoucherLimitService.RequestView> mine(@RequestParam(defaultValue="0")int page,
            @RequestParam(defaultValue="20")int size){return service.mine(page,size);}

    @PostMapping("/voucher-limit-requests")
    public ResponseEntity<VoucherLimitService.RequestView> submit(
            @RequestHeader(value="Idempotency-Key",required=false)String key, @RequestBody String body){
        VoucherLimitService.Submission result=service.submit(key,json.submit(body));
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED).body(result.view());
    }

    @PostMapping("/voucher-limit-requests/{id}/cancel")
    public VoucherLimitService.RequestView cancel(@PathVariable UUID id,@RequestBody String body){
        return service.cancel(id,json.cancel(body));
    }
}
