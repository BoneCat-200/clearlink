package com.gugucat.shortlink.project.toolkit;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务工具类
 */
public final class TransactionUtils {

    private TransactionUtils() {
    }

    /**
     * 在当前事务提交后执行；没有事务时立即执行
     * <p>
     * 延时删除缓存的消息必须在数据库提交之后再发：如果先发消息，
     * 消费端可能在事务提交前就删掉缓存，读请求仍会读到旧值并再次回填。
     *
     * @param action 提交后要执行的动作
     */
    public static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
