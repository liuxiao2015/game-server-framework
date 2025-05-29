/*
 * 文件名: TimerTask.java
 * 用途: 定时任务
 * 实现内容:
 *   - 定时器任务抽象
 *   - 任务执行接口
 *   - 任务取消回调
 *   - 异常处理
 * 技术选型:
 *   - 接口设计
 *   - 函数式接口支持
 *   - 异常处理机制
 * 依赖关系:
 *   - 被HashedWheelTimer使用
 *   - 定义定时任务行为规范
 *   - 与游戏业务定时任务集成
 * 作者: liuxiao2015
 * 日期: 2025-05-29
 */
package com.lx.gameserver.frame.concurrent.timer;

/**
 * 定时任务接口
 * <p>
 * 定义定时器任务的基本行为规范，包括任务执行、取消回调、异常处理等。
 * 适用于游戏内大量定时任务管理，如Buff过期、技能CD等。
 * </p>
 *
 * @author liuxiao2015
 * @version 1.0.0
 * @since 2025-05-29
 */
public interface TimerTask {

    /**
     * 执行定时任务
     * <p>
     * 当定时器触发时会调用此方法执行具体的任务逻辑。
     * 实现类应该确保此方法执行时间不会过长，以免影响定时器性能。
     * </p>
     *
     * @throws Exception 任务执行时可能抛出的异常
     */
    void run() throws Exception;

    /**
     * 任务取消回调
     * <p>
     * 当任务被取消时会调用此方法，实现类可以在此进行清理工作。
     * 此方法的默认实现为空，子类可以根据需要重写。
     * </p>
     */
    default void onCancel() {
        // 默认空实现，子类可以重写
    }

    /**
     * 任务异常处理
     * <p>
     * 当任务执行发生异常时会调用此方法，实现类可以在此进行异常处理。
     * 此方法的默认实现是记录错误日志，子类可以根据需要重写。
     * </p>
     *
     * @param throwable 异常信息
     */
    default void onException(Throwable throwable) {
        // 默认记录错误日志
        System.err.println("定时任务执行异常: " + throwable.getMessage());
        throwable.printStackTrace();
    }

    /**
     * 获取任务名称
     * <p>
     * 返回任务的可读名称，用于日志记录和监控。
     * 默认返回类的简单名称，子类可以重写以提供更有意义的名称。
     * </p>
     *
     * @return 任务名称
     */
    default String getTaskName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 判断任务是否可以重复执行
     * <p>
     * 返回true表示任务可以重复执行（周期性任务），
     * 返回false表示任务只执行一次（一次性任务）。
     * 默认返回false，子类可以根据需要重写。
     * </p>
     *
     * @return 是否可以重复执行
     */
    default boolean isRepeatable() {
        return false;
    }

    /**
     * 创建简单的定时任务
     * <p>
     * 基于Runnable创建简单的一次性定时任务的工厂方法。
     * </p>
     *
     * @param runnable 任务逻辑
     * @return 定时任务实例
     */
    static TimerTask of(Runnable runnable) {
        return of(runnable, runnable.getClass().getSimpleName());
    }

    /**
     * 创建带名称的定时任务
     * <p>
     * 基于Runnable创建带自定义名称的一次性定时任务的工厂方法。
     * </p>
     *
     * @param runnable 任务逻辑
     * @param taskName 任务名称
     * @return 定时任务实例
     */
    static TimerTask of(Runnable runnable, String taskName) {
        return new TimerTask() {
            @Override
            public void run() {
                runnable.run();
            }

            @Override
            public String getTaskName() {
                return taskName;
            }
        };
    }

    /**
     * 创建重复执行的定时任务
     * <p>
     * 基于Runnable创建可重复执行的定时任务的工厂方法。
     * </p>
     *
     * @param runnable 任务逻辑
     * @param taskName 任务名称
     * @return 定时任务实例
     */
    static TimerTask repeatable(Runnable runnable, String taskName) {
        return new TimerTask() {
            @Override
            public void run() {
                runnable.run();
            }

            @Override
            public String getTaskName() {
                return taskName;
            }

            @Override
            public boolean isRepeatable() {
                return true;
            }
        };
    }

    /**
     * 创建带异常处理的定时任务
     * <p>
     * 基于Runnable创建带自定义异常处理的定时任务的工厂方法。
     * </p>
     *
     * @param runnable         任务逻辑
     * @param taskName         任务名称
     * @param exceptionHandler 异常处理器
     * @return 定时任务实例
     */
    static TimerTask withExceptionHandler(Runnable runnable, String taskName, 
                                        java.util.function.Consumer<Throwable> exceptionHandler) {
        return new TimerTask() {
            @Override
            public void run() {
                runnable.run();
            }

            @Override
            public String getTaskName() {
                return taskName;
            }

            @Override
            public void onException(Throwable throwable) {
                if (exceptionHandler != null) {
                    exceptionHandler.accept(throwable);
                } else {
                    TimerTask.super.onException(throwable);
                }
            }
        };
    }

    /**
     * 创建带取消回调的定时任务
     * <p>
     * 基于Runnable创建带自定义取消回调的定时任务的工厂方法。
     * </p>
     *
     * @param runnable      任务逻辑
     * @param taskName      任务名称
     * @param cancelHandler 取消回调
     * @return 定时任务实例
     */
    static TimerTask withCancelHandler(Runnable runnable, String taskName, Runnable cancelHandler) {
        return new TimerTask() {
            @Override
            public void run() {
                runnable.run();
            }

            @Override
            public String getTaskName() {
                return taskName;
            }

            @Override
            public void onCancel() {
                if (cancelHandler != null) {
                    cancelHandler.run();
                }
            }
        };
    }
}