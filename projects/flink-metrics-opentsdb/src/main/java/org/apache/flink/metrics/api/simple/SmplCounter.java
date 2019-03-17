package org.apache.flink.metrics.api.simple;

import org.apache.flink.metrics.Counter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author zhouhai
 * @createTime 2017/12/22
 * @description 计数器，统计每一分钟的量
 */
public class SmplCounter implements Counter {
    private AtomicLong count;
    private boolean getAndReset;

    public SmplCounter() {
        this.count = new AtomicLong();
        this.getAndReset = true;
    }

    public SmplCounter(boolean getAndReset) {
        this.count = new AtomicLong();
        this.getAndReset = getAndReset;
    }

    @Override
    public void inc() {
        this.count.incrementAndGet();
    }

    @Override
    public void inc(long n) {
        this.count.addAndGet(n);
    }

    @Override
    public void dec() {
        this.count.decrementAndGet();
    }

    @Override
    public void dec(long n) {
        this.count.addAndGet(-n);
    }

    public long getValue() {
        return this.count.get();
    }

    /**
     * 指标定时上报的时候会调用这个方法
     *
     * @return
     */
    @Override
    public long getCount() {
        if (getAndReset) {
            return this.count.getAndSet(0L);
        } else {
            return this.count.get();
        }
    }
}
