package com.campus.remarket.portal;

import com.campus.remarket.dto.ShopChangeDto;
import com.campus.remarket.portal.StaffPortalService.ShopInput;
import com.campus.remarket.portal.StaffPortalService.ShopView;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/owner")
public class OwnerPortalController {
    private final StaffPortalService service;

    public OwnerPortalController(StaffPortalService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return service.ownerHome();
    }

    @GetMapping("/shop/history")
    public List<ShopChangeDto> history() {
        return service.history();
    }

    @PostMapping("/shop")
    public ShopView create(
            @RequestBody ShopInput input,
            @RequestHeader(value = "X-CSRF-Token", required = false) String csrf) {
        return service.create(input, csrf);
    }

    @PutMapping("/shop")
    public ShopView update(
            @RequestBody ShopInput input,
            @RequestHeader(value = "X-CSRF-Token", required = false) String csrf) {
        return service.update(input, csrf, true);
    }

    @PutMapping("/shop/operations")
    public ShopView updateOperations(
            @RequestBody ShopInput input,
            @RequestHeader(value = "X-CSRF-Token", required = false) String csrf) {
        return service.update(input, csrf, false);
    }

    public record Status(String status, Long version) {}

    @PutMapping("/shop/status")
    public ShopView updateStatus(
            @RequestBody Status input,
            @RequestHeader(value = "X-CSRF-Token", required = false) String csrf) {
        return service.status(input.status(), input.version(), csrf);
    }
}
