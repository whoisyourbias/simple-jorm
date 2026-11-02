package com.jorm.mapper;

import java.util.List;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import com.jorm.jdbc.BeanPropertyRowMapper;
import com.jorm.jdbc.JdbcTemplate;
import com.jorm.transaction.Transactional;

public class MapperInvocationHandler implements InvocationHandler {

	private final JdbcTemplate jdbcTemplate;

    /** Mapper 호출의 SQL 실행을 담당할 JdbcTemplate을 보관한다. */
    public MapperInvocationHandler(JdbcTemplate jdbcTemplate) {
        // 전달받은 값을 인스턴스 필드에 저장한다.
		this.jdbcTemplate = jdbcTemplate;
	}

    /** Mapper 호출을 가로채 선언 검증 후 조회·변경 SQL 실행으로 연결한다. */
	@Override
    public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {
        // Object 메서드는 SQL 없이 별도로 처리한다.
    	if (method.getDeclaringClass() == Object.class) {
            // 호출된 Object 메서드 이름에 맞는 결과를 선택한다.
    		return switch (method.getName()) {
                // 프록시 식별 문자열을 반환한다.
				case "toString" -> "MapperProxy";
                // 프록시 참조 동일성을 기준으로 해시를 반환한다.
				case "hashCode" -> System.identityHashCode(proxy);
                // 같은 프록시 객체인지 참조로 비교한다.
				case "equals" -> proxy == args[0];
                // 지원하지 않는 메서드 또는 컬럼 번호를 예외로 알린다.
				default -> throw new IllegalArgumentException(method.toString());
    		};
    	}



        // 현재 메서드에서 해당 SQL 어노테이션을 읽는다.
        Select select = method.getAnnotation(Select.class);
        // 현재 메서드에서 해당 SQL 어노테이션을 읽는다.
        Insert insert = method.getAnnotation(Insert.class);
        // 현재 메서드에서 해당 SQL 어노테이션을 읽는다.
        Update update = method.getAnnotation(Update.class);
        // 현재 메서드에서 해당 SQL 어노테이션을 읽는다.
        Delete delete = method.getAnnotation(Delete.class);

        // SQL 어노테이션이 정확히 하나인지 검사할 개수를 합산한다.
        int annotationCount = (select != null ? 1 : 0)
                + (insert != null ? 1 : 0)
                + (update != null ? 1 : 0)
                + (delete != null ? 1 : 0);

        // SQL 선언이 없는 메서드를 거부한다.
        if (annotationCount == 0) {
            // 잘못된 선언·입력 또는 실행 상태를 예외로 알린다.
            throw new UnsupportedOperationException(
                "어노테이션이 없는 메서드입니다: " + method.getName()
            );
        }

        // 둘 이상의 SQL 작업을 지정한 모호한 선언을 거부한다.
        if (annotationCount > 1) {
            // 잘못된 선언·입력 또는 실행 상태를 예외로 알린다.
            throw new IllegalArgumentException(
                    "SQL 어노테이션은 하나만 지정해야 합니다: " + method.getName()
            );
        }

        // 인자 없는 호출의 null을 빈 배열로 바꾼다.
        Object[] parameters = args == null ? new Object[0] : args;

        // 조회 SQL 처리 경로로 분기한다.
        if (select != null) {
            // 현재 처리할 조회 또는 변경 SQL을 결정한다.
	        String sql = select.value();
            // 실행 환경·진행 상황 또는 비교 결과를 출력한다.
	        System.out.println("실행할 SQL: " + sql);

            // 메서드 반환 타입으로 단건·목록 또는 int 여부를 판별한다.
	        Class<?> returnType = method.getReturnType();

            // 반환 타입이 List이면 여러 행을 매핑한다.
	        if (returnType == List.class) {
                // List 요소 타입을 포함하는 제네릭 반환 타입을 읽는다.
	        	Type genericType = method.getGenericReturnType();
                // 리스트타입의 리턴이 아닌 경우
                // 요소 타입 없는 raw List 선언을 거부한다.
	        	if (!(genericType instanceof ParameterizedType parameterizedType)) {
                        // 잘못된 선언·입력 또는 실행 상태를 예외로 알린다.
	        	        throw new IllegalArgumentException(
	        	                "List의 요소 타입을 지정해야 합니다: " + method.getName()
	        	        );
	    	    }

                // 리스트의 원소타입검사
                // List의 제네릭 인자에서 요소 타입을 꺼낸다.
	        	Type elementType = parameterizedType.getActualTypeArguments()[0];
                // 타입 변수나 와일드카드 대신 구체적인 요소 클래스인지 검사한다.
	            if (!(elementType instanceof Class<?> elementClass)) {
                    // 잘못된 선언·입력 또는 실행 상태를 예외로 알린다.
	                throw new IllegalArgumentException(
	                        "List의 요소 타입은 구체적인 클래스여야 합니다: "
	                                + method.getName()
	                );
	            }

                // 내부원소클래스를 원소로 사용.
                // List의 요소 클래스로 자동 매퍼를 만들기 위한 변수를 선언한다.
	            BeanPropertyRowMapper<?> rowMapper =
	                    new BeanPropertyRowMapper<>(elementClass);

                // 모든 조회 행을 요소 객체로 변환해 리스트로 반환한다.
	            return jdbcTemplate.query(sql, rowMapper, parameters);
	        }

            // 대상 클래스의 필드에 값을 저장할 자동 매퍼를 만든다.
	        BeanPropertyRowMapper<?> rowMapper = new BeanPropertyRowMapper<>(returnType);
            // 첫 조회 행을 결과 객체로 변환해 반환한다.
	        return jdbcTemplate.queryForObject(sql, rowMapper, parameters);
        }

        // 어노테이션은 정확히 하나이므로 나머지는 INSERT, UPDATE, DELETE 중 하나다.
        // 실패 메시지에 표시할 변경 SQL 어노테이션 이름을 선택한다.
        String annotationName = insert != null ? "@Insert" : update != null ? "@Update" : "@Delete";
        // 변경 행 수를 반환하도록 변경 메서드는 int만 허용한다.
        if (method.getReturnType() != int.class) {
            // 잘못된 선언·입력 또는 실행 상태를 예외로 알린다.
            throw new IllegalArgumentException(
                    annotationName + " 메서드의 반환 타입은 int여야 합니다: " + method.getName()
            );
        }

        // 현재 처리할 조회 또는 변경 SQL을 결정한다.
        String sql = insert != null ? insert.value() : update != null ? update.value() : delete.value();
        // 변경 SQL을 실행하고 영향받은 행 수를 반환한다.
        return jdbcTemplate.update(sql, parameters);
    }
}
