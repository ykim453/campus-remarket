package com.campus.remarket.service;

import com.campus.remarket.dao.SalesDao;
import com.campus.remarket.dao.TradeDao;
import com.campus.remarket.dto.TradeDto;
import com.campus.remarket.notification.NotificationService;

import demo.campus.campusremarketmember.member.entity.Member;
import demo.campus.campusremarketmember.member.repository.MemberRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class TradeService {
    private final TradeDao trades;
    private final SalesDao sales;
    private final NotificationService notifications;
    private final CampusScope scope;
    private final MemberRepository members;

    public TradeService(
            TradeDao trades,
            SalesDao sales,
            NotificationService notifications,
            CampusScope scope,
            MemberRepository members) {
        this.trades = trades;
        this.sales = sales;
        this.notifications = notifications;
        this.scope = scope;
        this.members = members;
    }

    public List<TradeDto> selectAll() {
        return trades.selectAll().stream()
                .filter(order -> scope.visible(order.getSchoolId()))
                .toList();
    }

    public TradeDto selectOne(String id) {
        var order = trades.selectOne(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "거래가 없습니다.");
        }
        scope.require(order.getSchoolId());
        return order;
    }

    @Transactional
    public void insert(TradeDto order) {
        String buyer = order.getBuyer();
        if (buyer == null || buyer.isBlank() || buyer.length() > 50) {
            throw new IllegalArgumentException("구매자 ID를 입력하세요.");
        }
        if (order.getSelno() == null || sales.lockSale(order.getSelno()) == null) {
            throw new IllegalArgumentException("상품을 선택하세요.");
        }
        var sale = sales.selectOne(order.getSelno());
        scope.require(sale.getSchoolId());
        if (sale.getSellerMemberId() != null
                && members.findById(sale.getSellerMemberId())
                        .map(Member::isWithdrawn)
                        .orElse(false)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "탈퇴한 판매자의 상품은 예약할 수 없습니다.");
        }
        if (!"판매중".equals(sale.getStatus())) {
            throw new IllegalStateException("이미 예약되었거나 완료된 상품입니다.");
        }
        if (order.getMeetingPlace() != null && order.getMeetingPlace().length() > 150) {
            throw new IllegalArgumentException("거래 장소는 150자 이내로 입력하세요.");
        }
        validateCoordinates(order);

        order.setOrderId("campus_" + UUID.randomUUID().toString().replace("-", ""));
        // 예약 시점의 실제 상품 가격을 보관합니다.
        order.setAgreedPrice(sale.getCurrentPrice());
        trades.insert(order);
        sales.statusUpdate(order.getSelno(), "예약중");
        notifications.reservation(order, sale.getSeller(), sale.getName());
    }

    @Transactional
    public void cancel(String id) {
        var order = lockOrder(id);
        sales.lockSale(order.getSelno());
        if (trades.cancel(id) != 1) {
            throw new IllegalStateException("예약 중인 거래만 취소할 수 있습니다.");
        }
        sales.statusUpdate(order.getSelno(), "판매중");
    }

    @Transactional
    public void complete(String id) {
        var order = lockOrder(id);
        sales.lockSale(order.getSelno());
        if (trades.complete(id) != 1) {
            throw new IllegalStateException("진행 중인 거래만 완료할 수 있습니다.");
        }
        sales.statusUpdate(order.getSelno(), "거래완료");
    }

    @Transactional
    public void meeting(TradeDto order) {
        lockOrder(order.getOrderId());
        String place = order.getMeetingPlace();
        if (place == null || place.isBlank() || place.length() > 150) {
            throw new IllegalArgumentException("150자 이내의 거래 장소를 선택하세요.");
        }
        validateCoordinates(order);
        if (trades.meeting(order) != 1) {
            throw new IllegalStateException("예약 중인 거래의 장소만 변경할 수 있습니다.");
        }
    }

    private TradeDto lockOrder(String id) {
        var order = trades.lockOne(id);
        if (order == null) {
            throw new IllegalArgumentException("거래가 없습니다.");
        }
        return order;
    }

    private void validateCoordinates(TradeDto order) {
        var latitude = order.getLatitude();
        var longitude = order.getLongitude();
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("위도와 경도는 함께 선택하세요.");
        }
        if (latitude != null
                && (latitude.abs().doubleValue() > 90 || longitude.abs().doubleValue() > 180)) {
            throw new IllegalArgumentException("지도 좌표를 확인하세요.");
        }
    }
}
