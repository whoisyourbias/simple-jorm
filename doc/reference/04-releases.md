# 릴리즈 내역과 버전 정책

## v0.1.0-alpha.1 — 2026-11-06

학습·실험용 첫 프리릴리즈다. 핵심 기능은 구현했지만 지원 범위가 제한되고 API·동작이 바뀔 수 있으므로 알파로 배포한다. 프로덕션 사용과 하위 호환성을 보장하지 않는다.

### 포함 기능

- JDBC SQL 실행·자원 정리와 setter 우선 자동 행 매핑.
- 어노테이션·동적 프록시 기반 Mapper, 이름 바인딩과 default 메서드.
- Connection 공유, REQUIRED·rollbackOnly, 선언적 롤백 규칙과 커밋 전 동기화.
- 엔티티 CRUD, 상속 필드 매핑, IDENTITY·UUID ID 생성.
- 트랜잭션별 객체 동일성, 스냅샷·변경 감지와 명시적·커밋 직전 flush.
- 실제 Java 데이터 접근 기술의 실행 흐름을 설명하는 독립 HTML 학습 페이지와 단계별 문서.

### 제약

JPA 표준 구현체가 아니다. 관계·복합 ID·낙관적 잠금, INSERT·DELETE 쓰기 지연, 조회 전 자동 flush, XML·동적 SQL은 지원하지 않는다. 전체 계약과 제약은 [구현 상세](02-implementation-status.md)에 정리했다.

### 다운로드와 사용

[GitHub 프리릴리즈](https://github.com/whoisyourbias/simple-jorm/releases/tag/v0.1.0-alpha.1)에서 다음 파일을 제공한다.

- `simple-jorm-0.1.0-alpha.1.jar`: Java 21 라이브러리. 실행형 JAR이 아니며 JDBC 드라이버·DataSource·DB 스키마는 사용자가 준비한다. Maven Central에는 배포하지 않는다.
- `simple-jorm-learning-0.1.0-alpha.1.html`: 내려받아 브라우저에서 열 수 있는 독립 학습 페이지.
- `SHA256SUMS.txt`: 첨부 파일의 SHA-256 체크섬.
- GitHub가 제공하는 소스 아카이브: 코드·테스트·전체 문서.

Gradle 프로젝트에서 내려받은 JAR을 `libs/`에 두었다면 다음과 같이 참조한다.

```kotlin
dependencies {
    implementation(files("libs/simple-jorm-0.1.0-alpha.1.jar"))
}
```

소스에서 검증·빌드하려면 Java 21과 Node.js 22 이상을 준비하고 태그를 체크아웃한 뒤 실행한다. 브라우저 검증에는 Chrome도 필요하다.

```powershell
.\gradlew.bat clean build --console=plain
node --test doc/tests/learning-page.test.cjs
node doc/tests/browser-smoke.cjs
```

MySQL 성능 실험은 이 릴리즈의 일반 검증과 별도로 실행한다.

### 릴리즈 검증 결과

2026-11-06, Java 21 환경에서 위 명령으로 검증했다. 전체 빌드 성공, Java 테스트 265개 통과(실패·오류·스킵 0), 페이지 테스트 10개 통과, Chrome 브라우저 검증 15개 통과. MySQL 성능 실험은 재실행하지 않았다.

## 버전 정책

- Gradle 버전은 `0.1.0-alpha.1`, Git 태그는 `v0.1.0-alpha.1` 형식으로 일치시킨다.
- 실험 단계에서는 `alpha.2`, `alpha.3`처럼 프리릴리즈 번호를 올린다.
- 예정된 기능 범위와 공개 API가 정리되고 사용자 검증을 받는 단계가 되면 `beta.1`로 전환한다.
- 안정 버전은 프리릴리즈 접미사를 제거한다. `0.x` 동안에는 호환성이 바뀔 수 있으며 변경 사항을 릴리즈에 명시한다.
- 이미 게시한 태그는 다른 커밋으로 이동하지 않는다. 수정은 새 버전으로 배포한다.
