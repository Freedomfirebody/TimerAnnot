package com.lewis.util.ppa.timer.annot;

import com.lewis.util.ppa.constant.Constant;

import java.lang.annotation.*;

/**
 * <pre>
 * 用于方法的用时的注释
 * value 为对应展示的自定义名称，默认为方法名
 * </pre>
 *
 * @author Lewis
 * @version 1.0
 * @since 2022-04-20 17:45
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
public @interface Timer {

    String name = Constant.TIMER_ANNOT_CLASSPATH + Constant.DOT + "Timer";

    String value() default "";
}
