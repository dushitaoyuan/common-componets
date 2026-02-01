package com.taoyuanx.common.audit.log.context;

import com.alibaba.ttl.TransmittableThreadLocal;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 支持嵌套调用的 ThreadLocal 工具类
 * 允许在同一上下文中多次设置值，并能正确恢复到上一层的值
 */
public class NestedThreadLocal<T> {

    private final TransmittableThreadLocal<Deque<T>> threadLocal = new TransmittableThreadLocal<Deque<T>>() {
        @Override
        protected Deque<T> initialValue() {
            return new ArrayDeque<>();
        }
    };

    /**
     * 获取当前层级的值
     *
     * @return 当前值，如果栈为空则返回 null
     */
    public T get() {
        Deque<T> stack = threadLocal.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    /**
     * 设置当前层级的值
     *
     * @param value 要设置的值，允许为 null
     */
    public void set(T value) {
        Deque<T> stack = threadLocal.get();
        stack.push(value);
    }

    /**
     * 移除当前层级的值，恢复到上一层级的值
     *
     * @return 被移除的值
     */
    public T remove() {
        Deque<T> stack = threadLocal.get();
        if (stack.isEmpty()) {
            clear();
            return null;
        }
        T removedValue = stack.pop();
        if (stack.isEmpty()) {
            clear();
        }
        return removedValue;
    }

    /**
     * 清理当前线程的所有值
     */
    public void clear() {
        threadLocal.remove();
    }

}
