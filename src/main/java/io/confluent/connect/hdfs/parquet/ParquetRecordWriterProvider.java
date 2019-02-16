/**
 * Copyright 2015 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 **/
package io.confluent.connect.hdfs.parquet;

import io.confluent.connect.hdfs.partitioner.Partitioner;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;

import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;


import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.confluent.connect.avro.AvroData;
import io.confluent.connect.hdfs.RecordWriterProvider;
import io.confluent.connect.hdfs.RecordWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParquetRecordWriterProvider implements RecordWriterProvider {

  private final static String EXTENSION = ".parquet";
  private static final Logger log = LoggerFactory.getLogger(ParquetRecordWriterProvider.class);

  @Override
  public String getExtension() {
    return EXTENSION;
  }

  @Override
  public RecordWriter<SinkRecord> getRecordWriter(
      Configuration conf, final String fileName, SinkRecord record, final AvroData avroData, Partitioner partitioner)
      throws IOException {
    final Schema avroSchema = avroData.fromConnectSchema(record.valueSchema());
    final Schema filteredSchema = removePartitionFields(avroSchema, partitioner);
    CompressionCodecName compressionCodecName = CompressionCodecName.SNAPPY;

    int blockSize = 256 * 1024 * 1024;
    int pageSize = 64 * 1024;

    Path path = new Path(fileName);
    final ParquetWriter<GenericRecord> writer =
        new AvroParquetWriter<>(path, filteredSchema, compressionCodecName, blockSize, pageSize, true, conf);

    return new RecordWriter<SinkRecord>() {

      private final Schema partitionedAvroSchema = filteredSchema;

      @Override
      public void write(SinkRecord record) throws IOException {
        GenericRecord value = (GenericRecord)avroData.fromConnectData(record.valueSchema(), record.value());
        GenericRecord filteredRecord = filterRecord(value);
        writer.write(filteredRecord);
      }

      @Override
      public void close() throws IOException {
        writer.close();
      }

      private GenericRecord filterRecord(GenericRecord value) {
        GenericRecordBuilder builder = new GenericRecordBuilder(partitionedAvroSchema);
        for (Schema.Field f : filteredSchema.getFields()) {
          builder.set(f,value.get(f.name()));
        }
        return builder.build();
      }
    };
  }

  private Schema removePartitionFields(Schema avroSchema, Partitioner partitioner) {
    List<FieldSchema> partitionFields = partitioner.partitionFields();
    Set<String> partitionFieldNames = partitionFields.stream().
            map((FieldSchema f) -> f.getName().toLowerCase()).
            collect(Collectors.toSet());
    SchemaBuilder.FieldAssembler<Schema> fieldAssembler = SchemaBuilder
            .record(avroSchema.getName())
            .doc(avroSchema.getDoc())
            .namespace(avroSchema.getNamespace())
            .fields();

    for (Schema.Field f : avroSchema.getFields()) {
      if (!partitionFieldNames.contains(f.name().toLowerCase())) {
        fieldAssembler = fieldAssembler.name(f.name()).doc(f.doc()).type(f.schema()).noDefault();
      }
    }
    Schema mappedSchema = fieldAssembler.endRecord();
    return mappedSchema;
  }



}
