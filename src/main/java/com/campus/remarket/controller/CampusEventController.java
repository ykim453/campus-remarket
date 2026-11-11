package com.campus.remarket.controller;

import com.campus.remarket.portal.StaffAccess;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import demo.campus.campusremarketmember.member.entity.Member;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/campus/events")
public class CampusEventController {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StudentEvent(
            String title,
            @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate start,
            String category,
            String location,
            String audience) {}

    public record CalendarPage(List<StudentEvent> events, String csrfToken) {}

    private final StaffAccess access;
    private final JdbcTemplate db;

    public CampusEventController(StaffAccess access, JdbcTemplate db) {
        this.access = access;
        this.db = db;
    }

    @GetMapping
    public CalendarPage list() {
        var student = currentStudent();
        var events =
                db.query(
                        """
                        SELECT title, event_date, category, location, audience
                        FROM student_campus_events
                        WHERE school_id = ?
                        ORDER BY event_date, id
                        """,
                        (rs, row) ->
                                new StudentEvent(
                                        rs.getString("title"),
                                        rs.getObject("event_date", LocalDate.class),
                                        rs.getString("category"),
                                        rs.getString("location"),
                                        rs.getString("audience")),
                        student.getSchool().getId());
        return new CalendarPage(events, access.csrf());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudentEvent create(
            @RequestBody StudentEvent input,
            @RequestHeader(value = "X-CSRF-Token", required = false) String token) {
        var student = currentStudent();
        access.verify(token);
        if (input.start() == null
                || input.start().getYear() < 1000
                || input.start().getYear() > 9999) {
            throw new IllegalArgumentException("행사 날짜를 확인해 주세요.");
        }
        String category = input.category() == null ? "행사" : input.category();
        if (!"행사".equals(category) && !"축제".equals(category)) {
            throw new IllegalArgumentException("행사 종류를 선택해 주세요.");
        }
        var event =
                new StudentEvent(
                        text(input.title(), 100, "행사명"),
                        input.start(),
                        category,
                        text(input.location(), 120, "장소"),
                        text(input.audience(), 120, "참여 대상"));
        db.update(
                """
                INSERT INTO student_campus_events (school_id, author_member_id, title, event_date, category, location, audience)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                student.getSchool().getId(),
                student.getId(),
                event.title(),
                event.start(),
                event.category(),
                event.location(),
                event.audience());
        return event;
    }

    private Member currentStudent() {
        var member = access.current();
        if (!member.isStudent() || member.getSchool() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "학생 계정으로 학교 행사를 이용해 주세요.");
        }
        return member;
    }

    private String text(String value, int maxLength, String label) {
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.isEmpty() || trimmed.length() > maxLength) {
            throw new IllegalArgumentException(label + " 항목은 1~" + maxLength + "자로 입력해 주세요.");
        }
        return trimmed;
    }
}
