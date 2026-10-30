package com.jorm.mapper;

import java.util.List;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import com.jorm.jdbc.BeanPropertyRowMapper;
import com.jorm.jdbc.JdbcTemplate;

public class MapperInvocationHandler implements InvocationHandler {

	private final JdbcTemplate jdbcTemplate;
	
    public MapperInvocationHandler(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
    public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {
    	if (method.getDeclaringClass() == Object.class) {
    		return switch (method.getName()) {
				case "toString" -> "MapperProxy";
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == args[0];
				default -> throw new IllegalArgumentException(method.toString());
    		};
    	}
    	
        Select select = method.getAnnotation(Select.class);
        Insert insert = method.getAnnotation(Insert.class);
        Update update = method.getAnnotation(Update.class);
        Delete delete = method.getAnnotation(Delete.class);

        int annotationCount = (select != null ? 1 : 0)
                + (insert != null ? 1 : 0)
                + (update != null ? 1 : 0)
                + (delete != null ? 1 : 0);

        if (annotationCount == 0) {
            throw new UnsupportedOperationException(
                "어노테이션이 없는 메서드입니다: " + method.getName()
            );
        }

        if (annotationCount > 1) {
            throw new IllegalArgumentException(
                    "SQL 어노테이션은 하나만 지정해야 합니다: " + method.getName()
            );
        }

        Object[] parameters = args == null ? new Object[0] : args;
        
        if (select != null) {        
	        String sql = select.value();
	        System.out.println("실행할 SQL: " + sql);
	        
	        Class<?> returnType = method.getReturnType();
	
	        if (returnType == List.class) {
	        	Type genericType = method.getGenericReturnType();
	    		// 리스트타입의 리턴이 아닌 경우
	        	if (!(genericType instanceof ParameterizedType parameterizedType)) {
	        	        throw new IllegalArgumentException(
	        	                "List의 요소 타입을 지정해야 합니다: " + method.getName()
	        	        );
	    	    }
	        	
	        	// 리스트의 원소타입검사
	        	Type elementType = parameterizedType.getActualTypeArguments()[0];
	            if (!(elementType instanceof Class<?> elementClass)) {
	                throw new IllegalArgumentException(
	                        "List의 요소 타입은 구체적인 클래스여야 합니다: "
	                                + method.getName()
	                );
	            }
	            
	            // 내부원소클래스를 원소로 사용.
	            BeanPropertyRowMapper<?> rowMapper =
	                    new BeanPropertyRowMapper<>(elementClass);
	
	            return jdbcTemplate.query(sql, rowMapper, parameters);
	        }
	
	        BeanPropertyRowMapper<?> rowMapper = new BeanPropertyRowMapper<>(returnType);
	        return jdbcTemplate.queryForObject(sql, rowMapper, parameters);
        }

        // 어노테이션은 정확히 하나이므로 나머지는 INSERT, UPDATE, DELETE 중 하나다.
        String annotationName = insert != null ? "@Insert" : update != null ? "@Update" : "@Delete";
        if (method.getReturnType() != int.class) {
            throw new IllegalArgumentException(
                    annotationName + " 메서드의 반환 타입은 int여야 합니다: " + method.getName()
            );
        }

        String sql = insert != null ? insert.value() : update != null ? update.value() : delete.value();
        return jdbcTemplate.update(sql, parameters);
    }
}
