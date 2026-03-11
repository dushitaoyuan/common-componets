package com.taoyuanx.common.audit.log.context;

import com.alibaba.ttl.TransmittableThreadLocal;

import java.util.ArrayDeque;
import java.util.Deque;


public class LogThreadLocal<T> {

    private boolean allowNest;
    /**
     * 支持嵌套调用的 ThreadLocal 工具类
     * 允许在同一上下文中多次设置值，并能正确恢复到上一层的值
     */
    private final TransmittableThreadLocal<Deque<T>> nestThreadLocal = new TransmittableThreadLocal<Deque<T>>() {
        @Override
        protected Deque<T> initialValue() {
            return new ArrayDeque<>();
        }
    };
    private final TransmittableThreadLocal<T> threadLocal = new TransmittableThreadLocal<T>();


    public LogThreadLocal(boolean allowNest) {
        this.allowNest = allowNest;
    }

    /**
     * 获取当前层级的值
     *
     * @return 当前值，如果栈为空则返回 null
     */
    public T get() {
        if (allowNest) {
            Deque<T> stack = nestThreadLocal.get();
            return stack.isEmpty() ? null : stack.peek();
        } else {
            return threadLocal.get();
        }

    }

    /**
     * 设置当前层级的值
     *
     * @param value 要设置的值，允许为 null
     */
    public void set(T value) {
        if (allowNest) {
            Deque<T> stack = nestThreadLocal.get();
            stack.push(value);
        } else {
            threadLocal.set(value);
        }
    }

    /**
     * 移除当前层级的值，恢复到上一层级的值
     *
     * @return 被移除的值
     */
    public void remove() {
        if (allowNest) {
            Deque<T> stack = nestThreadLocal.get();
            if (stack.isEmpty()) {
                nestThreadLocal.remove();
                return;
            }
            stack.pop();
            if (stack.isEmpty()) {
                nestThreadLocal.remove();
                return;
            }
        } else {
            threadLocal.get();
        }
    }

}