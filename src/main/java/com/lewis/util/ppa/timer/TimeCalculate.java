package com.lewis.util.ppa.timer;

import com.lewis.util.ppa.timer.annot.BlockTimerEnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * 该工具类作用为为标记的各个节点耗时计算及展示，以下为设计逻辑
 * <pre>
 * 遇到一个需要标记耗时的位置，先该位置之前通过 {@link TimeCalculate#newInstance(String)} 创建一个 {@link TimeDetail} 并在之后调用 {@link TimeDetail#close()} 完成计时器实例的生命周期
 * 最终展示目标为一个以根节点为中心的耗时树，可方便的查看每个节点的使用耗时，及所占父节点总耗时的百分比
 * 1、通过 {@link TimeCalculate#newInstance(String)} 创建一个计时器实例 {@link TimeDetail}、并使用 {@link ThreadLocal} 进行线程对象保存，由于单线程内的调用为顺序调用，所以对于同一线程内的调用不存在并发处理场景
 * 2、在当前线程已有实例对象的情况下，进行对象代理，新节点的前后节点等信息记录下来，记录其节点深度，更新深度迭代器，增加一个单位。（代理的具体实现方案请查看 {@link TimeDetail#inheritTimer}）
 * 3、每当计时器的生命周期结束的时候进行耗时确认，并且更新深度迭代器，减小一个单位，并判断是否为根节点（判断方法为 {@link TimeDetail#depth} 是否等于 0），若为根节点，则结束流程，统计并输出时间文本信息，删除 {@link ThreadLocal} 中的对象
 * 4、通过节点间的代理形成使得计时器间形成树形结构，便于后期的时间文本输出构建。
 * </pre>
 *
 * @author Lewis
 * @version 1.0
 * @since 2022-04-13 10:37
 */
public class TimeCalculate {
    private static final Logger logger = LoggerFactory.getLogger(TimeCalculate.class);

    private static boolean enable = false;

    // 公共继承变量
    private final static Map<Long, TimeDetail> TIMER_DETAIL = new ConcurrentHashMap<>();

    // 私有继承变量
    private final static ThreadLocal<TimeDetail> TIMER_DETAIL_PRIVATE = new ThreadLocal<>();

    // 为多线程输出日志而定义的线程池，在静态代码块中进行初始化
    private static final ThreadPoolExecutor threadPool;

    private static final char NEWLINE = '\n';

    // 输出格式，当前无考虑扩展显示样式
    private static final String DEFAULT_FORMAT = ">>>Timer report<<<" + NEWLINE +
            "===>thread name <{}>" + NEWLINE +
            "===>output time {}" + NEWLINE +
            "===>timer detail" + NEWLINE +
            "{}";

    private static final int QUEUE_LIMIT = 4096;

    // 此公共常量为新建实例的方法名，供以静态注入构建工程使用
    public static final String NEW_INSTANCE = "newInstance";

    // 此公共常量为默认构建对象名，供以静态注入构建工程使用
    public static final String DEFAULT_NAMING = "autoGenerateTimer";

    // 此公共常量为 block 方法名，供以静态注入构建工程使用
    public static final String TIMER_BLOCK_STATE = "TimeCalculate.block";

    static {
        threadPool = new ThreadPoolExecutor(8,
                32,
                3,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(QUEUE_LIMIT),
                new ThreadFactory() {
                    private final AtomicLong increment = new AtomicLong();
                    private static final String format = "Timer Thread-";

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r);
                        thread.setName(format + increment.getAndIncrement());
                        return thread;
                    }
                }
        );
    }

    /**
     * <pre>
     * 创建一个新的计时器的生命周期实例
     * 判断 {@link TimeCalculate#TIMER_DETAIL} 中该线程是否已有对应的实例对象
     * 有：做继承创建
     * 无：做新增创建
     * 将新的计时器实例放入 {@link TimeCalculate#TIMER_DETAIL} 中
     * </pre>
     *
     * @param stageName 计时器名称
     * @return 计时器对象
     */
    @SuppressWarnings("unused")
    public static TimeDetail newInstance(String stageName) {
        if (!enable) return null;
        TimeDetail timeDetail = TIMER_DETAIL_PRIVATE.get();
        if (Objects.isNull(timeDetail)) {
            timeDetail = TimeDetail.newTimer(stageName);
        } else {
            timeDetail = TimeDetail.inheritTimer(timeDetail, stageName, true);
        }
        timeDetail.setAwaitMode(false);
        TIMER_DETAIL_PRIVATE.set(timeDetail);
        TimeDetail superTimer = timeDetail.getSuperTimer();
        if (Objects.isNull(superTimer) || superTimer.isInheritBlock()) {
            TIMER_DETAIL.put(Thread.currentThread().getId(), timeDetail);
        } else {
            timeDetail.setNewBlock();
        }
        return timeDetail;
    }

    @SuppressWarnings("unused")
    public static TimeDetail newInstance(Thread t, String stageName) {
        if (!enable) return null;
        long id = t.getId();
        Thread currentThread = Thread.currentThread();
        boolean newThread = !currentThread.equals(t);
        TimeDetail timeDetail;
        if (newThread) {
            timeDetail = TIMER_DETAIL.get(id);
        } else {
            timeDetail = TIMER_DETAIL_PRIVATE.get();
        }
        if (Objects.isNull(timeDetail)) {
            timeDetail = TimeDetail.newTimer(stageName);
        } else {
            boolean inheritBlock = !newThread;
            int currentDepth = timeDetail.getCurrentDepth();
            timeDetail = TimeDetail.inheritTimer(timeDetail, stageName, false);
            timeDetail.setNewBlock();
            timeDetail.resetIncrement(currentDepth + 1);

        }
        TIMER_DETAIL_PRIVATE.set(timeDetail);
        if (newThread) {
            timeDetail.setBaseDepth(timeDetail.getDepth());
            TIMER_DETAIL.put(currentThread.getId(), timeDetail);
        }
        return timeDetail;
    }

    @SuppressWarnings("unused")
    public static void endInstance(TimeDetail timer) {
        if (Objects.nonNull(timer)) {
            timer.close();
        }
    }

    /**
     * 占位符，用于 JAVA 的代码块的时间判断，必须配合注解 {@link BlockTimerEnable} 使用，无法单独使用，若要修改 block 方法名，请同时变更 {@link TimeCalculate#TIMER_BLOCK_STATE} 常量为同名常量
     */
    @SuppressWarnings("unused")
    public static void block(String stageName) {
    }

    /**
     * 占位符，用于 JAVA 的代码块的时间判断，必须配合注解 {@link BlockTimerEnable} 使用，无法单独使用，若要修改 block 方法名，请同时变更 {@link TimeCalculate#TIMER_BLOCK_STATE} 常量为同名常量.
     * 额外提提供了指定夫线程的命名，用于异步流程的描述
     */
    @SuppressWarnings("unused")
    public static void block(Thread t, String stageName) {
    }

    /**
     * Timer 计时器开启
     */
    @SuppressWarnings("unused")
    public static void open() {
        enable = true;
    }

    /**
     * Timer 计时器关闭
     */
    @SuppressWarnings("unused")
    public static void close() {
        enable = false;
    }

    /**
     * Timer 计时器停用
     */
    @SuppressWarnings("unused")
    public static void shutdown() {
        threadPool.shutdown();
    }

    /**
     * Timer 的计时节点对象
     *
     * @author Lewis
     * @version 1.0
     * @since 2022-04-13 10:39
     */
    public static class TimeDetail implements AutoCloseable, Cloneable {
        // 防止重复关闭
        private volatile boolean closed = false;
        // 计时器节点名称
        private String stageName;
        // 开始时间
        private long startTime;
        // 结束时间
        private long useTime;

        private int baseDepth;

        // 计时器深度
        private int depth;
        // 父计时器节点对象
        private TimeDetail superTimer;
        // 子计时器节点集合
        private Queue<TimeDetail> timeDetailList;
        // 计时器单位
        private TimeUnit timeUint = NANOSECONDS;
        // 深度迭代器
        private AtomicInteger increment;
        // 节点内容显示构建器
        private final StringBuilder stringBuilder;

        private boolean inheritBlock;

        private boolean awaitMode;

        private TimeDetail(String stageName, AtomicInteger increment, StringBuilder stringBuilder, boolean inheritBlock) {
            this.increment = increment;
            this.stringBuilder = stringBuilder;
            this.inheritBlock = inheritBlock;
            this.awaitMode = false;
            this.baseDepth = 0;
            init(stageName);
        }

        /**
         * 初始化计时器字段
         *
         * @param stageName 计时器节点名称
         */
        private void init(String stageName) {
            this.stageName = stageName;
            this.startTime = getNow();
            this.timeDetailList = new ConcurrentLinkedQueue<>();
            this.depth = increment.getAndIncrement();
            this.useTime = 0;
        }

        /**
         * 初始化计时器字段
         *
         * @param stageName 计时器节点名称
         */
        private void initWithSuper(String stageName, int superDepth) {
            this.stageName = stageName;
            this.startTime = getNow();
            this.timeDetailList = new ConcurrentLinkedQueue<>();
            this.awaitMode = true;
            this.depth = superDepth;
            this.useTime = 0;
        }

        /**
         * 创建一个新的计时器实例，该写法为规范化构建实例，不允许主动 new 实例（已在构造方法中做 private 限制）
         *
         * @param stageName 计时器名称
         * @return 计时器实例
         */
        protected static TimeDetail newTimer(String stageName) {
            return new TimeDetail(stageName, new AtomicInteger(0), new StringBuilder(), true);
        }

        /**
         * <pre>
         * 继承一个历史计时器实例
         * 1、先根据历史计时器构建一个新的计时器实例
         * 2、根据历史计时器的父节点判断当前节点应该所属的节点位置，当前为根据历史计时器的深度与新计时器的深度差计算得知。
         * </pre>
         *
         * @param superInstance 继承的节点实例，不一定时父节点，只是作为上个处理的处理节点
         * @param stageName     计时器名称
         * @return 计时器实例
         */
        protected static TimeDetail inheritTimer(TimeDetail superInstance, String stageName, boolean sameBlock) {
            // 此处使用 clone 替代 new 方法，以加快效率
            TimeDetail timeDetail;
            timeDetail = superInstance.clone();
            if (sameBlock) {
                timeDetail.init(stageName);
            } else {
                timeDetail.initWithSuper(stageName, superInstance.getCommonDepth());
            }
            long comp = superInstance.getDepth() - timeDetail.getDepth();
            if (comp >= 0) {
                for (int index = 0; index <= comp; index++) {
                    superInstance = superInstance.getSuperTimer();
                }
            }
            timeDetail.setSuperTimer(superInstance);
            superInstance.setTimeDetail(timeDetail);
            return timeDetail;
        }

        private void setNewBlock() {
            inheritBlock = false;
        }

        private void setBaseDepth(int baseDepth) {
            this.baseDepth = baseDepth;
        }

        protected boolean isInheritBlock() {
            return inheritBlock;
        }

        private void setTimeDetail(TimeDetail timeDetail) {
            if (Objects.isNull(this.timeDetailList)) this.timeDetailList = new ConcurrentLinkedQueue<>();
            this.timeDetailList.add(timeDetail);
        }

        protected TimeDetail getSuperTimer() {
            return superTimer;
        }

        protected int getCommonDepth() {
            return increment.get();
        }

        protected int getDepth() {
            return this.depth;
        }

        protected void resetIncrement(int value) {
            this.increment = new AtomicInteger(value);
        }

        protected int getCurrentDepth() {
            return increment.get();
        }

        protected void setSuperTimer(TimeDetail superTimer) {
            this.superTimer = superTimer;
        }

        @SuppressWarnings("all")
        protected void setAwaitMode(boolean awaitMode) {
            this.awaitMode = awaitMode;
        }

        protected boolean isAwaitMode() {
            return this.awaitMode;
        }

        @SuppressWarnings("unused")
        public void finish() {
            try {
                close();
            } catch (Exception e) {
                e.fillInStackTrace();
            }
        }

        /**
         * 完成该节点的时间统计
         */
        private void completed() {
            useTime = getNow() - startTime;
            if (useTime > 1000 * 1000 * 1000) {
                timeUint = TimeUnit.SECONDS;
            } else if (useTime > 1000 * 1000) {
                timeUint = TimeUnit.MILLISECONDS;
            } else if (useTime > 1000) {
                timeUint = TimeUnit.MICROSECONDS;
            }
        }

        private String getUnit(TimeUnit timeUint) {
            switch (timeUint) {
                case NANOSECONDS:
                    return "ns";
                case MICROSECONDS:
                    return "μs";
                case MILLISECONDS:
                    return "ms";
                case SECONDS:
                    return "s";
                default:
                    throw new NoSuchElementException("not sport this timeUint <" + timeUint.name() + ">");
            }
        }

        /**
         * <pre>
         * 根节点调用的打印数据处理方法
         * 根据不同的时间大小，进行不同数据展示样式的变更
         * </pre>
         */
        private void genConsole() {
            for (int i = 0, length = depth * 3; i < length; i++) stringBuilder.append(" ");
            stringBuilder.append("---> stage<")
                    .append(stageName)
                    .append("> track total time: ")
                    .append(NANOSECONDS.convert(useTime, timeUint))
                    .append(getUnit(timeUint))
                    .append("\n");
            if (Objects.nonNull(timeDetailList)) {
                for (TimeDetail timeDetail : timeDetailList) {
                    timeDetail.genConsole(this.useTime);
                }
            }
        }

        /**
         * <pre>
         * 子节点代理对象的打印数据的处理方法
         * 相较于根节点子节点添加了与上层节点的交互，用于判断所有子节点占用耗时百分比
         * </pre>
         *
         * @param totalTime 上层调用总耗时
         */
        private void genConsole(double totalTime) {
            for (int i = 0, length = depth * 3; i < length; i++) stringBuilder.append(" ");
            stringBuilder.append("---> stage<")
                    .append(stageName)
                    .append("> time: ")
                    .append(String.format("%.2f", (double) NANOSECONDS.convert(useTime, timeUint)))
                    .append(getUnit(timeUint));
            if (isAwaitMode()) {
                stringBuilder.append(" for async await");
            } else {
                stringBuilder.append(String.format(" proportion: %.2f", ((double) useTime) / totalTime * 100)).append("%");
            }
            stringBuilder.append("\n");
            if (Objects.nonNull(timeDetailList)) {
                for (TimeDetail timeDetail : timeDetailList) {
                    timeDetail.genConsole(this.useTime);
                }
            }
        }

        /**
         * 获取时间戳方法，独立出来是为了，后续若存在时间戳获取方法变更的情况下，可快捷的直接变更
         *
         * @return 当前时间戳
         */
        private static long getNow() {
            return System.nanoTime();
        }

        /**
         * <pre>
         * 此处逻辑是由于 ide 在 debug 的过程中会调用 {@link TimeDetail#toString()} 方法
         * 会导致 stringBuilder 被提前触发计算，因此在每次计算前需要先进行数据清空
         * 为避免并发调用所以此处使用 synchronized 作为限制
         * 由于方法调用并非频繁调用（对于单个对象而言）, 且以异步调用为主
         * 所以 synchronized 性能消耗，可忽略不计
         * </pre>
         *
         * @return 树形的时间数据结构
         */
        private synchronized String getDetail() {
            if (stringBuilder.length() >= 0) {
                stringBuilder.delete(0, stringBuilder.length());
            }
            genConsole();
            return stringBuilder.toString();
        }

        /**
         * 同步调用进行日志打印，提供方法实际使用较为繁琐，不推荐
         *
         * @return 格式化后的 Timer 结果
         */
        @Override
        public String toString() {
            final String threadName = Thread.currentThread().getName();
            final LocalTime nowTime = LocalTime.now();
            return DEFAULT_FORMAT
                    .replaceFirst("\\{}", threadName)
                    .replaceFirst("\\{}", nowTime.toString())
                    .replaceFirst("\\{}", getDetail());
        }

        /**
         * <pre>
         * 实现核心之一
         * 使用 autoClose 自动在 try 块结束后调用该方法
         * 手动执行亦可
         * 逻辑为先执行 {@link TimeDetail#completed()} 方法，完成上下文时间耗时计算
         * 然后降低深度计算器的值 {@link TimeDetail#increment}
         * 然后判断当前深度 {@link TimeDetail#depth} 是否为 0 以及该功能是否开启 {@link TimeCalculate#enable}
         * 最终通过多线程输出时间记录记录日志
         * </pre>
         */
        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            completed();
            increment.decrementAndGet();
            if (depth == 0) {
                if (enable) {
                    final String threadName = Thread.currentThread().getName();
                    final LocalTime nowTime = LocalTime.now();
                    try {
                        threadPool.execute(() -> logger.info(DEFAULT_FORMAT, threadName, nowTime, getDetail()));
                    } catch (RejectedExecutionException e) {
                        logger.error("Timer's waiting queue is too large, limit <{}>", QUEUE_LIMIT);
                    }
                }
                TIMER_DETAIL.remove(Thread.currentThread().getId()).close();
            }
            if (depth == baseDepth) {
                TIMER_DETAIL_PRIVATE.remove();
                TIMER_DETAIL.remove(Thread.currentThread().getId()).close();
            }
        }

        @Override
        public TimeDetail clone() {
            try {
                return (TimeDetail) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError();
            }
        }
    }
}
