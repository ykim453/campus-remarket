# 캠퍼스 리마켓 학생 페이지 모바일 UX 리디자인 계획

## 구현 범위

학생 페이지(`/sales/selectAll`)의 기존 상품 조회·검색·카테고리·정렬·할인 필터·학교 일정 등록 기능은 그대로 두고, 모바일에서 다음 흐름이 한 손으로 읽히고 조작되도록 화면 계층과 간격을 개선한다.

- 상품 카드는 모바일 2열을 유지하되 카드 사이의 가로·세로 여백, 이미지 비율, 제목·가격·메타 정보의 리듬을 일관되게 조정한다.
- 전역 검색 입력은 충분한 터치 높이와 삭제 버튼 상태를 제공하고, 검색·카테고리·정렬·할인 필터가 서로 섞이지 않도록 모바일 컨트롤을 명확한 순서로 배치한다.
- 학교생활 달력의 `＋ 일정 등록` CTA는 안내 문구 아래 전체 폭의 고대비 버튼으로 보여 주고, 키보드 포커스·비활성·터치 상태를 보강한다.
- 기존 JavaScript 이벤트와 API 경로는 변경하지 않는다. 스타일과 최소한의 시맨틱 속성만 조정해 회귀 범위를 제한한다.

## 디자인 결정

- **Design Movement**: 캠퍼스 편집숍의 조용한 에디토리얼 스타일을 모바일 정보 밀도에 맞게 재구성한다.
- **Core Principles**: 넉넉한 여백, 한 번에 한 가지 선택, 짧은 정보 위계, 손가락 친화적 컨트롤.
- **Color Philosophy**: 기존 숲색을 주요 행동과 현재 상태에만 사용하고, 배경은 따뜻한 백색과 연한 녹색으로 분리해 상품 사진과 CTA가 먼저 보이게 한다.
- **Layout Paradigm**: 검색 → 필터 → 결과 순서의 세로 흐름과 좌우로 넘길 수 있는 카테고리 행을 사용한다.
- **Signature Elements**: 둥근 검색 쉘, 가로 스크롤 필터 칩, 일정 등록용 풀폭 CTA.
- **Interaction Philosophy**: 검색은 입력 즉시 반영하되 삭제·필터·정렬은 독립적인 명확한 컨트롤로 유지한다.
- **Animation**: 기존 호버 효과는 유지하고, 모바일에는 과한 이동을 추가하지 않는다. `prefers-reduced-motion` 사용자의 현재 경험을 깨지 않는다.
- **Typography System**: 기존 Malgun Gothic 기반의 한글 가독성을 유지하고, 상품명·가격·상태를 단계적으로 구분한다.
- **Brand Essence**: 같은 캠퍼스 안에서 다시 쓰임을 연결하는 학생 중심 리마켓. 친근함, 실용적, 순환적.
- **Brand Voice**: CTA는 짧고 행동 지향적으로, 안내 문구는 부담 없이 쓴다. 예: “필요한 물건을 찾아보세요”, “우리 학교 일정도 함께 알려요.”
- **Signature Brand Color**: 기존 `#24694f` 캠퍼스 그린.

## 프로젝트 구조

- `src/main/resources/templates/pages/student.html`: 학생 포털의 시맨틱 구조와 자산 로딩.
- `src/main/resources/static/preview/style.css`: 공통 레이아웃, 검색, 상품 그리드의 기본 반응형 스타일.
- `src/main/resources/static/preview/features.css`: 필터·상품 메타·부가 기능의 반응형 보정.
- `src/main/resources/static/preview/campus-calendar.css`: 학교 일정 CTA와 달력 레이아웃.
- `src/main/resources/static/student-ux.js`: 검색어 삭제 버튼 등 기존 보조 UX.
- `src/main/resources/static/preview/app.js`, `features.js`, `campus-calendar.js`: 기존 데이터 로딩·검색·필터·등록 동작(변경하지 않음).

## 검증 기준

요청한 세 영역이 모바일 폭에서 가로 스크롤이나 컨트롤 겹침 없이 읽히고, 기존 검색·필터·정렬·할인 필터·학교 일정 등록 동작을 유지하며, Gradle 회귀 테스트와 정적 소스 검사를 통과하도록 한다.
