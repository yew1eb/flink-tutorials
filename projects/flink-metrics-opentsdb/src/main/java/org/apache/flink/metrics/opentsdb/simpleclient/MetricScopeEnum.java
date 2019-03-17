package org.apache.flink.metrics.opentsdb.simpleclient;


/**
 * @author zhouhai
 * @createTime 2017/12/21
 * @description
metrics.scope.jm: JM.<host>
metrics.scope.jm.job: JM_JOB.<host>.<job_name>
metrics.scope.tm: TM.<host>.<tm_id>
metrics.scope.tm.job: TM_JOB.<host>.<tm_id>.<job_name>
metrics.scope.task: TASK.<host>.<tm_id>.<job_name>.<task_name>.<subtask_index>
metrics.scope.operator: OPERATOR.<host>.<tm_id>.<job_name>.<operator_name>.<subtask_index>
 */
public enum MetricScopeEnum {
    JM("JM", new String[]{"host"}),
    JM_JOB("JM_JOB", new String[]{"host", "job_name"}),
    TM("TM", new String[]{"host", "tm_id"}),
    TM_JOB("TM_JOB", new String[]{"host", "tm_id", "job_name"}),
    TASK("TASK", new String[]{"host", "tm_id", "job_name", "task_name", "subtask_index"}),
    OPERATOR("OPERATOR", new String[]{"host", "tm_id", "job_name", "operator_name", "subtask_index"}),
    UDF("UDF", new String[]{"host", "tm_id", "job_name", "operator_name", "subtask_index"});

    private String name;
    private String[] scopeFormats;

    MetricScopeEnum(String name, String[] scopeFormats) {
        this.name = name;
        this.scopeFormats = scopeFormats;
    }

    public static MetricScopeEnum of(String name) {
        for (MetricScopeEnum metricScope : MetricScopeEnum.values()) {
            if (metricScope.getName().equals(name)) {
                return metricScope;
            }
        }
        return OPERATOR;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String[] getScopeFormats() {
        return scopeFormats;
    }

    public void setScopeFormats(String[] scopeFormats) {
        this.scopeFormats = scopeFormats;
    }
}
