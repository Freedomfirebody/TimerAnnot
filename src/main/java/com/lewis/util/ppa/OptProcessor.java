package com.lewis.util.ppa;

/**
 * 操作处理器接口
 *
 * @author Lewis
 * @version 1.0
 * @since 2022-04-22 15:44
 */
public interface OptProcessor<E, T> {

    T process(E member);

}
