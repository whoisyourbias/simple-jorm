# 05-02. 스냅샷·변경 감지·flush 구현 계약

기준일 2026-11-04. 이 문서를 먼저 작성하고 실패 테스트, 최소 구현, 소개 페이지 갱신 순서로 진행한다.

## 수명과 관리 범위

같은 TransactionManager·스레드의 가장 바깥 REQUIRED 트랜잭션이 하나의 EntityManagerSession을 소유한다. 여러 EntityManager 진입점은 이 세션을 공유한다. find와 성공한 persist가 관리 객체와 스냅샷을 등록한다. Long·Integer·String·UUID 및 long·int ID만 관리하고 다른 ID 타입은 기존 즉시 JDBC CRUD를 유지한다. null 조회 결과는 저장하지 않는다.

## 변경 감지와 SQL 시점

- `EntityManager.flush()`는 활성 트랜잭션 안에서만 호출한다. 밖에서는 IllegalStateException이다. 빈 트랜잭션의 flush는 SQL 없이 끝난다.
- 관리 시작 당시의 매핑 필드 값과 현재 값을 비교한다. 변경된 객체에만 ID 외 전체 컬럼 UPDATE를 실행한다. 원래 값으로 되돌렸거나 변경이 없으면 UPDATE하지 않는다.
- flush 성공 후 스냅샷을 갱신한다. 다시 변경하지 않고 flush하거나 commit하면 UPDATE를 반복하지 않는다.
- 가장 바깥 commit 직전에 TransactionResource.beforeCommit → holder → session.flush를 실행한다. 내부 REQUIRED 반환이나 일반 조회 전에는 자동 flush하지 않는다.
- persist/update/remove는 기존 즉시 SQL과 int 행 수 반환을 유지한다. 성공한 명시적 update는 이미 관리 중인 객체의 스냅샷을 갱신한다. 미관리 객체의 update는 객체를 새로 관리하지 않는다. 0행 update 및 remove는 해당 키의 객체와 스냅샷을 제거한다.
- 관리 ID 변경은 CRUD 접근뿐 아니라 flush에서도 거부한다. flush는 SQL을 실행하기 전에 모든 관리 ID를 검증한다.

## 실패와 롤백

flush는 commit이 아니다. 같은 트랜잭션 연결에서만 동기화하므로 이후 실패 시 앞선 INSERT·UPDATE·DELETE를 모두 롤백할 수 있다. SQL 실패는 rollbackOnly를 남긴다. 호출자가 SQLException을 잡아도 가장 바깥 경계는 UnexpectedRollbackException으로 전체 롤백한다. commit 직전 flush에서 전파된 실패도 전체 롤백한다. afterCompletion은 성공·실패와 관계없이 세션과 스냅샷을 정리한다. DB 롤백이 Java 객체 값이나 생성 ID를 복구하지는 않는다.

## 값 복사 경계

스냅샷은 byte[]를 복사하고 내용으로 비교한다. java.sql.Date·Time·Timestamp와 java.util.Date는 복사하므로 setter 또는 배열 원소의 in-place 변경도 감지한다. Timestamp의 nanos를 보존한다. String·숫자 wrapper·UUID·java.time 같은 불변 JDBC 값은 값 동등성을 사용한다. 임의 mutable 객체·컬렉션·관계·Blob/Clob·사용자 정의 JDBC 값의 깊은 복사는 지원하지 않는다. 이 값들의 내부 변경 추적을 보장하지 않는다.

## 실제 JPA와 비교

JPA EntityManager의 persist/remove는 void이고, 관리 변경을 flush에서 동기화한다. 이 프로젝트는 즉시 INSERT·DELETE와 명시적 update(int)를 학습용 계약으로 유지한다. JPA의 전체 생명주기·쓰기 지연·쿼리 전 AUTO flush·merge/clear/detach/refresh·관계 매핑·cascade·낙관적 잠금·확장 컨텍스트는 구현하지 않는다. [Jakarta Persistence 3.2 EntityManager API](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/EntityManager.html)

## 검증

H2에서 SQL 실행 횟수와 같은 연결의 DB 값을 함께 검사한다. 변경 없음·값 복원·null 전환·여러 필드·반복 flush·명시적 update·persist 후 수정·삭제 후 수정·성공 flush 후 rollback·잡힌 SQL 실패·commit 실패·ID 변조·가변 JDBC 값·컨텍스트 종료를 확인한다.

```powershell
.\gradlew.bat test --tests 'com.jorm.entity.*'
.\gradlew.bat test
node --test doc/tests/learning-page.test.cjs
```
