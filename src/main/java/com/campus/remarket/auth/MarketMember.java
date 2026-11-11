package com.campus.remarket.auth;

import com.campus.remarket.dto.TradeDto;
import com.campus.remarket.service.BoardService;
import com.campus.remarket.service.SalesService;
import com.campus.remarket.service.TradeService;

import demo.campus.campusremarketmember.member.entity.Member;
import demo.campus.campusremarketmember.member.service.MemberService;
import demo.campus.campusremarketmember.member.util.LoginSession;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 학생 거래의 식별자는 student.unisno와 같은 실제 학번 문자열이다. */
@Component
public class MarketMember {
    private final HttpServletRequest request;
    private final MemberService members;
    private final SalesService sales;
    private final BoardService boards;
    private final TradeService trades;

    public MarketMember(
            HttpServletRequest request,
            MemberService members,
            SalesService sales,
            BoardService boards,
            TradeService trades) {
        this.request = request;
        this.members = members;
        this.sales = sales;
        this.boards = boards;
        this.trades = trades;
    }

    public Member current() {
        var session = request.getSession(false);
        if (session == null || LoginSession.memberId(session) == null) return null;
        return members.get(LoginSession.memberId(session));
    }

    public String key() {
        var me = current();
        if (me == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");
        return members.actorKey(me);
    }

    public String studentKey() {
        var me = current();
        if (me == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");
        if (!me.isStudent())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "학생 계정으로 로그인한 후 거래할 수 있어요.");
        return members.actorKey(me);
    }

    private void check(boolean allowed) {
        if (!allowed)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 글 또는 참여한 거래만 변경할 수 있어요.");
    }

    public void sale(Long id) {
        var key = studentKey();
        var sale = sales.selectOne(id);
        check(
                sale.getSellerMemberId() != null
                        ? sale.getSellerMemberId().equals(current().getId())
                        : key.equals(sale.getSeller()));
    }

    public void board(Long id) {
        var key = key();
        var post = boards.selectOne(id);
        check(
                post.getAuthorMemberId() != null
                        ? post.getAuthorMemberId().equals(current().getId())
                        : key.equals(post.getWriter()));
    }

    public void reserve(Long id) {
        check(!studentKey().equals(sales.selectOne(id).getSeller()));
    }

    public void trade(String id) {
        var me = studentKey();
        var order = trades.selectOne(id);
        check(me.equals(order.getBuyer()) || me.equals(order.getSeller()));
    }

    public List<TradeDto> visibleTrades() {
        Member student = current();
        if (student == null || !student.isStudent()) {
            return List.of();
        }
        String me = members.actorKey(student);
        return trades.selectAll().stream()
                .filter(order -> me.equals(order.getBuyer()) || me.equals(order.getSeller()))
                .toList();
    }
}
