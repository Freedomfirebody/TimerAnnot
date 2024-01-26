package com.lewis.util.ppa.timer;

import com.lewis.util.ppa.OptProcessor;
import com.lewis.util.ppa.OptResult;
import com.lewis.util.ppa.timer.annot.BlockTimerEnable;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;

import javax.lang.model.element.Element;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 指定块 Timer 注解处理器
 *
 * @author Lewis
 * @version 1.0
 * @since 2022-04-22 16:12
 */
public class BlockTimerProcessor extends AbstractTimerProcessor<BlockTimerEnable> {

    @Override
    public Class<BlockTimerEnable> getAnnot() {
        return BlockTimerEnable.class;
    }

    // 设置一个共享的空的上传引用
    private static final AtomicReference<List<JCTree.JCExpression>> COM_DEF_REF = new AtomicReference<>();

    /**
     * 判断是否为 class 还是 method，若为 method 则直接执行方法处理器 {@link BlockTimerProcessor#methodProcess(JCTree.JCMethodDecl)}，若为 class，则先取子集，找到其中的 method 进行执行方法的处理器  {@link BlockTimerProcessor#methodProcess(JCTree.JCMethodDecl)}
     *
     * @param member 当前处理对象
     * @param jcTree 当前处理的 Java 对象构建的结构体
     */
    @Override
    public void realProcess(Element member, JCTree jcTree) {
        BlockTimerEnable annot = obtainAnnot(member);
        // 若注解设置未开启则直接返回
        if (Objects.nonNull(annot) && annot.unEnable()) return;
        if (JCTree.Tag.CLASSDEF.equals(jcTree.getTag())) {
            JCTree.JCClassDecl jcClassDecl = (JCTree.JCClassDecl) jcTree;
            Scope scope = ((Symbol.ClassSymbol) member).members();

            boolean next = false;
            for (JCTree classMember : jcClassDecl.getMembers()) {
                if (classMember instanceof JCTree.JCMethodDecl) {
                    // jdk 18 版本代码
//                     Iterable<Symbol> iterable = scope.getSymbolsByName(((JCTree.JCMethodDecl) classMember).getName());
                    // jdk 1.8 版本代码
                    Iterable<Symbol> iterable = scope.getElementsByName(((JCTree.JCMethodDecl) classMember).getName());
                    if (Objects.nonNull(iterable)) {
                        for (Element subMember : iterable) {
                            annot = obtainAnnot(subMember);
                            if (Objects.nonNull(annot) && annot.unEnable()) {
                                next = true;
                                break;
                            }
                        }
                        if (next) {
                            next = false;
                            continue;
                        }
                    }
                    methodProcess((JCTree.JCMethodDecl) classMember);
                }
            }
        } else if (JCTree.Tag.METHODDEF.equals(jcTree.getTag())) {
            methodProcess((JCTree.JCMethodDecl) jcTree);
        }
    }

    /**
     * 先判断该结构体中是否存在关键字 {@link TimeCalculate#TIMER_BLOCK_STATE} 是的话执行后续的处理步骤
     *
     * @param jcMethodDecl 可能需要处理的方法结构体
     */
    private void methodProcess(JCTree.JCMethodDecl jcMethodDecl) {
        String memberStruct = jcMethodDecl.toString();
        // 判断该方法内是否有存在 block 方法调用
        // 存在 Block 该结构体需要进行变更调整
        if (memberStruct.contains(TimeCalculate.TIMER_BLOCK_STATE)) {
            AtomicReference<List<JCTree.JCExpression>> atomicReference = new AtomicReference<>(null);
            if (Boolean.TRUE.equals(blockProcessor(jcMethodDecl.getBody(), atomicReference).format(Boolean.class).get())) {
                // 此处 atomicReference 的值理论上不可能为空，为空基本上是代码异常
                jcMethodDecl.body = constructTimerBlock(atomicReference.get(), jcMethodDecl.getBody());
            }
        }
    }

    /**
     * 对块进行处理，相对与后面的处理器较为特殊，直接业务表达，循环块内的所有 statement 对 statement 做递归处理，并判断 statement
     * 直接的子集中是否存在 {@link TimeCalculate#block(String)} 的方法调用，则返回 Boolean 至上层，告知上层该块中含有目标 statement {@link TimeCalculate#block(String)}
     *
     * @param processingBlock 处理的目标块
     * @param commonValue     下级给到上级的引用体，以获取上级的数据
     * @return 操作结果，该结果表达为 Boolean，为是否包含了目标代码块
     */
    private OptResult blockProcessor(JCTree.JCBlock processingBlock, AtomicReference<List<JCTree.JCExpression>> commonValue) {
        List<JCTree.JCStatement> statementList = processingBlock.getStatements();
        List<JCTree.JCStatement> newStatement = List.nil();
        boolean hasBlock = false;
        for (JCTree.JCStatement jcStatement : statementList) {
            Boolean result = getProcessor(jcStatement)
                    .process(commonValue)
                    .format(Boolean.class)
                    .get();
            if (Boolean.TRUE.equals(result)) hasBlock = true;
            else if (Boolean.FALSE.equals(result)) {
                newStatement = newStatement.append(jcStatement);
            }
        }
        if (Boolean.TRUE.equals(hasBlock)) processingBlock.stats = newStatement;
        return new OptResult(hasBlock);
    }

    /**
     * 通过输入的方法调用 statement 判断是否为 {@link TimeCalculate#block(String)} 方法调用，并且使用处理器处理或获取入参，若同时满足为 {@link TimeCalculate#block(String)}
     * 和参数仅为 LITERAL 则认定该代码块为目标代码块 {@link TimeCalculate#block(String)}，并更新下级给到上级的共享变量 input。
     * 此处可能遇到 lambda 做为参数导入，所以此处对除 LITERAL 外的 statement 也会通过 {@link BlockTimerProcessor#getProcessor}
     * 方法进行处理，以保证作为参数的 lambda 中的 Timer 块也会进行处理
     *
     * @param jcMethodInvocation 执行方法对象
     * @return 返回结果
     */
    private OptProcessor<AtomicReference<List<JCTree.JCExpression>>, OptResult> jcMethodInvocationProcessor(JCTree.JCMethodInvocation jcMethodInvocation) {
        return (input) -> {
            List<JCTree.JCExpression> value = null;
            boolean isTimerBlock = Objects.nonNull(jcMethodInvocation) && jcMethodInvocation.getMethodSelect().toString().startsWith(TimeCalculate.TIMER_BLOCK_STATE);
            if (!isTimerBlock) {
                // 此处无需关心结果，所以无需判断返回值
                getProcessor(jcMethodInvocation.getMethodSelect()).process(COM_DEF_REF);
                for (JCTree.JCExpression argsJcExpression : jcMethodInvocation.getArguments()) {
                    getProcessor(argsJcExpression).process(COM_DEF_REF);
                }
            } else {
                // 根据参数进行获取数据，此处代码为确定的结果，期望值一定为字符串
                try {
                    value = jcMethodInvocation.getArguments();
                }catch (RuntimeException e) {
                    throw new RuntimeException("Compile " + jcMethodInvocation + " failed, reason: " + e.getMessage());
                }
            }
            // 为 Timer 块标志结构、当前共享之为空，且 Timer 块标志结构调用参数仅为 1 个，则将共享代码块进行赋值
            if (isTimerBlock && Objects.isNull(input.get()))
                input.set(value);
            if (isTimerBlock)
                return new OptResult(Boolean.TRUE);
            else
                return new OptResult(Boolean.FALSE);
        };
    }

    /**
     * 判断是否为 block（IF 结构使用简化写法则会是 JCExpressionStatement）, 是则继续进行结构数据分析，否则直接返回（只有单条语句，是不可能使用 Timer 的 Block 的），无需后续操作
     *
     * @param jcStatement 输入结构体，只允许输入 JCIf
     * @return if 结构一定返回 false
     */
    private OptProcessor<AtomicReference<List<JCTree.JCExpression>>, OptResult> ifProcessor(JCTree.JCIf jcStatement) {
        final TreeMaker treeMaker = processStruct.getTreeMaker();
        return (input) -> {
            AtomicReference<List<JCTree.JCExpression>> newRef = new AtomicReference<>(null);
            JCTree.JCStatement thenPart = jcStatement.getThenStatement();
            if (thenPart instanceof JCTree.JCBlock && Boolean.TRUE.equals(blockProcessor((JCTree.JCBlock) thenPart, newRef).format(Boolean.class).get())) {
                jcStatement.thenpart = constructTimerBlock(newRef.get(), treeMaker.Block(0, List.of(thenPart)));
            }

            newRef.set(null);
            JCTree.JCStatement elsePart = jcStatement.getElseStatement();
            if (elsePart instanceof JCTree.JCBlock && Boolean.TRUE.equals(blockProcessor((JCTree.JCBlock) elsePart, newRef).format(Boolean.class).get())) {
                jcStatement.elsepart = constructTimerBlock(newRef.get(), treeMaker.Block(0, List.of(elsePart)));
            }
            return new OptResult(Boolean.FALSE);
        };
    }

    /**
     * 通过 statement 获取其应该使用的处理器，构建其对应的 lambda 处理器返回给调用方，调用方进行调用逻辑使用
     * 其中处理器内若有需要再执行的 statement 则作递归调用，并做处理
     * 不同的处理模块最终结果均为当直接下级捕捉到目标 statement {@link TimeCalculate#block(String)} 时均进行结构体的调整
     * <pre>
     *     当 try 模块直接下级捕捉到时，在本身的资源链中添加额外的 Timer 资源
     *     当 block 模块直接下级捕捉到时，用 Try 块替换原有的 block 块，将原有的 block 块移至 Try 的块中，且添加 Timer 资源到 Try 的资源链中
     *     IF、LAMBDA等模块类似 block 进行处理
     * </pre>
     *
     * @param jcStatement 目标 statement
     * @return statement 对应的操作处理器
     */
    private OptProcessor<AtomicReference<List<JCTree.JCExpression>>, OptResult> getProcessor(JCTree jcStatement) {
        if (Objects.isNull(jcStatement)) {
            return (input) -> new OptResult(Boolean.FALSE);
        }
        final TreeMaker treeMaker = processStruct.getTreeMaker();
        switch (jcStatement.getTag()) {
            case TRY:
                return (input) -> {
                    AtomicReference<List<JCTree.JCExpression>> newRef = new AtomicReference<>(null);
                    if (Boolean.TRUE.equals(blockProcessor(((JCTree.JCTry) jcStatement).getBlock(), newRef).format(Boolean.class).get())) {
                        // 若下级 block 存在 Timer 的 block 标志位则在 try 的资源列表中添加计时器对象
                        ((JCTree.JCTry) jcStatement).resources = ((JCTree.JCTry) jcStatement).getResources().append(constructTimerVariable(newRef.get()));
                    }
                    // 此处，由于该对象已使用，因此需要将该对象置为 null, 以保证下次使用
                    return new OptResult(Boolean.FALSE);
                };
            case BLOCK:
                return (input) -> {
                    final JCTree.JCBlock jcBlock = (JCTree.JCBlock) jcStatement;
                    AtomicReference<List<JCTree.JCExpression>> newRef = new AtomicReference<>(null);
                    if (Boolean.TRUE.equals(blockProcessor(jcBlock, newRef).format(Boolean.class).get())) {
                        jcBlock.stats = constructTimerBlock(newRef.get(), treeMaker.Block(0, jcBlock.getStatements())).getStatements();
                    }
                    return new OptResult(Boolean.FALSE);
                };
            case EXEC:
                return (input) -> {
                    final JCTree.JCExpression jcExpression = ((JCTree.JCExpressionStatement) jcStatement).getExpression();
                    // 此处作为中间处理，不做额外封装，直接返回上级结果
                    return getProcessor(jcExpression).process(input);
                };
            case IF:
                return ifProcessor((JCTree.JCIf) jcStatement);
            case LAMBDA:
                return (input) -> {
                    final JCTree jcTree = ((JCTree.JCLambda) jcStatement).getBody();
                    AtomicReference<List<JCTree.JCExpression>> newRef = new AtomicReference<>(null);
                    if (jcTree instanceof JCTree.JCBlock && Boolean.TRUE.equals(blockProcessor(((JCTree.JCBlock) jcTree), newRef).format(Boolean.class).get()))
                        ((JCTree.JCLambda) jcStatement).body = constructTimerBlock(newRef.get(), treeMaker.Block(0, ((JCTree.JCBlock) jcTree).getStatements()));
                    return new OptResult(Boolean.FALSE);
                };
            case SELECT:
                return (input) -> {
                    final JCTree.JCFieldAccess jcFieldAccess = (JCTree.JCFieldAccess) jcStatement;
                    // 此处无需关心结果，所以无需判断返回值
                    getProcessor(jcFieldAccess.getExpression()).process(COM_DEF_REF);
                    return new OptResult(Boolean.FALSE);
                };
            case ASSIGN:
                return (input) -> {
                    final JCTree.JCAssign jcAssign = (JCTree.JCAssign) jcStatement;
                    // 此处无需关心结果，所以无需判断返回值
                    getProcessor(jcAssign.getExpression()).process(COM_DEF_REF);
                    return new OptResult(Boolean.FALSE);
                };
            case VARDEF:
                return (input) -> {
                    final JCTree.JCVariableDecl jcVariableDecl = (JCTree.JCVariableDecl) jcStatement;
                    // 此处无需关心结果，所以无需判断返回值
                    getProcessor(jcVariableDecl.getInitializer()).process(COM_DEF_REF);
                    return new OptResult(Boolean.FALSE);
                };
            case APPLY:
                return jcMethodInvocationProcessor((JCTree.JCMethodInvocation) jcStatement);
            default:
                return (input) -> new OptResult(Boolean.FALSE);
        }
    }

}
