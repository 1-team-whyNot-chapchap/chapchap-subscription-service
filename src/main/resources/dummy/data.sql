-- Prefix 없는 UUID v4 공개 식별자 전환 전의 로컬 Fixture를 보존 가능한 형태로 정리한다.
-- Prefix만 제거하므로 같은 로컬 업무 행과 연관관계는 유지된다.
UPDATE addresses
SET public_id = SUBSTRING(public_id, 5)
WHERE public_id REGEXP '^ADR-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$';

UPDATE plans
SET public_id = SUBSTRING(public_id, 5)
WHERE public_id REGEXP '^PLN-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$';

INSERT INTO delivery_methods (
    code,
    display_name
)
VALUES
    ('DIRECT', '직접 받을게요'),
    ('DOORSTEP', '문 앞 배송'),
    ('OTHER', '기타')
    ON DUPLICATE KEY UPDATE
                         code = code;
-- DUPLICATE KEY(중복키) 중복값 입력하려고 할 때 에러발생하지 않고, 값 업데이트 처리
-- code에 code 값 업데이트

INSERT INTO terms (
    terms_type,
    version_number,
    title,
    content,
    is_required,
    is_current
)
SELECT
    'NON_FACE_TO_FACE_STORAGE',
    1,
    '비대면 보관 약관',
    '챱챱은 고객이 지정한 배송지와 배달 요청사항에 따라 구독 상품을 배송합니다.

1. 고객이 배송 시 상품을 직접 수령하지 못하는 경우, 고객이 배송지에 설정한 배달 방식과 요청사항에 따라 상품이 문 앞 또는 고객이 지정한 장소에 보관될 수 있습니다.

2. 고객은 배송지마다 다음 배달 방식 중 하나를 선택합니다.
- 직접 받을게요
- 문 앞 배송
- 기타

기타를 선택한 경우 고객이 입력한 요청사항을 해당 배송지의 배달 요청사항으로 사용합니다.

3. 구독 주문은 주문이 생성될 당시 확정된 배송지와 배달 요청사항을 기준으로 처리됩니다.

4. 본 약관은 첫 구독 신청을 완료하고 첫 결제를 진행하기 전에 반드시 동의해야 하는 필수 약관입니다. 본 약관에 동의하지 않은 경우 첫 구독 신청 및 결제를 진행할 수 없습니다.

5. 고객의 동의 사실은 동의한 약관의 버전 및 동의 시각과 함께 기록됩니다. 동일한 약관 버전에 다시 동의하더라도 기존 동의 기록이 유지됩니다.

6. 구독을 통해 생성되는 주문은 해당 고객이 동의한 비대면 보관 약관 기록을 기준으로 생성됩니다.

본인은 위 내용을 확인하였으며, 배송 과정에서 직접 수령하지 못하는 경우 선택한 배달 방식 및 요청사항에 따라 상품이 문 앞 또는 지정한 장소에 보관될 수 있음에 동의합니다.',
    TRUE,
    TRUE
    WHERE NOT EXISTS (
    SELECT 1
    FROM terms
    WHERE terms_type = 'NON_FACE_TO_FACE_STORAGE'
      AND version_number = 1
);

INSERT INTO plans (
    public_id,
    name,
    description,
    unit_price
)
VALUES
    ('11111111-1111-4111-8111-111111111111', '간편식', '가볍고 빠르게 먹기 좋은 플랜', 7900),
    ('22222222-2222-4222-8222-222222222222', '가정식', '영양 구성과 메뉴 구성이 다채로운 플랜', 8900),
    ('33333333-3333-4333-8333-333333333333', '든든식', '양과 메뉴가 풍성한 플랜', 9900)
    ON DUPLICATE KEY UPDATE
                         public_id = public_id;

-- 주문은 배송일의 달력 일자와 같은 메뉴 순번을 사용하므로,
-- 로컬 환경에서도 각 플랜에 1~31 순번 메뉴가 모두 있어야 한다.
INSERT INTO menus (
    public_id,
    plan_id,
    menu_sequence,
    name,
    description,
    image_url,
    allergen_info,
    nutrition_info,
    ingredient_info
)
WITH RECURSIVE menu_sequences (menu_sequence) AS (
    SELECT 1
    UNION ALL
    SELECT menu_sequence + 1
    FROM menu_sequences
    WHERE menu_sequence < 31
)
SELECT
    CONCAT(
        LOWER(HEX(RANDOM_BYTES(4))), '-',
        LOWER(HEX(RANDOM_BYTES(2))), '-',
        '4', RIGHT(LOWER(HEX(RANDOM_BYTES(2))), 3), '-',
        '8', RIGHT(LOWER(HEX(RANDOM_BYTES(2))), 3), '-',
        LOWER(HEX(RANDOM_BYTES(6)))
    ),
    plans.id,
    menu_sequences.menu_sequence,
    CONCAT(plans.name, ' ', LPAD(menu_sequences.menu_sequence, 2, '0'), '일 메뉴'),
    CONCAT(plans.name, ' 플랜의 ', menu_sequences.menu_sequence, '일 배송용 로컬 테스트 메뉴'),
    CONCAT('https://example.com/images/menus/', plans.id, '-', menu_sequences.menu_sequence, '.jpg'),
    '로컬 테스트 데이터 — 실제 알레르기 정보 확인 필요',
    '로컬 테스트 데이터 — 실제 영양성분 정보 확인 필요',
    '로컬 테스트 데이터 — 실제 원재료 정보 확인 필요'
FROM plans
CROSS JOIN menu_sequences
WHERE plans.public_id IN (
    '11111111-1111-4111-8111-111111111111',
    '22222222-2222-4222-8222-222222222222',
    '33333333-3333-4333-8333-333333333333'
)
ON DUPLICATE KEY UPDATE
    menus.public_id = menus.public_id;

-- 2026·2027년 대한민국 공식 공휴일·대체공휴일 기준 데이터
-- 출처: 우주항공청 월력요항(한국천문연구원 천문우주지식정보 제공)
INSERT INTO holidays (
    holiday_date,
    holiday_name,
    is_substitute_holiday
)
VALUES
    ('2026-01-01', '1월 1일', FALSE),
    ('2026-02-16', '설날 연휴', FALSE),
    ('2026-02-17', '설날', FALSE),
    ('2026-02-18', '설날 연휴', FALSE),
    ('2026-03-01', '3·1절', FALSE),
    ('2026-03-02', '대체공휴일(3·1절)', TRUE),
    ('2026-05-05', '어린이날', FALSE),
    ('2026-05-24', '부처님오신날', FALSE),
    ('2026-05-25', '대체공휴일(부처님오신날)', TRUE),
    ('2026-06-03', '전국동시지방선거', FALSE),
    ('2026-06-06', '현충일', FALSE),
    ('2026-08-15', '광복절', FALSE),
    ('2026-08-17', '대체공휴일(광복절)', TRUE),
    ('2026-09-24', '추석 연휴', FALSE),
    ('2026-09-25', '추석', FALSE),
    ('2026-09-26', '추석 연휴', FALSE),
    ('2026-10-03', '개천절', FALSE),
    ('2026-10-05', '대체공휴일(개천절)', TRUE),
    ('2026-10-09', '한글날', FALSE),
    ('2026-12-25', '기독탄신일', FALSE),
    ('2027-01-01', '1월 1일', FALSE),
    ('2027-02-06', '설날 연휴', FALSE),
    ('2027-02-07', '설날', FALSE),
    ('2027-02-08', '설날 연휴', FALSE),
    ('2027-02-09', '대체공휴일(설날)', TRUE),
    ('2027-03-01', '3·1절', FALSE),
    ('2027-05-01', '노동절', FALSE),
    ('2027-05-03', '대체공휴일(노동절)', TRUE),
    ('2027-05-05', '어린이날', FALSE),
    ('2027-05-13', '부처님오신날', FALSE),
    ('2027-06-06', '현충일', FALSE),
    ('2027-07-17', '제헌절', FALSE),
    ('2027-07-19', '대체공휴일(제헌절)', TRUE),
    ('2027-08-15', '광복절', FALSE),
    ('2027-08-16', '대체공휴일(광복절)', TRUE),
    ('2027-09-14', '추석 연휴', FALSE),
    ('2027-09-15', '추석', FALSE),
    ('2027-09-16', '추석 연휴', FALSE),
    ('2027-10-03', '개천절', FALSE),
    ('2027-10-04', '대체공휴일(개천절)', TRUE),
    ('2027-10-09', '한글날', FALSE),
    ('2027-10-11', '대체공휴일(한글날)', TRUE),
    ('2027-12-25', '기독탄신일', FALSE),
    ('2027-12-27', '대체공휴일(기독탄신일)', TRUE) AS incoming
ON DUPLICATE KEY UPDATE
    holiday_name = incoming.holiday_name,
    is_substitute_holiday = incoming.is_substitute_holiday;
