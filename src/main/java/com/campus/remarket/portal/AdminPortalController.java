package com.campus.remarket.portal;

import com.campus.remarket.dto.AdminActionDto;
import com.campus.remarket.portal.StaffPortalService.ShopView;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminPortalController {
    private final StaffPortalService service;

    public AdminPortalController(StaffPortalService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return service.adminHome();
    }

    @GetMapping("/history")
    public List<AdminActionDto> history() {
        return service.adminHistory();
    }

    public record Approval(Boolean approved, Long version, String note) {}

    @PutMapping("/shops/{id}/approval")
    public ShopView updateApproval(
            @PathVariable Long id,
            @RequestBody Approval input,
            @RequestHeader(value = "X-CSRF-Token", required = false) String csrf) {
        return service.approval(id, input.approved(), input.version(), input.note(), csrf);
    }

    public record Assignment(Long ownerId, Long version, String note) {}

    @PutMapping("/shops/{id}/owner")
    public ShopView assignOwner(
            @PathVariable Long id,
            @RequestBody Assignment input,
            @RequestHeader(value = "X-CSRF-Token", required = false) String csrf) {
        return service.assign(id, input.ownerId(), input.version(), input.note(), csrf);
    }
}
