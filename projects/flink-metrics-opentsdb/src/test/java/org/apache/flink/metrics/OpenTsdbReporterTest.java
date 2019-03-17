package org.apache.flink.metrics;

import org.apache.flink.api.common.JobID;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.opentsdb.OpenTsdbReporter;
import org.apache.flink.runtime.metrics.MetricRegistryConfiguration;
import org.apache.flink.runtime.metrics.MetricRegistryImpl;
import org.apache.flink.runtime.metrics.groups.FrontMetricGroup;
import org.apache.flink.runtime.metrics.groups.TaskManagerJobMetricGroup;
import org.apache.flink.runtime.metrics.groups.TaskManagerMetricGroup;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @Description docker run -dp 4242:4242 petergrace/opentsdb-docker
 * @Date 2018/10/30 11:34
 * @Created by yew1eb
 */

public class OpenTsdbReporterTest {

    @Test
    @Ignore
    public void testReporter() {
        OpenTsdbReporter reporter = new OpenTsdbReporter();
        MetricConfig config = new MetricConfig();
        config.setProperty("url", "http://localhost:4242");
        config.setProperty("debug", "true");

        reporter.open(config);

        MetricGroup mp = createMetricsGroup();

        Counter c = new SimpleCounter();
        c.inc(1230);
        MetricGroup myMp = mp.addGroup("custom-group1", "1")
                .addGroup("custom-group");
        reporter.notifyOfAddedMetric(c, "task-qps", myMp);

        reporter.report();
    }

    private MetricGroup createMetricsGroup() {
        Configuration cfg = new Configuration();
        cfg.setString("metrics.scope.tm.job", "TM_JOB.<host>.<tm_id>.<job_name>");
        MetricRegistryImpl registry = new MetricRegistryImpl(MetricRegistryConfiguration.fromConfiguration(cfg));

        TaskManagerMetricGroup tmMetricGroup = new TaskManagerMetricGroup(registry, "localhost", "tm01");
        FrontMetricGroup<TaskManagerJobMetricGroup> metricGroup1 = new FrontMetricGroup<>(0, new TaskManagerJobMetricGroup(registry, tmMetricGroup, JobID.generate(), "job_1"));
        return metricGroup1;
    }
}
