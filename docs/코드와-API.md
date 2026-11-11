# 캠퍼스 리마켓 코드 설명

## 세 화면에서 어떤 일을 하나요?

| 화면 | 기본 기능 | 추가하거나 개선한 기능 |
| --- | --- | --- |
| [학생](../src/main/resources/templates/pages/student.html) | 판매글과 게시글 등록·조회·수정·삭제 | 같은 학교 게시글 분류 조회, 댓글, 예약 재시도 중복 방지, 약속 장소 변경, 직거래 완료와 후기, 학교 행사 공유 |
| [사장·매니저](../src/main/resources/templates/pages/owner.html) | 내 가게 등록·조회·수정 | 사장은 매니저 발급, 매니저는 영업 상태 등 운영 정보 수정. 다른 사람이 먼저 수정하면 충돌을 알리고 변경 이력을 기록 |
| [관리자](../src/main/resources/templates/pages/admin.html) | 회원·가게·거래 현황 조회 | 가게 공개 승인, 소속 학교가 맞는 사장 배정, 처리 메모와 이력 조회 |

이번 정리에서 중복된 메인 HTML을 모아 역할마다 한 파일을 편집하도록 바꿨습니다. 사장·관리자의 REST 진입점도 역할별 클래스로 분리했습니다. 학생 거래는 **상품 등록 → 예약 → 대면 거래 완료 → 후기** 순서로 진행합니다. 앱에서 결제 승인, 에스크로 보관, 판매자 정산을 처리하는 기능은 제거했습니다.

## 게시글 한 건으로 보는 MyBatis

1. 학생 화면이 `GET /api/boards/27`을 보내면 [BoardApiController](../src/main/java/com/campus/remarket/controller/BoardApiController.java)의 `selectOne`이 경로에서 게시글 번호 27을 읽습니다.
2. [BoardService](../src/main/java/com/campus/remarket/service/BoardService.java)는 `dao.selectOne(id)`로 글을 조회하고 해당 학교의 글을 열 수 있는지 확인합니다.
3. [BoardDao.java](../src/main/java/com/campus/remarket/dao/BoardDao.java)는 메서드의 이름·입력·반환형을 정합니다. MyBatis가 `@Mapper` 인터페이스의 구현체를 런타임에 연결합니다.

   ```java
   BoardDto selectOne(@Param("id") Long id);
   ```

4. [BoardMapper.xml](../src/main/resources/mappers/BoardMapper.xml)의 `namespace="com.campus.remarket.dao.BoardDao"`가 인터페이스를, `id="selectOne"`이 메서드를 가리킵니다. 다음은 이 파일에서 사용하는 조회문입니다.

   ```xml
   <select id="selectOne" resultMap="board">
       <include refid="postWithCategory"/>
       WHERE p.post_id = #{id}
   </select>
   ```

   `<include>`는 게시글·분류·댓글 수를 읽는 공통 SELECT를 가져옵니다. `#{id}`는 입력값을 SQL 파라미터로 전달합니다. `resultMap`은 DB 열 `post_id` 등을 Java 객체의 `postId` 같은 필드로 옮깁니다. 등록할 때는 `<insert useGeneratedKeys="true" keyProperty="postId">`로 MySQL이 만든 글 번호를 받습니다.

5. REST 컨트롤러가 반환한 `BoardDto`는 JSON이 됩니다. 같은 게시글을 `GET /board/selectOne?id=27`로 열면 [BoardController](../src/main/java/com/campus/remarket/controller/BoardController.java)가 같은 서비스를 호출하고 Thymeleaf가 HTML을 만듭니다.

**DB 방식은 기능별로 나뉩니다.** 기본 DB는 MySQL이고 JDBC 드라이버가 연결을 담당합니다. 상품·거래·게시판·가게 등은 MyBatis의 `XDao.java`와 `XMapper.xml`을 한 쌍으로 사용합니다. 학교 행사와 예약 중복 확인은 [JdbcTemplate](../src/main/java/com/campus/remarket/controller/CampusEventController.java)으로 SQL을 실행합니다. 회원·학교·이메일 인증은 [Spring Data JPA 저장소](../src/main/java/demo/campus/campusremarketmember/member/repository)로 관리합니다.

## 브라우저에서 따라가는 게시판 REST API

학생으로 로그인한 뒤 같은 브라우저에서 학생 페이지를 엽니다. 쓰기 요청에서는 같은 세션의 `GET /api/boards/session` 응답에 있는 `csrfToken`을 `X-CSRF-Token` 헤더에 넣습니다. JSON 본문을 보낼 때는 `Content-Type: application/json`을 지정합니다.

| 용도 | 메서드와 경로 | 입력 | 성공 응답 |
| --- | --- | --- | --- |
| 목록·분류 조회 | `GET /api/boards?categoryId=6` | 선택값 `categoryId`. 6은 맛집 추천 | 200, 최신 글부터 JSON 배열. 글이 없으면 `[]` |
| 한 건 조회 | `GET /api/boards/{id}` | 경로의 글 번호 | 200, 글 객체 |
| 등록 | `POST /api/boards` | `{"categoryId":6,"title":"학생회관 점심","content":"오늘 메뉴를 추천해요."}` | 200, 생성된 `postId`와 글 내용 |
| 수정 | `PUT /api/boards/{id}` | 본인 글 번호와 등록 때와 같은 세 필드 | 200, 수정된 글 |
| 삭제 | `DELETE /api/boards/{id}` | 본인 글 번호 | 200, 빈 본문 |
| 댓글 등록 | `POST /api/boards/{id}/comments` | `{"content":"저도 가봤어요."}` | 200, `commentId`를 포함한 댓글 |

글 번호는 등록 응답에서 받은 값을 사용합니다. 등록 응답에서 확인할 대표 필드는 `postId`, `title`, `content`, `categoryId`, `categoryName`, `authorName`, `commentCount`입니다. 작성자와 소속 학교는 로그인 세션으로 결정합니다. 다른 사람의 글 수정은 403, 현재 학생이 볼 수 없는 다른 학교 글 조회는 404입니다.

## 역할별 API 예시

| 용도 | 메서드와 경로 | 입력 | 응답과 동작 |
| --- | --- | --- | --- |
| 학생의 상품 예약 | `POST /api/trades` | `{"selno":11,"meetingPlace":"학생회관 앞"}`, 헤더 `Idempotency-Key: reserve-11-a` | 200, 예약의 `orderId`, `status`, `agreedPrice`. 같은 구매자가 같은 키와 내용으로 재시도하면 같은 예약을 반환. 같은 키로 약속 내용을 바꾸면 409 |
| 사장의 영업 상태 변경 | `PUT /api/owner/shop/status` | `{"status":"영업중","version":2}`와 토큰 | 200, 갱신한 가게 정보. 매니저도 이용 가능 |
| 관리자의 가게 공개 승인 | `PUT /api/admin/shops/{id}/approval` | `{"approved":true,"version":2,"note":"가게 정보 확인"}`와 토큰 | 200, `approved`가 반영된 가게 정보. 실제 상태가 바뀌면 처리 이력 기록 |

상품 번호·가게 번호·버전은 예시입니다. 학생은 상품 목록에서 `selno`를, 사장은 `GET /api/owner/dashboard`의 `shop`에서 현재 `version`을, 관리자는 `GET /api/admin/dashboard`의 `shops`에서 가게별 `id`와 `version`을 읽습니다. 사장·관리자의 쓰기 요청은 해당 대시보드의 `csrfToken`을 `X-CSRF-Token`에 넣습니다. 버전이 오래된 채로 저장하면 409를 반환합니다.
