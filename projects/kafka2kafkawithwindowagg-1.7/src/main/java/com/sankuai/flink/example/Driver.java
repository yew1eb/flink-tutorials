package com.sankuai.flink.example;

import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer08;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer08;
import org.apache.flink.util.Collector;
import org.apache.flink.util.StringUtils;

import java.util.Properties;


/**
 * Created by yew1eb.
 * com.sankuai.flink.demo.Driver
 */
public class Driver {

    public static final String jobName = "kafka2kafkawithwindowagg";

    public static void main(String[] args) throws Exception {
        ParameterTool params = ParameterTool.fromArgs(args);
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.disableOperatorChaining();
        env.setStreamTimeCharacteristic(TimeCharacteristic.IngestionTime);
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
        env.getCheckpointConfig().setCheckpointInterval(20000);
        String srcTopic = params.get("src.topic", "org.data_log_sdk_metrics");
        String dstTopic = params.get("dst.topic", "app.zh_data_log_sdk_metrics");
        //Integer srcParallelism = params.getInt("src.p", 12);

        Boolean online = params.getBoolean("online", true);
        Properties props = new Properties();
        if (online) {
            props.setProperty("bootstrap.servers", KafkaUtil.prodBrokerList);
            props.setProperty("zookeeper.connect", KafkaUtil.prodZkList);
        } else {
            props.setProperty("bootstrap.servers", KafkaUtil.testBrokerList);
            props.setProperty("zookeeper.connect", KafkaUtil.testZkList);
        }

        props.setProperty("group.id", jobName);
        FlinkKafkaConsumer08<String> consumer08 = new FlinkKafkaConsumer08<String>(srcTopic, new SimpleStringSchema(), props);
        //DataStream<String> source = env.addSource(consumer08).setParallelism(srcParallelism);

        DataStream<String> input = env.addSource(consumer08);
        DataStream<Tuple2<Integer, Integer>> stage1 = input.flatMap(new RichFlatMapFunction<String, Tuple2<Integer, Integer>>() {

            @Override
            public void flatMap(String s, Collector<Tuple2<Integer, Integer>> collector) throws Exception {
                if (!StringUtils.isNullOrWhitespaceOnly(s)) {
                    collector.collect(new Tuple2<Integer, Integer>(s.hashCode(), 1));
                }
            }
        });


        DataStream<Tuple4<Integer, Long, Long, Integer>> result = stage1.keyBy(0)
                .timeWindow(Time.minutes(1))
                .reduce(new ReduceFunction<Tuple2<Integer, Integer>>() {
                    @Override
                    public Tuple2<Integer, Integer> reduce(Tuple2<Integer, Integer> t1, Tuple2<Integer, Integer> t2) throws Exception {
                        return new Tuple2<>(t1.f0, t1.f1 + t2.f1);
                    }
                }, new WindowFunction<Tuple2<Integer, Integer>, Tuple4<Integer, Long, Long, Integer>, Tuple, TimeWindow>() {

                    @Override
                    public void apply(Tuple key, TimeWindow window, Iterable<Tuple2<Integer, Integer>> input, Collector<Tuple4<Integer, Long, Long, Integer>> out) throws Exception {
                        for (Tuple2<Integer, Integer> in : input) {
                            out.collect(new Tuple4<>(in.f0,
                                    window.getStart(),
                                    window.getEnd(),
                                    in.f1));
                        }
                    }
                });

        DataStream<String> stringedResult = result.map(Tuple4::toString);

        System.out.println(props);
        FlinkKafkaProducer08<String> producer08 = new FlinkKafkaProducer08<String>(dstTopic, new SimpleStringSchema(), props);

        stringedResult.addSink(producer08);
        env.execute(jobName);
    }
}
