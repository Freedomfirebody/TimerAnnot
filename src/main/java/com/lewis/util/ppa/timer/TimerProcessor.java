package com.lewis.util.ppa.timer;

import com.lewis.util.ppa.timer.annot.Timer;
import com.sun.tools.javac.tree.JCTree;

import javax.lang.model.element.Element;
import java.util.Objects;

/**
 * @author Lewis
 * @version 1.0
 * @since 2022-04-22 16:03
 */
public class TimerProcessor extends AbstractTimerProcessor<Timer> {

    public Class<Timer> getAnnot() {
        return Timer.class;
    }

    /**
     * 将 @Timer 注解的方法，的 body 重新封装成 Try 模块
     * @param member 当前处理对象
     * @param jcTree 当前处理的 Java 对象构建的结构体
     */
    @Override
    public void realProcess(Element member, JCTree jcTree) {
        // 此处无需判断，该注解只允许注释在方法上
        JCTree.JCMethodDecl jcMethodDecl = (JCTree.JCMethodDecl) jcTree;
        Timer annot = obtainAnnot(member);
        // 判断使用注解值还是注解的方法名作为 Timer 的命名参数
        final String method = Objects.nonNull(annot.value()) ? annot.value().isEmpty() ? member.getSimpleName().toString() : obtainAnnot(member).value() : member.getSimpleName().toString();
        jcMethodDecl.body = constructTimerBlock(method, jcMethodDecl.body);
    }
}
