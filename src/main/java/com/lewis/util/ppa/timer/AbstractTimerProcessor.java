package com.lewis.util.ppa.timer;

import com.lewis.util.ppa.ProcessStruct;
import com.lewis.util.ppa.OptProcessor;
import com.lewis.util.ppa.OptResult;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;

import javax.lang.model.element.Element;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.sun.tools.javac.tree.JCTree.Tag.*;

/**
 * 注解处理类的综合抽象类
 *
 * @author Lewis
 * @version 1.0
 * @since 2022-04-24 13:45
 */
public abstract class AbstractTimerProcessor<Annot extends Annotation> {

    protected ProcessStruct processStruct;

    private static final AtomicReference<String> COM_STR_REF = new AtomicReference<>();

    protected static final ThreadLocal<AtomicInteger> threadLocal = new ThreadLocal<>();

    public abstract Class<Annot> getAnnot();

    /**
     * 根据请求的成员获取该成员拥有的指定的注解
     *
     * @param member 成员变量
     * @return 注解
     */
    protected Annot obtainAnnot(Element member) {
        if (Objects.isNull(member)) return null;
        return member.getAnnotation(getAnnot());
    }

    static {
        threadLocal.set(new AtomicInteger(0));
    }

    public void setProcessStruct(ProcessStruct processStruct) {
        this.processStruct = processStruct;
    }

    /**
     * 通用处理器，操作通用的数据
     *
     * @param member 类或类成员
     */
    public final void process(Element member) {
        final JCTree jcTree = this.processStruct.getElementUtils().getTree(member);
        processStruct.getTreeMaker().pos = jcTree.pos;
        realProcess(member, jcTree);
    }

    /**
     * 实际的处理单元，由具体的实现单元实现具体的操作
     *
     * @param member 当前处理对象
     * @param jcTree 当前处理的 Java 对象构建的结构体
     */
    abstract void realProcess(Element member, JCTree jcTree);

    /**
     * 根据原代码块，构建 Timer 的代码块
     *
     * @param literalName Timer 对应的名曾
     * @param source      原代码块对象
     * @return 新构建的代码块
     */
    protected JCTree.JCBlock constructTimerBlock(String literalName, JCTree.JCBlock source) {
        final TreeMaker treeMaker = this.processStruct.getTreeMaker();
        return treeMaker.Block(0,
                List.of(
                        treeMaker.Try(
                                List.of(constructTimerVariable(literalName)),
                                source,
                                List.nil(),
                                treeMaker.Block(0, List.nil())
                        )
                ));

    }

    /**
     * 根据原代码块，构建 Timer 的代码块
     *
     * @param expression 表达式内对应的参数
     * @param source      原代码块对象
     * @return 新构建的代码块
     */
    protected JCTree.JCBlock constructTimerBlock(List<JCTree.JCExpression> expression, JCTree.JCBlock source) {
        final TreeMaker treeMaker = this.processStruct.getTreeMaker();
        JCTree.JCVariableDecl decl = constructTimerVariable(expression);
        return treeMaker.Block(0,
                List.of(
                        treeMaker.Try(
                                List.of(decl),
                                source,
                                List.nil(),
                                treeMaker.Block(0, List.nil())
                        )
                ));

    }

    /**
     * 构建 Timer 定义表达式
     *
     * @param literalName Timer 对应的名称
     * @return Timer 定义表达式
     */
    protected JCTree.JCVariableDecl constructTimerVariable(String literalName) {
        return constructTimerVariable(literalName, String.valueOf(threadLocal.get().getAndIncrement()));
    }

    /**
     * 构建 Timer 定义表达式
     *
     * @param expression 表达式内对应的参数
     * @return Timer 定义表达式
     */
    protected JCTree.JCVariableDecl constructTimerVariable(List<JCTree.JCExpression> expression) {
        return constructTimerVariable(expression, String.valueOf(threadLocal.get().getAndIncrement()));
    }

    /**
     * 构建 Timer 定义表达式
     *
     * @param literalName Timer 对应的名称
     * @param defName     类声明的名称
     * @return Timer 定义表达式
     */
    protected JCTree.JCVariableDecl constructTimerVariable(String literalName, String defName) {
        final JCTree.JCExpression variable = parseLiteral(literalName);
        final TreeMaker treeMaker = this.processStruct.getTreeMaker();
        final JavacElements elementUtils = this.processStruct.getElementUtils();
        return treeMaker.VarDef(treeMaker.Modifiers(Flags.FINAL),
                elementUtils.getName(TimeCalculate.DEFAULT_NAMING + defName),
                generateClassExpression(TimeCalculate.TimeDetail.class.getName(), processStruct),
                treeMaker.Apply(
                        List.nil(),
                        treeMaker.Select(
                                generateClassExpression(TimeCalculate.class.getName(), processStruct),
                                elementUtils.getName(TimeCalculate.NEW_INSTANCE)
                        ),
                        List.of(
                                variable
                        )
                )
        );
    }

    /**
     * 构建 Timer 定义表达式
     *
     * @param expression 表达式内对应的参数
     * @param defName    类声明的名称
     * @return Timer 定义表达式
     */
    protected JCTree.JCVariableDecl constructTimerVariable(List<JCTree.JCExpression> expression, String defName) {
        final TreeMaker treeMaker = this.processStruct.getTreeMaker();
        final JavacElements elementUtils = this.processStruct.getElementUtils();
        JCTree.JCExpression[] expressions = expression.toArray(new JCTree.JCExpression[0]);
        // 定时器描述为最后一个参数，因此只需要构造最后一个参数即可
        JCTree.JCExpression jcExpression = expressions[expressions.length - 1];
        AtomicReference<String> ref = new AtomicReference<>();
        String value = getVarStrProcessor(jcExpression)
                .process(ref)
                .format(String.class)
                .unexpected(o -> null)
                .get();
        expressions[expressions.length - 1] = parseLiteral(value);
        return treeMaker.VarDef(treeMaker.Modifiers(Flags.FINAL),
                elementUtils.getName(TimeCalculate.DEFAULT_NAMING + defName),
                generateClassExpression(TimeCalculate.TimeDetail.class.getName(), processStruct),
                treeMaker.Apply(
                        List.nil(),
                        treeMaker.Select(
                                generateClassExpression(TimeCalculate.class.getName(), processStruct),
                                elementUtils.getName(TimeCalculate.NEW_INSTANCE)
                        ),
                        List.from(expressions)
                ));
    }

    /**
     * 此方法与 {@link BlockTimerProcessor#getProcessor(JCTree)} 方法实现思路完全一致，区别在于此方法目标为获取目标参数变量内的对象，将对象进行转化为可解析的 #{XXX} 及 ${XXX}
     *
     * @param jcStatement 目标
     * @return statement 对应的操作处理器
     */
    private OptProcessor<AtomicReference<String>, OptResult> getVarStrProcessor(JCTree jcStatement) {
        if (Objects.isNull(jcStatement)) {
            return (input) -> new OptResult(Boolean.FALSE);
        }
        switch (jcStatement.getTag()) {
            case LITERAL:
                return (input) -> {
                    JCTree.JCLiteral literal = ((JCTree.JCLiteral) jcStatement);
                    return new OptResult(literal.getValue());
                };
            case PLUS:
                return (input) -> {
                    JCTree.JCBinary plus = ((JCTree.JCBinary) jcStatement);
                    JCTree.JCExpression leftExpression = plus.getLeftOperand();
                    JCTree.JCExpression rightExpression = plus.getRightOperand();
                    String result = "";
                    result += getVarStrProcessor(leftExpression)
                            .process(COM_STR_REF)
                            .format(String.class)
                            .unexpected(o -> "")
                            .get();
                    result += getVarStrProcessor(rightExpression)
                            .process(COM_STR_REF)
                            .format(String.class)
                            .unexpected(o -> "")
                            .get();
                    return new OptResult(result);
                };
            case IDENT:
                return (input) -> new OptResult("${" + jcStatement + "}");
            // 此处特意不写 break 为的就是当不满足条件的情况下直接使用 default 抛出异常
            case APPLY:
                if (((JCTree.JCMethodInvocation) jcStatement).getArguments().isEmpty()) {
                    return (input) -> new OptResult("#{" + jcStatement + "}");
                }
            default:
                throw new RuntimeException("not support parameter, this plugin only support String, function(no parameter), variable as parameter, and plus operation");
        }
    }

    /**
     * 由于构造了两种占位符定义，此处进行解析 #{XXX} 解析为函数调用， ${XXX} 解析为变量调用
     *
     * @param literalName 被解析参数对象
     * @return 返回解析后的实际参数结构体
     */
    private JCTree.JCExpression parseLiteral(String literalName) {
        java.util.List<ParseObj> parseObjList = new ArrayList<>();
        final TreeMaker treeMaker = this.processStruct.getTreeMaker();
        final JavacElements elementUtils = this.processStruct.getElementUtils();
        for (parseExpression expression : parseExpression.values()) {
            int index = 0;
            ParseObj parseObj;
            while (Objects.nonNull(parseObj = expression.findNext(literalName, index))) {
                index = parseObj.getEnd();
                parseObjList.add(parseObj);
            }
        }
        if (parseObjList.isEmpty()) {
            return treeMaker.Literal(literalName);
        }
        parseObjList.sort(Comparator.comparingInt(ParseObj::getBegin));
        // 验证是否有异常的输入结构体
        // 解析目标例如： #{${}}
        parseObjList.sort((o1, o2) -> {
            if (o1.getEnd() < o2.getBegin()) {
                throw new RuntimeException("parse error: " + literalName);
            }
            return 0;
        });
        JCTree.JCExpression rightBinary = null;
        JCTree.JCExpression leftBinary = null;
        int lastIndex = 0;
        for (ParseObj parseObj : parseObjList) {
            if (Objects.isNull(leftBinary)) {
                if (parseObj.getBegin() > lastIndex) {
                    leftBinary = treeMaker.Literal(literalName.substring(lastIndex, parseObj.getBegin()));
                } else {
                    leftBinary = treeMaker.Literal(literalName.substring(lastIndex, parseObj.getBegin()));
                    continue;
                }
            } else {
                if (parseObj.getBegin() > lastIndex) {
                    rightBinary = treeMaker.Literal(literalName.substring(lastIndex, parseObj.getBegin()));
                    rightBinary = treeMaker.Binary(PLUS, leftBinary, rightBinary);
                }
            }
            if (APPLY.equals(parseObj.getTag())) {
                rightBinary = treeMaker.Apply(
                        List.nil(),
                        treeMaker.Ident(elementUtils.getName(literalName.substring(parseObj.getBegin() + 2, parseObj.getEnd() - 2))),
                        List.nil());
            } else if (IDENT.equals(parseObj.getTag())) {
                rightBinary = treeMaker.Ident(elementUtils.getName(literalName.substring(parseObj.getBegin() + 2, parseObj.getEnd())));
            }
            leftBinary = treeMaker.Binary(PLUS, leftBinary, rightBinary);
            lastIndex = parseObj.getEnd() + 1;
        }
        return leftBinary;
    }


    /**
     * <pre>
     * 通过玩完整的类名构建 JCTree 表达式
     * 以 {@link TimeCalculate.TimeDetail} 为例
     * 正常为 import 后, 直接使用 TimeCalculate.TimeDetail 作为声明类型
     * 但此时 JCTree 中必须使用完整的类名称, 即
     * cn.com.datu.ppa.timer.TimeCalculate.TimeDetail
     * 对等结构体如下
     * treeMaker.Select(
     *      treeMaker.Select(
     *          treeMaker.Select(
     *              treeMaker.Select(
     *                  treeMaker.Select(
     *                      treeMaker.Ident(elementUtils.getName("cn")),
     *                      elementUtils.getName("com")
     *                  ),
     *                  elementUtils.getName("datu")
     *              ),
     *              elementUtils.getName("processor")
     *          ),
     *          elementUtils.getName("TimeCalculate")
     *      ),
     *      elementUtils.getName("TimeDetail")
     *  )</pre>
     *
     * @param clazzName     要自动构建的类名，".class" 请根据需要保留或去除
     * @param processStruct 处理器相关结构提
     * @return 表达式的构建结果
     */
    protected JCTree.JCExpression generateClassExpression(String clazzName, final ProcessStruct processStruct) {
        final JavacElements elementUtils = processStruct.getElementUtils();
        final TreeMaker treeMaker = processStruct.getTreeMaker();
        if (Objects.isNull(clazzName) || clazzName.isEmpty()) throw new NullPointerException("clazzName was empty");
        // 内部类 getName 时会使用 "$" 表达，此处替换为标准格式 "."
        String[] packetNodeList = clazzName.replaceAll("\\$", ".").split("\\.");
        JCTree.JCExpression tempExpression = treeMaker.Ident(elementUtils.getName(packetNodeList[0]));
        for (int i = 1, size = packetNodeList.length; i < size; i++) {
            tempExpression = treeMaker.Select(tempExpression, elementUtils.getName(packetNodeList[i]));
        }
        return tempExpression;
    }

    enum parseExpression {
        FUNCTION("#{", "}", APPLY),
        VARIABLE("${", "}", IDENT);
        private final String beginExp;
        private final String endExp;

        private final JCTree.Tag tag;

        parseExpression(String beginExp, String endExp, JCTree.Tag tag) {
            this.beginExp = beginExp;
            this.endExp = endExp;
            this.tag = tag;
        }

        public String getBeginExp() {
            return beginExp;
        }

        public String getEndExp() {
            return endExp;
        }

        public JCTree.Tag getTag() {
            return tag;
        }

        ParseObj findNext(String str, int index) {
            int begin = str.indexOf(this.getBeginExp(), index);
            if (begin == -1) {
                return null;
            }
            int end = str.indexOf(this.getEndExp(), begin);
            return new ParseObj(begin, end, getTag());
        }
    }

    static class ParseObj {
        private final int begin;

        private final int end;

        private final JCTree.Tag tag;

        public ParseObj(int begin, int end, JCTree.Tag tag) {
            this.begin = begin;
            this.end = end;
            this.tag = tag;
        }

        public int getBegin() {
            return begin;
        }

        public int getEnd() {
            return end;
        }

        public JCTree.Tag getTag() {
            return tag;
        }
    }

}
