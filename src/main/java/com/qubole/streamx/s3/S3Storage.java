package com.qubole.streamx.s3;

import com.qubole.streamx.s3.wal.DBWAL;
import io.confluent.connect.hdfs.storage.Storage;
import io.confluent.connect.hdfs.wal.WAL;
import com.qubole.streamx.s3.wal.DBWAL;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.velocity.exception.MethodInvocationException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;


import java.io.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;

public class S3Storage implements Storage {

    private final FileSystem fs;
    private final Configuration conf;
    private final String url;
    private final Class<? extends  WAL> walClass;

    public S3Storage(Configuration conf, Class walClass, String url) throws IOException {
        fs = FileSystem.newInstance(URI.create(url), conf);
        this.conf = conf;
        this.url = url;
        this.walClass = walClass;
    }

    @Override
    public FileStatus[] listStatus(String path, PathFilter filter) throws IOException {
        return fs.listStatus(new Path(path), filter);
    }

    @Override
    public FileStatus[] listStatus(String path) throws IOException {
        return fs.listStatus(new Path(path));
    }

    @Override
    public void append(String filename, Object object) throws IOException {

    }

    @Override
    public boolean mkdirs(String filename) throws IOException {
        return fs.mkdirs(new Path(filename));
    }

    @Override
    public boolean exists(String filename) throws IOException {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(new Path(filename))));
        } catch(IOException e){
            return false;
        }
        return true;
    }

    @Override
    public void commit(String tempFile, String committedFile) throws IOException {
        renameFile(tempFile, committedFile);
    }


    @Override
    public void delete(String filename) throws IOException {
        fs.delete(new Path(filename), true);
    }

    @Override
    public void close() throws IOException {
        if (fs != null) {
            fs.close();
        }
    }

    @Override
    public WAL wal(String topicsDir, TopicPartition topicPart) {
        try {
            Constructor<? extends WAL> ctor = walClass.getConstructor(String.class, TopicPartition.class, Storage.class);
            return ctor.newInstance(topicsDir, topicPart, this);
        } catch (NoSuchMethodException | InvocationTargetException | MethodInvocationException | InstantiationException | IllegalAccessException e) {
            throw new ConnectException(e);
        }
    }

    @Override
    public Configuration conf() {
        return conf;
    }

    @Override
    public String url() {
        return url;
    }

    private void renameFile(String sourcePath, String targetPath) throws IOException {
        if (sourcePath.equals(targetPath)) {
            return;
        }
        final Path srcPath = new Path(sourcePath);
        final Path dstPath = new Path(targetPath);
        FileSystem localFs = FileSystem.get(srcPath.toUri(),conf);

        if (localFs.exists(srcPath)) {
            fs.copyFromLocalFile(false, srcPath, dstPath);
            //fs.rename(srcPath, dstPath);
        }
    }
}
