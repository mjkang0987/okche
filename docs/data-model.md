# 데이터 구조

iOS(SwiftData)와 안드로이드(Room)가 똑같이 따르는 저장 구조. 백업 파일(JSON)도 이 구조 그대로 쓴다.
화면 기준은 디자인 캔버스 '완성 디자인' 페이지, 기능 기준은 기획 문서. 셋이 다르면 디자인을 먼저 고친다.

> 상태: **초안 (검토 전)**. 맨 아래 '확인 필요'를 정하면 확정한다.

## 1. 공통 규칙

### 1-1. 모든 저장 대상이 갖는 칸

| 칸 | 형식 | 뜻 |
|---|---|---|
| `id` | UUID 문자열 | 고유 ID. 기기에서 만든다(Swift `UUID()`, Kotlin `UUID.randomUUID()`). 정렬은 `createdAt`으로 한다. |
| `createdAt` | UTC 시각 (밀리초) | 처음 만든 때 |
| `updatedAt` | UTC 시각 (밀리초) | 마지막으로 고친 때. 고칠 때마다 바꾼다. |
| `deletedAt` | UTC 시각 또는 없음 | 지운 때. 줄은 남기고(백업 병합에서 '지웠음'도 전달해야 해서) **내용 칸은 비우고 사진 파일은 바로 지운다** — 화면이 '되돌릴 수 없어요'라고 말하므로. 화면과 통계에선 빼고 본다. |

- 시각은 모두 UTC로 저장하고, 화면에 보일 때만 기기 시간대로 바꾼다.
- 이름 규칙: 문서·백업 JSON은 `camelCase`, Swift도 `camelCase`, Kotlin도 `camelCase`, Room 테이블·열 이름은 `snake_case`.

### 1-2. 하루 기준: 새벽 6시

- 하루는 **새벽 6시부터 다음 날 새벽 6시까지**다. 탕약 줄 초기화, '오늘' 기록, 실록 일별 집계가 모두 이 기준을 따른다.
- 기록마다 `day`(`YYYY-MM-DD`)를 저장한다: 기록한 순간의 **기기 현지 시각에서 6시간을 뺀 날짜**. 예: 10/28 새벽 2시 기록 → `day = 2026-10-27`.
- `day`는 기록할 때 한 번 계산해 저장한다. 나중에 시간대를 옮겨도(해외여행) 이미 남긴 기록의 날짜는 바뀌지 않는다.

### 1-3. 누가 남겼는지 모를 때

- 기록의 `petId`는 비어 있을 수 있다 = '누군지 모름'. 기록 목록에서 '누구였는지 정하기'로 나중에 채운다.
- `petId`가 빈 기록은 어느 아이의 실록·통계에도 들어가지 않는다.

## 2. 아이 (`Pet`)

| 칸 | 형식 | 비고 |
|---|---|---|
| `name` | 글자 | 필수 |
| `species` | `cat` / `dog` | |
| `breed` | 글자 | 목록에서 고르거나 '믹스 · 잘 모르겠어요' |
| `furColor` | 글자 | 치즈·고등어·턱시도·회색 … (캐릭터 털색) |
| `birthDate` | 날짜 또는 없음 | |
| `birthEstimated` | 참/거짓 | '생일을 몰라요 (나이를 어림잡아 넣을게요)' |
| `familyDate` | 날짜 또는 없음 | 가족 된 날 (동행 N일, 가족 된 날 축하) |
| `diagnosis` | 글자 또는 없음 | 진단명 |
| `hasDiabetes` | 참/거짓 | 켜면 일기·위젯에 혈당·인슐린 칸 |
| `resting` | 참/거짓 | 휴양 중 — 알림만 멈춤, 기록은 됨 |
| `memorializedAt` | 시각 또는 없음 | '추억으로 간직하기'를 고른 때. 보호자가 직접 고를 때만 바뀐다(앱이 추측해서 바꾸지 않음). |
| `profilePhotoId` | 사진 ID 또는 없음 | |
| `sortOrder` | 정수 | 아이 바꾸기 목록 순서 |

체중은 아이 칸이 아니라 기록(`weight`)으로 남긴다. 아이 등록 때 넣은 체중도 첫 체중 기록이 된다.

**아이 지우기**: 아이와 그 아이의 기록·약 주기·인슐린·사료 바꾸기·급식기 설정에 모두 `deletedAt`을 넣는다. 사진은 그 아이를 `petIds`에서 빼고, 남은 아이가 없으면 사진도 지운다.

## 3. 기록 (`Record`)

모든 일기 기록을 한 테이블에 담는다. 실록 집계가 `petId + kind + day`로 한 번에 되도록.

| 칸 | 형식 | 비고 |
|---|---|---|
| `petId` | 아이 ID 또는 없음 | 1-3 참고 |
| `kind` | 아래 표의 값 | |
| `occurredAt` | UTC 시각 | 일어난 때. 시트의 '시간 바꾸기'로 바꿀 수 있다. |
| `day` | `YYYY-MM-DD` | 1-2 규칙 |
| `data` | JSON | 종류별 내용 (아래) |
| `photoIds` | 사진 ID 목록 | '사진 붙이기 (선택)' |
| `memo` | 글자 또는 없음 | |
| `source` | `quick` / `sheet` / `widget` / `feeder` | 어디서 남겼는지. 급식기 자동 기록은 `feeder`. |

### 종류별 `data`

| `kind` | 화면 | `data` 칸 |
|---|---|---|
| `vomit` | 구토 | `contents`: `kibble`(사료 알갱이), `digested`(죽처럼 삭은 사료), `liquidFoam`(액체·거품), `hairball`(헤어볼), `plant`(풀·식물), `foreign`(이물질) 중 여러 개 · `color`: `food`(사료 색), `yellow`, `clear`(투명·흰색), `green`, `red`(피), `darkRed`(검붉은·커피색) · `count`: 1, 2, 3(3번 이상) |
| `stool` | 매화 | `shape`: `hard`(단단해요), `normal`(정상), `soft`(물러요), `loose`(묽어요), `watery`(물 같아요) · `color`: `brown`, `yellowOrange`, `green`, `black`(짜장처럼), `pale`(회백색) · `signs`: `blood`, `mucus`, `foreign` 중 여러 개 |
| `urine` | 소피 | `amount`: `little`(찔끔), `normal`(보통), `much`(많음) · `color`: `paleYellow`(보통), `clear`, `darkYellow`, `pinkRed` · `signs`: `straining`(힘만 주고 안 나옴), `frequent`(자주 들락거림), `outside`(화장실 밖), `pain`(볼 때 울거나 아파함) 중 여러 개 |
| `meal` | 수라 | `result`: `all`(다 먹음), `some`(남김), `none`(안 먹음) · `foodId`(그때 먹던 사료) · `mealSlot`: 아침·점심·저녁 같은 끼니 이름 또는 없음 |
| `snack` | 다과 | `snackId` · `amount`: 글자 또는 없음 ('얼마나 줬나요? (선택)') |
| `weight` | 옥체 | `kg`: 소수 (소수점 둘째 자리까지) |
| `med` | 탕약 | `scheduleId`(어느 알림에서 왔는지, 예정에 없던 약이면 없음) · `medicationId` · `plannedAt`(먹일 예정이던 시각 또는 없음) · `status`: `given`(줬어요), `skipped`(건너뜀), `vomited`(먹고 토함) · `amount`, `unit` |
| `glucose` | 혈당 | `mgdl`: 정수 · `timing`: `beforeInsulin`, `afterInsulin`, `other` · `signs`: `weak`(기운 없음), `wobbly`(비틀거림·떨림), `seizure`(경련) 중 여러 개 |
| `insulin` | 인슐린 | `planSlotTime`(정해 둔 시간 `HH:mm` 또는 없음) · `units`: 소수 · `status`: `full`(다 맞음), `partial`(일부만), `skipped`(건너뜀) · `mealBefore`: `all`, `some`, `none` 또는 없음 |

- 값은 화면 글자가 아니라 위의 영어 값으로 저장한다(문구를 바꿔도 기록이 깨지지 않게).
- **기록은 보호자가 누른 것만 저장한다.** '건너뜀'·'먹고 토함'은 직접 눌렀을 때만 남고, 아무것도 안 누른 차례를 앱이 '안 줌'으로 저장하지 않는다. 몇 번 중 몇 번 줬는지는 6장 규칙으로 계산한다.

## 4. 약 (`Medication`) — 약방

| 칸 | 형식 | 비고 |
|---|---|---|
| `name` | 글자 | 필수 |
| `shortName` | 글자(6자까지) 또는 없음 | 일기 탕약 버튼에 보일 짧은 이름. 없으면 `name` |
| `category` | `medicine` / `supplement` | 약 / 보조제 |
| `form` | `pill`, `powder`, `liquid`, `topical`(연고·안약), `injection` | |
| `doseText` | 글자 | '한 번에' (예: 1알) |
| `remaining` | 숫자 또는 없음 | 지금 남은 양. 줬어요를 누르면 줄어든다. |
| `remainingUnit` | 글자 또는 없음 | 알·포·ml … |
| `photoId` | 사진 ID 또는 없음 | 약 봉투·포장 사진 |
| `memo` | 글자 또는 없음 | |

약방의 약은 어느 아이에게 줄지 정하지 않는다. 아이와 시간은 5장(주기)에서 정한다.

## 5. 약 주기 (`MedSchedule`) — 탕약 알림 · 약 추가

| 칸 | 형식 | 비고 |
|---|---|---|
| `petId` | 아이 ID | 필수 |
| `medicationId` | 약 ID | 필수 |
| `amount`, `unit` | 숫자, `pill`/`halfPill`/`ml`/`pack`/`drop` | '한 번에 얼마나' |
| `frequency` | 아래 다섯 가지 중 하나 | |
| `times` | `HH:mm` 목록 | 매일·며칠마다·요일에서 쓰는 먹일 시간 |
| `everyDays`, `startDate` | 정수(2 이상), 날짜 | 며칠마다 |
| `weekdays` | 1~7 목록 (월=1) | 요일 정해서 |
| `everyHours`, `firstAt` | 정수, UTC 시각 | 몇 시간마다 |
| `howTo` | `plain`, `inFood`, `inTreat`, `syringe` 또는 없음 | 먹이는 방법 |
| `reminder` | 참/거짓 | 알림 보내기 |
| `leadMinutes` | 0, 5, 10, 15, 30, 60 | 알림 보낼 때 (기본 10) |
| `activeFrom` | 날짜 | 이 주기를 쓰기 시작한 날 |
| `activeUntil` | 날짜 또는 없음 | 그만 쓴 날(이 날 포함 안 함) |

`frequency` 값: `daily`(매일), `everyNDays`(며칠마다), `weekdays`(요일 정해서), `everyNHours`(몇 시간마다), `asNeeded`(필요할 때만 — 줄·알림에 안 나옴).

- **주기를 바꾸면 고치지 않고 새 줄을 만든다.** 원래 줄에 `activeUntil`을 넣고, 새 줄을 `activeFrom` 그날로 만든다. 그래야 지난 기간의 '몇 번 중 몇 번'이 그때 주기로 계산된다. 실록의 '이 기간에 바뀐 것'(약)도 이 날짜에서 나온다.
- 몇 시간마다: 차례는 `firstAt`부터 `everyHours`씩. **늦게 줬으면 다음 차례도 그만큼 밀린다** = 다음 차례는 '그 앞 차례에 남긴 기록 시각(없으면 그 앞 차례 예정 시각) + `everyHours`'. 지난 기간 통계도 같은 방식으로 차례를 다시 만들어 센다.

## 6. 계산 규칙

### 6-1. 오늘 먹일 약 (일기 탕약 줄)

1. 오늘(`day`)에 먹는 날인 주기만 고른다: 매일은 늘, 며칠마다는 `(오늘 - startDate)`가 `everyDays`로 나누어떨어질 때, 요일은 오늘 요일이 `weekdays`에 있을 때, 몇 시간마다는 오늘 하루 안에 차례가 있을 때. `asNeeded`·휴양 중·추억으로 간직한 아이는 빼고, `activeFrom ≤ 오늘 < activeUntil`인 것만.
2. 각 주기의 오늘 차례(시간)를 만들고, 그 차례에 `med` 기록이 이미 있으면 뺀다(`given`·`skipped`·`vomited` 모두 — 보호자가 이미 처리한 차례).
3. 남은 차례를 먹일 시간이 이른 순으로 보여 준다. **시간이 지나도 가리지 않는다.** 새벽 6시가 지나면 새 하루로 다시 만든다.

### 6-2. 몇 번 중 몇 번 (탕약 1/3, 인슐린 57/60)

- 분모: 그 기간 각 날짜에 6-1의 1~2단계로 만든 차례 수 (그날 유효했던 주기 기준).
- 분자: 같은 기간 `med`(또는 `insulin`) 기록 중 `given`(인슐린은 `full`·`partial`) 수. `vomited`는 분자에 넣지 않고 실록에 따로 센다('먹고 토함'은 실록에 따로 세요).

### 6-3. 혈당 곡선

- 같은 아이·같은 `day`의 `glucose` 기록을 시각 순으로 이은 것. 따로 묶음 ID를 두지 않는다('같은 날 여러 번 재면 곡선으로 묶여요').

### 6-4. 바꾼 날 (실록 금색 선)

- 사료: `FoodChange.startedOn`. 약: `MedSchedule.activeFrom`(새로 만든 날)과 `activeUntil`.

## 7. 인슐린 (`InsulinPlan`) — 당뇨 아이만

| 칸 | 형식 | 비고 |
|---|---|---|
| `petId` | 아이 ID | |
| `name` | 글자 또는 없음 | '몰라도 돼요' |
| `syringe` | `u40`, `u100`, `unknown` | |
| `slots` | `{time: HH:mm, units: 소수}` 목록 | 맞는 시간과 양 |
| `reminder` | 참/거짓 | 밤에 알림 쉬기 중에도 울림 |
| `leadMinutes` | 0, 5, 10, 15, 30, 60 | |
| `activeFrom`, `activeUntil` | 날짜 | 5장과 같은 방식(바꾸면 새 줄) |

- 일기의 '맞음 · 2단위'는 지금 차례 `slot`의 양으로 `insulin` 기록(`full`)을 만든다.
- 같은 차례에 `full`·`partial` 기록이 있으면 버튼은 '✓ 08:02 맞음'으로 바뀌고, **다음 차례 1시간 전까지** 그대로 둔다(두 번 기록·두 번 맞히기 방지).

## 8. 사료·다과

**사료 (`Food`)**: `name`, `ingredientPhotoId`.

**사료 바꾸기 (`FoodChange`)**: `petId`, `foodId`, `startedOn`(먹기 시작한 날), `method`: `gradual`(조금씩 섞어서) / `atOnce`(한 번에). 지금 먹는 사료 = 그 아이의 가장 늦은 `startedOn`.

**다과 (`Snack`)**: `name`, `kind`: `lickable`(짜 먹는 간식), `freezeDried`, `jerky`(져키·스틱), `wet`(캔·파우치), `prescription`(처방 간식), `human`(사람 음식) · `okDuringFoodChange`(수라 바꾸기 중에도 괜찮은 간식) · `ingredientPhotoId`.

**급식기 (`FeederSetting`)**: `petId`, `enabled`(급식기 모드), `times`(`HH:mm` 목록). 시간이 되면 `meal` 기록(`result = all`, `source = feeder`)을 만든다. 바꾼 시간은 아직 지나지 않은 끼니부터 적용.

## 9. 사진 (`Photo`)

| 칸 | 형식 | 비고 |
|---|---|---|
| `petIds` | 아이 ID 목록 | 여러 아이가 같이 찍힐 수 있음 |
| `takenAt` | UTC 시각 | 찍은 때(없으면 올린 때) |
| `day` | `YYYY-MM-DD` | 1-2 규칙 (작년 오늘) |
| `kind` | `normal` / `body` | `body` = 체형 비교용 |
| `file` | 앱 저장 공간 안 파일 이름 | `{id}.jpg`. 원본 대신 긴 변 2048px로 줄여 저장(확인 필요) |
| `caption` | 글자 또는 없음 | 메모 |
| `goodDay` | 참/거짓 | '길일로 표시' |

기록에 붙인 사진도 같은 `Photo`이고, 기록의 `photoIds`로 연결한다.

## 10. 설정 (`Setting`, 기기 하나에 하나)

`quietHours`(밤 알림 쉬기 켜짐·시작·끝), `notify`(탕약 시간·인슐린 시간·남은 약·체중 재기·기쁜 날·작년 오늘 켜짐), `weightReminder`(요일·시각, 기본 일요일 10:00), `lowMedDays`(남은 약 알림 기준 일수, 기본 3), `lastOpenedAt`(앱을 오래 안 열면 알림을 줄이는 데 씀), `celebrateTone`(`joseon`/`plain` — 축하 카드 문구), `lastPetId`(마지막으로 보던 아이).

결제 여부는 저장하지 않는다. 앱이 켜질 때 App Store·구글 플레이에 묻는다(구매 복원 포함).

## 11. 백업과 병합

- 백업 파일 = 위 모든 표를 JSON 배열로 + 사진 파일. 본인 iCloud Drive·구글 드라이브에 저장.
- **가져오기는 덮어쓰지 않고 합친다.** 같은 `id`가 양쪽에 있으면 `updatedAt`이 늦은 쪽을 남긴다. `deletedAt`도 고친 것과 똑같이 취급한다(늦게 지웠으면 지운 게 이김).
- 이 기기에 없는 `id`는 그대로 가져온다. 사진 파일은 이 기기에 없는 것만 복사한다.
- 시각 비교는 각 기기 시계를 믿는다. 기기 시계가 크게 틀리면 늦게 고친 쪽이 질 수 있다(v1.0은 한 사람이 쓰는 백업이라 받아들임).
- 같은 규칙을 다음 버전 서버 동기화에도 그대로 쓴다.

## 12. 디자인과 맞지 않는 곳 (디자인부터 고칠 것)

1. **수라 결과 문구가 화면마다 다름** — 일기 빠른 버튼 '다 먹음·남김·안 먹음', 수라 화면 '다 먹음·반쯤·거의 안 먹음', 급식기 화면 '다 먹음·남겼어요·안 먹었어요', 인슐린 시트 '맞기 전에 밥은?' '다 먹음·조금 먹음·안 먹음'. 값은 `all/some/none` 하나로 두고 문구를 하나로 맞춰야 한다.
2. **구토 빠른 버튼과 시트의 항목이 다름** — 빠른 버튼은 '사료·거품·액체'(거품과 액체가 따로), 시트는 '액체·거품'이 하나. 빠른 버튼 '사료'가 '사료 알갱이'인지 '죽처럼 삭은 사료'인지도 정해야 한다.

## 13. 확인 필요

1. 위 12장 두 가지의 문구·항목 통일
2. 사진 저장 크기 — 원본 그대로 vs 긴 변 2048px로 줄이기(용량·백업 시간 차이 큼)
3. 체중 단위 — kg 소수 둘째 자리로 충분한지(작은 아기 고양이는 g 단위가 필요할 수 있음)
