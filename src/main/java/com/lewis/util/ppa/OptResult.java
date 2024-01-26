package com.lewis.util.ppa;

import com.sun.org.apache.bcel.internal.classfile.ClassFormatException;

import java.util.Objects;
import java.util.function.Function;

/**
 * 操作结果，实体类，用于对结果的封装，便于业务中的上下文传输
 *
 * @author Lewis
 * @version 1.0
 * @since 2022-05-06 15:26
 */
public class OptResult {

    private Object returnObj;

    private Class<?> formatClass;

    private Boolean formatSuccess = null;

    private Function<Object, Object> unexpectedFn = null;

    public OptResult(Object obj) {
        this.returnObj = obj;
    }

    /**
     * 进行数据格式验证
     *
     * @param formatClass 目标格式化类
     */
    public OptResult format(Class<?> formatClass) {
        formatSuccess = formatClass.isInstance(returnObj);
        this.formatClass = formatClass;
        return this;
    }

    /**
     * 对象操作方法，仅允许修改结果对象
     *
     * @param fn 操作方案
     */
    public OptResult opr(Function<Object, Object> fn) {
        returnObj = fn.apply(returnObj);
        return this;
    }

    /**
     * 显示的构建未达成期望结果的时候的处理方法
     *
     * @param function 处理方案
     */
    public OptResult unexpected(Function<Object, Object> function) {
        if (Objects.nonNull(formatSuccess)) {
            unexpectedFn = function;
        } else {
            throw new RuntimeException("format first");
        }
        return this;
    }

    /**
     * 根据预设的预期值与不符合预期处理方案，执行，获取最终的预期结果或异常
     * @return 返回预期结果
     */
    @SuppressWarnings("unchecked")
    public <R> R get() {
        if (Objects.isNull(formatSuccess)) {
            throw new RuntimeException("format first");
        } else if (formatSuccess) {
            return (R) returnObj;
        } else if (Objects.nonNull(unexpectedFn)) {
            return (R) unexpectedFn.apply(returnObj);
        } else {
            throw new ClassFormatException("expect class <" + formatClass.getName() + "> real class<" + returnObj.getClass().getName() + ">");
        }
    }

}
