package com.campus.remarket.service;

import com.campus.remarket.dao.BoardDao;
import com.campus.remarket.dto.BoardCategory;
import com.campus.remarket.dto.BoardCommentDto;
import com.campus.remarket.dto.BoardDto;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BoardService {
    private final BoardDao dao;
    private final CampusScope scope;

    public BoardService(BoardDao dao, CampusScope scope) {
        this.dao = dao;
        this.scope = scope;
    }

    public List<BoardDto> selectAll(Integer categoryId) {
        Long schoolId = scope.admin() ? null : scope.id();
        return dao.selectAll(schoolId, categoryId);
    }

    public BoardDto selectOne(Long id) {
        BoardDto post = dao.selectOne(id);
        if (post == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }
        scope.require(post.getSchoolId());
        return post;
    }

    public List<BoardCategory> categories() {
        return dao.categories();
    }

    @Transactional
    public void insert(BoardDto post) {
        post.setSchoolId(scope.id());
        var author = scope.member();
        if (author != null) {
            post.setAuthorMemberId(author.getId());
            post.setAuthorName(author.getName());
            post.setSchoolName(
                    author.getSchool() == null ? "캠퍼스 이웃" : author.getSchool().getName());
        }
        validate(post);
        dao.insert(post);
    }

    @Transactional
    public void update(BoardDto post) {
        validate(post);
        selectOne(post.getPostId());
        dao.update(post);
    }

    @Transactional
    public void delete(Long id) {
        selectOne(id);
        dao.delete(id);
    }

    public List<BoardCommentDto> comments(Long postId) {
        selectOne(postId);
        return dao.comments(postId);
    }

    @Transactional
    public BoardCommentDto addComment(BoardCommentDto comment) {
        selectOne(comment.getPostId());
        if (comment.getMemberId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요해요.");
        }
        comment.setContent(text(comment.getContent(), "댓글", 1000));
        dao.insertComment(comment);
        return dao.comment(comment.getCommentId());
    }

    @Transactional
    public void deleteComment(Long postId, Long commentId, Long memberId) {
        selectOne(postId);
        BoardCommentDto comment = dao.comment(commentId);
        if (comment == null || !postId.equals(comment.getPostId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없어요.");
        }
        if (!memberId.equals(comment.getMemberId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "내가 쓴 댓글만 삭제할 수 있어요.");
        }
        dao.deleteComment(commentId, memberId);
    }

    private void validate(BoardDto post) {
        requireText(post.getWriter(), "작성자 ID");
        post.setTitle(text(post.getTitle(), "제목", 150));
        post.setContent(text(post.getContent(), "내용", 3000));
        if (post.getCategoryId() == null || !dao.categoryExists(post.getCategoryId())) {
            throw new IllegalArgumentException("게시판 분류를 선택해 주세요.");
        }
    }

    private String text(String value, String label, int limit) {
        requireText(value, label);
        String trimmed = value.strip();
        if (trimmed.length() > limit) {
            throw new IllegalArgumentException(label + "은 " + limit + "자까지 입력할 수 있어요.");
        }
        return trimmed;
    }

    private void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "을 입력하세요.");
        }
    }
}
