package com.campus.remarket.controller;

import com.campus.remarket.auth.MarketMember;
import com.campus.remarket.dto.BoardDto;
import com.campus.remarket.portal.StaffAccess;
import com.campus.remarket.service.BoardService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/board")
public class BoardController {
    private final MarketMember member;
    private final BoardService service;
    private final StaffAccess access;

    public BoardController(MarketMember member, BoardService service, StaffAccess access) {
        this.member = member;
        this.service = service;
        this.access = access;
    }

    @GetMapping("/selectAll")
    public String selectAll(@RequestParam(required = false) Integer categoryId, Model model) {
        model.addAttribute("list", service.selectAll(categoryId));
        return "board/selectAll";
    }

    @GetMapping("/selectOne")
    public String selectOne(@RequestParam Long id, Model model) {
        model.addAttribute("dto", service.selectOne(id));
        model.addAttribute("csrfToken", access.csrf());
        return "board/selectOne";
    }

    @GetMapping("/insert")
    public String insertForm(Model model) {
        return form(model, new BoardDto(), false);
    }

    @PostMapping("/insert")
    public String insert(BoardDto post, @RequestParam(required = false) String csrfToken) {
        post.setWriter(member.key());
        access.verify(csrfToken);
        service.insert(post);
        return "redirect:/board/selectAll";
    }

    @GetMapping("/update")
    public String updateForm(@RequestParam Long id, Model model) {
        member.board(id);
        return form(model, service.selectOne(id), true);
    }

    @PostMapping("/update")
    public String update(BoardDto post, @RequestParam(required = false) String csrfToken) {
        member.board(post.getPostId());
        access.verify(csrfToken);
        post.setWriter(member.key());
        service.update(post);
        return "redirect:/board/selectAll";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long id, @RequestParam(required = false) String csrfToken) {
        member.board(id);
        access.verify(csrfToken);
        service.delete(id);
        return "redirect:/board/selectAll";
    }

    private String form(Model model, BoardDto post, boolean editing) {
        model.addAttribute("dto", post);
        model.addAttribute("editing", editing);
        model.addAttribute("categories", service.categories());
        model.addAttribute("csrfToken", access.csrf());
        return "board/form";
    }
}
