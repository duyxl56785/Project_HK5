package com.projecthk5.sellerservice.voucherlimit;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/voucher-limit-requests")
public class AdminVoucherLimitController {
    private final VoucherLimitService service;
    private final VoucherLimitJson json=new VoucherLimitJson();
    public AdminVoucherLimitController(VoucherLimitService service){this.service=service;}

    @GetMapping
    public List<VoucherLimitService.RequestView> queue(@RequestParam(required=false)String status,
            @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){
        return service.adminList(status,page,size);
    }
    @GetMapping("/{id}")
    public VoucherLimitService.RequestView detail(@PathVariable UUID id){return service.detail(id);}

    @PostMapping("/{id}/{action}")
    public VoucherLimitService.RequestView decide(@PathVariable UUID id,@PathVariable String action,
            @RequestBody String body){
        if("approve".equals(action)){
            var decision=json.approve(body);
            return service.decide(id,action,decision.expectedVersion(),decision.approvedLimit(),
                    decision.approvedFrom(),decision.approvedUntil(),decision.reason());
        }
        if("reject".equals(action)){
            var rejection=json.reject(body);
            return service.decide(id,action,rejection.expectedVersion(),null,null,null,rejection.reason());
        }
        throw com.projecthk5.security.ApiException.notFound("ROUTE_NOT_FOUND","Unknown voucher limit action");
    }
}
