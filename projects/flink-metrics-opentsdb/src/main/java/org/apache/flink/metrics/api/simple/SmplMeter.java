package org.apache.flink.metrics.api.simple;

import org.apache.flink.metrics.Meter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author zhouhai
 * @createTime 2017/12/22
 * @description 一般用于qps/tps的统计，统计最近1分钟
 */
public class SmplMeter implements Meter {

    private AtomicLong count = new AtomicLong();
    private long startTime;
    private final boolean getAndReset;

    public SmplMeter() {
        this.startTime = System.currentTimeMillis();
        this.getAndReset = true;
    }

    public SmplMeter(boolean getAndReset) {
        this.startTime = System.currentTimeMillis();
        this.getAndReset = getAndReset;
    }

    @Override
    public void markEvent() {
        this.count.incrementAndGet();
    }

    @Override
    public void markEvent(long n) {
        this.count.addAndGet(n);
    }

    @Override
    public long getCount() {
        return this.count.get();
    }

    @Override
    public double getRate() {
        return calcMean();
    }

    private double calcMean() {
        long endTime = System.currentTimeMillis();
        double elapsedSec = (endTime - this.startTime) / 1000d;

        if (elapsedSec > 0) {
            if (getAndReset) {
                this.startTime = endTime;
                return this.count.getAndSet(0L) / elapsedSec;
            } else {
                return this.count.get() / elapsedSec;
            }
        }

        return 0.0;
    }
}
