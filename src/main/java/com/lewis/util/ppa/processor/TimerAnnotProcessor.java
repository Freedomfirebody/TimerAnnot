package com.lewis.util.ppa.processor;

import com.lewis.util.ppa.ProcessStruct;
import com.lewis.util.ppa.timer.AbstractTimerProcessor;
import com.lewis.util.ppa.timer.BlockTimerProcessor;
import com.lewis.util.ppa.timer.TimerProcessor;
import com.lewis.util.ppa.timer.annot.BlockTimerEnable;
import com.lewis.util.ppa.timer.annot.Timer;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.Set;

/**
 * A preprocessor relate with Timer
 *
 * @author Lewis
 * @version 1.0
 * @since 2022-02-24 09:04
 */

@SupportedAnnotationTypes({
        Timer.name,
        BlockTimerEnable.name
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class TimerAnnotProcessor extends AbstractProcessor {

    protected ProcessStruct processStruct = null;

    /**
     * 初始化部分的结构参数，便于后续调取使用
     *
     * @param processingEnv environment to access facilities the tool framework
     *                      provides to the processor
     */
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        Context processContext;
        JavacElements elementUtils;
        // Intellij idea 2020.3 之后的版本使用 JDK 代理传入 processingEnv 对象
        // 因此此处做定制的处理
        if ( Proxy.isProxyClass( processingEnv.getClass() ) ) {
            try {
                processContext = (Context) Proxy.getInvocationHandler(
                        processingEnv).invoke(processingEnv,
                        JavacProcessingEnvironment.class.getMethod("getContext"),
                        null);

                elementUtils = (JavacElements) Proxy.getInvocationHandler(
                        processingEnv).invoke(processingEnv,
                        ProcessingEnvironment.class.getMethod("getElementUtils"),
                        null);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }else {
            processContext = ((JavacProcessingEnvironment) processingEnv).getContext();
            elementUtils = (JavacElements) processingEnv.getElementUtils();
        }
        this.processStruct = new ProcessStruct(
                processContext,
                elementUtils
        );
    }

    /**
     * process
     * 注解处理器，根据传入的注解以及，获取的环境参数，可以获取到注解应用的成员对象，进行成员对象的修改变更等操作
     *
     * @param annotations the annotation types requested to be processed
     * @param roundEnv    environment for information about the current and prior round
     * @return ture
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        System.out.println("log for annotationProcessor");
        for (TypeElement t : annotations) {
            AbstractTimerProcessor<? extends Annotation> optProcessor = selectTimerProcessor(t.getQualifiedName().toString());
            // 注解处理器中添加需要用到的对象集合
            optProcessor.setProcessStruct(processStruct);
            for (Element member : roundEnv.getElementsAnnotatedWith(t)) {
                optProcessor.process(member);
            }
        }
        return true;
    }

    /**
     * 注解选择器，根据不同的注解，选择使用对应的注解处理器，进行成员对象的处理
     *
     * @param annotName 注解类名
     * @return 注解处理器
     */
    private AbstractTimerProcessor<? extends Annotation> selectTimerProcessor(String annotName) {
        switch (annotName) {
            case Timer.name:
                return new TimerProcessor();
            case BlockTimerEnable.name:
                return new BlockTimerProcessor();
            default:
                throw new NullPointerException("not find specify annotation");
        }
    }
}
