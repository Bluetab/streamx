package io.confluent.connect.hdfs.partitioner;

import io.confluent.connect.hdfs.HdfsSinkConnectorConfig;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.kafka.connect.sink.SinkRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class CORPartitioner implements Partitioner {
  private static final Logger log = LoggerFactory.getLogger(SchemaPartitioner.class);
  private List<Partitioner> partitionerList = new ArrayList<>();


  @Override
  public void configure(Map<String, Object> config) {
    List<String> partitionerNamesList =
            Arrays.asList(((String) config.get(HdfsSinkConnectorConfig.PARTITIONER_LIST_CONFIG)).split(","));
    for (String partitionerName: partitionerNamesList) {
      try {
        Class<? extends Partitioner> partitionerClass = (Class<? extends Partitioner>) Class.forName(partitionerName);
        partitionerList.add(createPartitioner(config, partitionerClass));
      } catch (ClassNotFoundException e) {
        System.out.println(partitionerName);
        e.printStackTrace();
      }
    }
  }

  @Override
  public String encodePartition(SinkRecord sinkRecord) {
    List<String> encodedPartitions = new ArrayList<>();
    for (Partitioner partitioner: partitionerList){
      encodedPartitions.add(partitioner.encodePartition(sinkRecord));
    }
    return StringUtils.join(encodedPartitions, "/");
  }

  @Override
  public String generatePartitionedPath(String topic, String encodedPartition) {
    return topic + "/" + encodedPartition;
  }

  @Override
  public List<FieldSchema> partitionFields() {
    List<FieldSchema> fields = new ArrayList<>();
    for (Partitioner partitioner: partitionerList){
      fields.addAll(partitioner.partitionFields());
    }
    return fields;
  }

  private Partitioner createPartitioner(Map<String, Object> config, Class<? extends Partitioner> partitionerClasss) {

    Partitioner partitioner = null;
    try {
      partitioner = partitionerClasss.newInstance();
    } catch (InstantiationException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    }
    partitioner.configure(config);
    return partitioner;
  }


}
