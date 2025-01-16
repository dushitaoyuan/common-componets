package com.taoyuanx.common.search.util;

import com.taoyuanx.common.search.dto.page.PageRequest;
import com.taoyuanx.common.search.dto.query.Condition;
import com.taoyuanx.common.search.dto.query.SearchCondition;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;


/**
 * @Author dushitaoyuan
 * @Description 条件转换工具(只支持简单条件转换)
 */
@Slf4j
public class SearchConditionBeanUtil {

    public static <T> T convertSearchBean(Class<T> clazz, SearchCondition searchCondition) {
        try {
            T newInstance = clazz.newInstance();

            if (searchCondition == null || CollectionUtils.isEmpty(searchCondition.getConditions())) {
                return newInstance;
            }
            List<Condition> conditions = searchCondition.getConditions();
            conditions.forEach(filedCondition -> {
                if (StringUtils.isEmpty(filedCondition.getValue())) {
                    return;
                }
                Field field = ReflectionUtils.findField(clazz, filedCondition.getName());
                if (field == null) {
                    log.warn("convertSearchBean field match error,fieldName:{}", filedCondition.getName());
                    return;
                }
                field.setAccessible(true);
                ReflectionUtils.setField(field, newInstance, toFieldType(field, field.getType(), filedCondition.getValue()));
            });

            // 如果条件对象存在字段[sorts]自动填充排序条件
            Field field = ReflectionUtils.findField(clazz, "sorts");
            if (field == null || CollectionUtils.isEmpty(searchCondition.getSorts())) {
                return newInstance;
            }
            field.setAccessible(true);
            //排序
            Collections.sort(searchCondition.getSorts());
            ReflectionUtils.setField(field, newInstance, searchCondition.getSorts());
            return newInstance;
        } catch (Exception e) {
            log.error("convertSearchBean error,searchCondition:{}", searchCondition, e);
            throw new RuntimeException(e);
        }
    }

    public static <T> PageRequest<T> convertToPageQuery(Class<T> clazz, PageRequest<SearchCondition> pageRequest) {
        T newQuery = convertSearchBean(clazz, pageRequest.getQuery());
        return new PageRequest<>(pageRequest.getPageNum(), pageRequest.getPageSize(), newQuery);
    }

    @SuppressWarnings("all")
    private static <T> T toFieldType(Field field, Class<?> type, String fieldValue) {
        /**
         * 自动转换支持的类型
         */
        if (type.isAssignableFrom(String.class) || type.isInstance(fieldValue)) {
            return (T) fieldValue;
        }
        if (type.isAssignableFrom(Integer.class)) {
            return (T) Integer.valueOf(fieldValue);
        }
        if (type.isAssignableFrom(Long.class)) {
            return (T) Long.valueOf(fieldValue);
        }
        if (type.isAssignableFrom(Byte.class)) {
            return (T) Byte.valueOf(fieldValue);
        }
        if (type.isAssignableFrom(Boolean.class)) {
            return (T) Boolean.valueOf(fieldValue);
        }
        if (type.isAssignableFrom(Date.class)) {
            return (T) new Date(Long.parseLong(fieldValue));
        }
        if (Collection.class.isAssignableFrom(type)) {
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) genericType;
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                if (actualTypeArguments.length > 0) {
                    Class<?> collectionItemType = (Class<?>) actualTypeArguments[0];
                    String[] items = fieldValue.split(",");
                    return (T) createCollectionInstance(field.getType(), Arrays.asList(items), collectionItemType);
                }
            }

        }
        throw new IllegalArgumentException("Unsupported field type: " + type);
    }


    private static Collection<?> createCollectionInstance(Class<?> collectionType, Collection<String> items, Class<?> itemType) {
        Collection<Object> collection;
        if (List.class.isAssignableFrom(collectionType)) {
            collection = new ArrayList<>();
        } else if (Set.class.isAssignableFrom(collectionType)) {
            collection = new HashSet<>();
        } else {
            throw new IllegalArgumentException("Unsupported collection type: " + collectionType);
        }
        for (String item : items) {
            collection.add(toFieldType(null, itemType, item));
        }
        return collection;
    }

}
