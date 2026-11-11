-- Service reference data for registration and category selectors. A completed
-- catalog is not repopulated if an administrator later renames or removes an entry.
INSERT INTO school (name, region, email_domain, campus_code, address, latitude, longitude, version)
SELECT s.name, s.region, s.domain, s.code, s.address, s.lat, s.lng, 0
FROM (
    SELECT '인하대학교' AS name, '인천 미추홀구' AS region, 'inha.edu,inha.ac.kr' AS domain,
           'INHA' AS code, '인천광역시 미추홀구 인하로 100' AS address, 37.4501 AS lat, 126.6537 AS lng
    UNION ALL SELECT '인천대학교','인천 연수구','inu.ac.kr','INU','인천광역시 연수구 아카데미로 119',37.3751446,126.6323415
    UNION ALL SELECT '연세대학교 국제캠퍼스','인천 연수구','yonsei.ac.kr',NULL,NULL,NULL,NULL
    UNION ALL SELECT '경인교육대학교','인천 계양구','ginue.ac.kr',NULL,NULL,NULL,NULL
    UNION ALL SELECT '인천가톨릭대학교','인천 연수구','iccu.ac.kr',NULL,NULL,NULL,NULL
    UNION ALL SELECT '청운대학교 인천캠퍼스','인천 미추홀구','chungwoon.ac.kr','CHUNGWOON','인천광역시 미추홀구 숙골로 113',37.4719042,126.6608009
    UNION ALL SELECT '인하공업전문대학','인천 미추홀구','inhatc.ac.kr',NULL,NULL,NULL,NULL
    UNION ALL SELECT '재능대학교','인천 동구','jeiu.ac.kr',NULL,NULL,NULL,NULL
    UNION ALL SELECT '경인여자대학교','인천 계양구','kiwu.ac.kr',NULL,NULL,NULL,NULL
) s
WHERE NOT EXISTS (SELECT 1 FROM catalog_meta WHERE name = 'schools-initial')
  AND NOT EXISTS (SELECT 1 FROM school existing WHERE existing.name = s.name OR existing.campus_code = s.code);
INSERT INTO catalog_meta (name) SELECT 'schools-initial'
WHERE NOT EXISTS (SELECT 1 FROM catalog_meta WHERE name = 'schools-initial');

INSERT INTO board_categories (category_id, category_name)
SELECT d.id, d.name
FROM (
    SELECT 1 AS id, '학교' AS name
    UNION ALL SELECT 2,'주변' UNION ALL SELECT 3,'취업팁' UNION ALL SELECT 4,'고민상담'
    UNION ALL SELECT 5,'제안' UNION ALL SELECT 6,'맛집 추천' UNION ALL SELECT 7,'술집 추천'
    UNION ALL SELECT 8,'가성비' UNION ALL SELECT 9,'공지' UNION ALL SELECT 10,'자유게시판'
) d
WHERE NOT EXISTS (SELECT 1 FROM catalog_meta WHERE name = 'hosted-initial-categories')
  AND NOT EXISTS (SELECT 1 FROM board_categories existing WHERE existing.category_id = d.id OR existing.category_name = d.name);
INSERT INTO catalog_meta (name) SELECT 'hosted-initial-categories'
WHERE NOT EXISTS (SELECT 1 FROM catalog_meta WHERE name = 'hosted-initial-categories');

INSERT INTO business_categories (name)
SELECT d.name
FROM (
    SELECT '카페' AS name UNION ALL SELECT '편의점' UNION ALL SELECT '중식' UNION ALL SELECT '양식'
    UNION ALL SELECT '디저트' UNION ALL SELECT '호프' UNION ALL SELECT '주점' UNION ALL SELECT '당구장' UNION ALL SELECT '탁구장'
) d
WHERE NOT EXISTS (SELECT 1 FROM catalog_meta WHERE name = 'categories-v1')
  AND NOT EXISTS (SELECT 1 FROM business_categories existing WHERE existing.name = d.name);
INSERT INTO catalog_meta (name) SELECT 'categories-v1'
WHERE NOT EXISTS (SELECT 1 FROM catalog_meta WHERE name = 'categories-v1');
