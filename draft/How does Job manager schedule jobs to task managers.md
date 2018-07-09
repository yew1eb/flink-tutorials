Flink does not necessarily spread the tasks across all available TMs. It depends a little bit on the topology of the job, because in general it is beneficial to deploy downstream tasks to the same machines where their input tasks run. I described the process in more detail here [1]. This link [2] also contains some information regarding Flink's internal scheduling.

[1] [http://apache-flink-<wbr>mailing-list-archive.1008284.<wbr>n3.nabble.com/Scheduling-task-<wbr>slots-in-round-robin-<wbr>tp12068p12143.html](http://apache-flink-mailing-list-archive.1008284.n3.nabble.com/Scheduling-task-slots-in-round-robin-tp12068p12143.html)

[2] [https://ci.apache.org/<wbr>projects/flink/flink-docs-<wbr>release-1.2/internals/job_<wbr>scheduling.html](https://ci.apache.org/projects/flink/flink-docs-release-1.2/internals/job_scheduling.html)
