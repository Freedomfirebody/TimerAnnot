package com.lewis.util.ppa.timer.annot;

import com.lewis.util.ppa.constant.Constant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 是否启用 Timer 块的注解，若注解作用于类上则实际作用于所有的方法上，详细使用可查看 README.md
 *
 * @author Lewis
 * @version 1.0
 * @since 2022-04-20 17:45
 */

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
public @interface BlockTimerEnable {

    String name = Constant.TIMER_ANNOT_CLASSPATH + Constant.DOT + "BlockTimerEnable";

    boolean unEnable() default false;

}
