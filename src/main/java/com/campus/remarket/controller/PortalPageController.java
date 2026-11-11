package com.campus.remarket.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PortalPageController {
    @GetMapping("/")
    public String home() {
        return "redirect:/sales/selectAll";
    }

    @GetMapping({"/sales/selectAll", "/preview/index.html"})
    public String studentPage() {
        return "pages/student";
    }

    @GetMapping("/owner")
    public String ownerShortcut() {
        return "redirect:/owner/";
    }

    @GetMapping({"/owner/", "/owner/index.html"})
    public String ownerPage() {
        return "pages/owner";
    }

    @GetMapping("/admin")
    public String adminShortcut() {
        return "redirect:/admin/";
    }

    @GetMapping({"/admin/", "/admin/index.html"})
    public String adminPage() {
        return "pages/admin";
    }

    @GetMapping("/manager.html")
    public String managerShortcut() {
        return "redirect:/owner/#managers";
    }
}
