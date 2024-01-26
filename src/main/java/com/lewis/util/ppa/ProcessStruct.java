package com.lewis.util.ppa;

import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;

/**
 * 基础 jctree 处理所需要的数据的对象化封装
 *
 * @author Lewis
 * @version 1.0
 * @since 2022-05-20 10:10
 */
public class ProcessStruct {

    // 用于帮助构建一些参数
    private final JavacElements elementUtils;

    // 用于帮助构建 JCTree 结构体
    final TreeMaker treeMaker;

    public ProcessStruct(Context context, JavacElements elementUtils) {
        this.elementUtils = elementUtils;
        this.treeMaker = TreeMaker.instance(context);
    }

    public JavacElements getElementUtils() {
        return elementUtils;
    }

    public TreeMaker getTreeMaker() {
        return treeMaker;
    }
}