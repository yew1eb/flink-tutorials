package org.apache.flink.metrics.opentsdb;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.metrics.*;
import org.apache.flink.metrics.groups.UnregisteredMetricsGroup;
import org.apache.flink.metrics.opentsdb.simpleclient.MetricUtil;
import org.apache.flink.metrics.opentsdb.simpleclient.MetricWrapper;
import org.apache.flink.metrics.opentsdb.simpleclient.OpenTsdb;
import org.apache.flink.metrics.opentsdb.simpleclient.OpenTsdbMetric;
import org.apache.flink.metrics.reporter.MetricReporter;
import org.apache.flink.metrics.reporter.Scheduled;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Description TODO
 * @Date 2018/09/10 12:10
 * @Created by yew1eb
 */
@Slf4j
public class OpenTsdbReporter implements MetricReporter, Scheduled, CharacterFilter {

    private transient OpenTsdb client;
    private boolean debug;
    private String delimiter;
    private String jobName;

    protected final Map<Gauge<?>, MetricWrapper> gauges = new ConcurrentHashMap<>();
    protected final Map<Counter, MetricWrapper> counters = new ConcurrentHashMap<>();
    protected final Map<Histogram, MetricWrapper> histograms = new ConcurrentHashMap<>();
    protected final Map<Meter, MetricWrapper> meters = new ConcurrentHashMap<>();


    @Override
    public void open(MetricConfig config) {
        String url = config.getString("url", "http://localhost:4242");
        int batchSize = config.getInteger("batchSize", 100);
        this.debug = config.getBoolean("debug", false);
        this.delimiter = config.getString("scope.delimiter", ".");
        this.jobName = System.getProperty("job_name", null);
        this.client = new OpenTsdb(url, batchSize);
    }


    @Override
    public void notifyOfAddedMetric(Metric metric, String metricName, MetricGroup metricGroup) {
        MetricWrapper.MetricWrapperBuilder builder = MetricWrapper.builder().metricRef(metric);
        String name = metricGroup.getMetricIdentifier(metricName, this);

        MetricWrapper metricWrapper = MetricUtil.completeMetricWrapper(builder, name, this.delimiter);

        if (metricWrapper == null) {
            log.warn("This {} metric is invalid !", name);
            return;
        }

        if (StringUtils.isNotEmpty(this.jobName)) {
            metricWrapper.getTags().put(MetricUtil.TAG_JOB_NAME, this.jobName);
        }

        synchronized (this) {
            if (metric instanceof Counter) {
                counters.put((Counter) metric, metricWrapper);
            } else if (metric instanceof Gauge) {
                if (((Gauge) metric).getValue() instanceof Number) {
                    gauges.put((Gauge<?>) metric, metricWrapper);
                } else {
                    log.warn("This gauge {} metric's value type not Number, will be ignored when report!", metricName);
                }
            } else if (metric instanceof Histogram) {
                histograms.put((Histogram) metric, metricWrapper);
            } else if (metric instanceof Meter) {
                meters.put((Meter) metric, metricWrapper);
            } else {
                log.warn("Cannot add unknown metric type {}. This indicates that the reporter " +
                        "does not support this metric type.", metric.getClass().getName());
            }
        }
    }


    @Override
    public void notifyOfRemovedMetric(Metric metric, String metricName, MetricGroup metricGroup) {
        synchronized (this) {
            if (metric instanceof Counter) {
                counters.remove(metric);
            } else if (metric instanceof Gauge) {
                gauges.remove(metric);
            } else if (metric instanceof Histogram) {
                histograms.remove(metric);
            } else if (metric instanceof Meter) {
                meters.remove(metric);
            } else {
                log.warn("Cannot remove unknown metric type {}. This indicates that the reporter " +
                        "does not support this metric type.", metric.getClass().getName());
            }
        }
    }

    @Override
    public void report() {
        List<OpenTsdbMetric> metrics = new ArrayList<>();

        long timestamp = System.currentTimeMillis() / 1000;

        for (Map.Entry<Counter, MetricWrapper> entry : counters.entrySet()) {
            long value = entry.getKey().getCount();
            MetricWrapper wrapper = entry.getValue();
            OpenTsdbMetric metric = MetricUtil.generateOpenTsdbMetric(wrapper.getName(), value, timestamp, wrapper.getTags());
            metrics.add(metric);
        }

        for (Map.Entry<Gauge<?>, MetricWrapper> entry : gauges.entrySet()) {
            if (!(entry.getKey().getValue() instanceof Number)) {
                notifyOfRemovedMetric(entry.getKey(), "ignore", new UnregisteredMetricsGroup());
            } else {
                Number value = (Number) entry.getKey().getValue();
                MetricWrapper wrapper = entry.getValue();
                OpenTsdbMetric metric = MetricUtil.generateOpenTsdbMetric(wrapper.getName(), value, timestamp, wrapper.getTags());
                metrics.add(metric);
            }
        }

        for (Map.Entry<Meter, MetricWrapper> entry : meters.entrySet()) {
            double value = entry.getKey().getRate();
            MetricWrapper wrapper = entry.getValue();
            OpenTsdbMetric metric = MetricUtil.generateOpenTsdbMetric(wrapper.getName(), value, timestamp, wrapper.getTags());
            metrics.add(metric);
        }

        for (Map.Entry<Histogram, MetricWrapper> entry : histograms.entrySet()) {
            Histogram histogram = entry.getKey();
            MetricWrapper wrapper = entry.getValue();
            Set<OpenTsdbMetric> openTsdbMetrics = MetricUtil.generateHistogramMetircs(wrapper.getName(), histogram, timestamp, wrapper.getTags());
            metrics.addAll(openTsdbMetrics);
        }

        if (this.debug) {
            Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
            log.info("[DEBUG] - Print opentsdb metrics = {}\n\n", gson.toJson(metrics));
        }

        List<OpenTsdbMetric> validMetrics = new ArrayList<>();
        for (OpenTsdbMetric metric : metrics) {
            if (MetricUtil.validOpenTsdbMetric(metric)) {
                validMetrics.add(metric);
            }
        }


        client.send(validMetrics);
    }


    @Override
    public void close() {
    }


    @Override
    public String filterCharacters(String s) {
        return MetricUtil.filterText(s);
    }

}
