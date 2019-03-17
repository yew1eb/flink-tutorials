package org.apache.flink.metrics.api.simple;

import org.apache.flink.metrics.Histogram;
import org.apache.flink.metrics.HistogramStatistics;

import java.io.Serializable;
import java.util.Arrays;

import static java.lang.Math.floor;
import static java.lang.Math.min;

/**
 * @author zhouhai
 * @createTime 2017/12/22
 * @description Histogram一般用于统计某一段操作执行的耗时分布
 */
public class SmplHistogram implements Histogram {

    private static final int DEFAULT_SIZE = 256;
    private static final int DEFAULT_MIN_SIZE = 50;
    private static final int DEFAULT_MAX_SIZE = 2048;

    private final long[] measurements;
    private int count;

    public SmplHistogram() {
        this.measurements = new long[DEFAULT_SIZE];
        this.count = 0;
    }

    /**
     *
     * @param size 采样窗口的大小
     * @throws RuntimeException
     */
    public SmplHistogram(int size) throws RuntimeException {
        if (DEFAULT_MIN_SIZE > size || DEFAULT_MAX_SIZE < size) {
            throw new IllegalArgumentException(size + "is not in [" + DEFAULT_MIN_SIZE + " , " + DEFAULT_MAX_SIZE + "]");
        }

        this.measurements = new long[size];
        this.count = 0;
    }

    @Override
    public synchronized void update(long value) {
        measurements[(int) (count++ % measurements.length)] = value;
    }

    @Override
    public synchronized long getCount() {
        return min(count, measurements.length);
    }

    @Override
    public HistogramStatistics getStatistics() {
        final long[] values = new long[(int) getCount()];
        for (int i = 0; i < values.length; i++) {
            synchronized (this) {
                values[i] = measurements[i];
            }
        }
        return new MTHistogramStatistics(values);
    }


    public static class MTHistogramStatistics extends HistogramStatistics implements Serializable {
        private static final long serialVersionUID = 1L;

        private long[] values;

        public MTHistogramStatistics(long[] values) {
            this.values = Arrays.copyOf(values, values.length);
            Arrays.sort(this.values);
        }

        @Override
        public double getQuantile(double quantile) {
            if (quantile < 0.0 || quantile > 1.0 || Double.isNaN(quantile)) {
                throw new IllegalArgumentException(quantile + " is not in [0..1]");
            }

            if (values.length == 0) {
                return 0.0;
            }

            final double pos = quantile * (values.length + 1);
            final int index = (int) pos;

            if (index < 1) {
                return values[0];
            }

            if (index >= values.length) {
                return values[values.length - 1];
            }

            final double lower = values[index - 1];
            final double upper = values[index];
            return lower + (pos - floor(pos)) * (upper - lower);
        }

        @Override
        public int size() {
            return values.length;
        }


        @Override
        public long[] getValues() {
            return Arrays.copyOf(values, values.length);
        }

        @Override
        public long getMax() {
            if (values.length == 0) {
                return 0;
            }
            return values[values.length - 1];
        }

        @Override
        public long getMin() {
            if (values.length == 0) {
                return 0;
            }
            return values[0];
        }


        @Override
        public double getMean() {
            if (values.length == 0) {
                return 0;
            }

            double sum = 0;
            for (long value : values) {
                sum += value;
            }
            return sum / values.length;
        }

        @Override
        public double getStdDev() {
            // two-pass algorithm for variance, avoids numeric overflow

            if (values.length <= 1) {
                return 0;
            }

            final double mean = getMean();
            double sum = 0;

            for (long value : values) {
                final double diff = value - mean;
                sum += diff * diff;
            }

            final double variance = sum / (values.length - 1);
            return Math.sqrt(variance);
        }


    }
}
