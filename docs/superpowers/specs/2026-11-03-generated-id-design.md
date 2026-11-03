# 생성 ID 반영

사용자가 선택한 방식은 `@Id @GeneratedValue` 명시다. 일반 `@Id`는 기존 직접 지정 규칙을 유지한다. null ID를 자동 생성으로 간주하는 대안은 기존 입력 검증을 바꾸므로 채택하지 않는다.

DB identity 컬럼에서 생성한 키를 INSERT 직후 같은 연결에서 읽어 객체에 기록한다. 생성 필드는 Long 또는 Integer만 지원하고 초기값은 null이어야 한다. INSERT는 해당 컬럼을 제외하며 ID만 있는 엔티티는 DEFAULT VALUES를 사용한다. persist의 int 반환은 유지한다.

잘못된 어노테이션 위치와 타입은 SQL 전에 거부한다. 생성 키가 없거나 변환할 수 없으면 SQLException이다. 키 읽기와 객체 반영은 JDBC 연결 콜백 안에서 수행한다. 롤백은 DB 행만 취소하고 이미 반영된 객체 ID는 복구하지 않는다. 시퀀스 전략, Mapper 생성 키, UPDATE/DELETE는 이번 범위에 포함하지 않는다.
