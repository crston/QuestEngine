package com.gmail.bobason01.questengine.gui.editor;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class DraftFieldAccessor {

    private static final Map<Class<?>, Map<String, Field>> CACHE = new ConcurrentHashMap<>();

    private DraftFieldAccessor() {
    }

    public static Object get(Object target, String fieldName) {
        if (target == null || fieldName == null) {
            return null;
        }
        Field field = getField(target.getClass(), fieldName);
        if (field == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Object target, String fieldName, Class<T> type) {
        Object value = get(target, fieldName);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            return null;
        }
        return (T) value;
    }

    public static boolean set(Object target, String fieldName, Object value) {
        if (target == null || fieldName == null) {
            return false;
        }
        Field field = getField(target.getClass(), fieldName);
        if (field == null) {
            return false;
        }
        try {
            if (value != null && !field.getType().isInstance(value)) {
                Object converted = tryConvert(value, field.getType());
                if (converted != null) {
                    field.set(target, converted);
                    return true;
                }
            } else {
                field.set(target, value);
                return true;
            }
        } catch (IllegalAccessException e) {
            return false;
        }
        return false;
    }

    private static Field getField(Class<?> type, String fieldName) {
        Map<String, Field> map = CACHE.get(type);
        if (map == null) {
            map = buildFieldMap(type);
            CACHE.put(type, map);
        }
        return map.get(fieldName);
    }

    private static Map<String, Field> buildFieldMap(Class<?> type) {
        Map<String, Field> map = new ConcurrentHashMap<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            Field[] fields = current.getDeclaredFields();
            for (Field f : fields) {
                String name = f.getName();
                if (map.containsKey(name)) {
                    continue;
                }
                f.setAccessible(true);
                map.put(name, f);
            }
            current = current.getSuperclass();
        }
        return map;
    }

    private static Object tryConvert(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        if (targetType == int.class || targetType == Integer.class) {
            if (value instanceof Number n) {
                return n.intValue();
            }
            if (value instanceof String s) {
                try {
                    return Integer.parseInt(s.trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (targetType == boolean.class || targetType == Boolean.class) {
            if (value instanceof Boolean b) {
                return b;
            }
            if (value instanceof String s) {
                return Boolean.parseBoolean(s.trim());
            }
        }

        if (targetType == String.class) {
            return value.toString();
        }

        return null;
    }
}
