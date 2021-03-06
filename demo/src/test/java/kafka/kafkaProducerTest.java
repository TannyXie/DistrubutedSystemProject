package kafka;

import DistributedSystem.miaosha.kafka.kafkaProducer;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.record.InvalidRecordException;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.utils.Utils;
import org.apache.tomcat.jni.Time;
import org.junit.Test;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

class Customer {

    private int customerID;
    private String customerName;

    public Customer(int ID, String name) {
        this.customerID = ID;
        this.customerName = name;
    }

    public String getCustomerName() {
        return customerName;
    }

    public int getCustomerID() {
        return customerID;
    }
}

//自定义序列化器
class CustomerSerializer implements Serializer<Customer> {
    @Override
    public void configure(Map configs, boolean isKey) {

    }

    @Override
    public byte[] serialize(String topic, Customer data) {
        try {
            byte[] serializedName;
            int stringSize;
            if (data == null) {
                return null;
            } else {
                if (data.getCustomerName() != null) {
                    serializedName = data.getCustomerName().getBytes();
                    stringSize = serializedName.length;
                } else {
                    serializedName = new byte[0];
                    stringSize = 0;
                }
            }
            //customerID 4 byte
            //stringSize 4 byte
            ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + stringSize);
            buffer.putInt(data.getCustomerID());
            buffer.putInt(stringSize);
            buffer.put(serializedName);
            return buffer.array();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void close() {

    }
}


class MyPartitioner implements Partitioner {

    private String Name = "alex";

    @Override
    public void configure(Map<String, ?> configs) {
    }

    @Override
    public int partition(String topic, Object key, byte[] keyBytes,
                         Object value, byte[] valueBytes, Cluster cluster) {
        List<PartitionInfo> partitionInfoList = cluster.partitionsForTopic(topic);
        int numPartitions = partitionInfoList.size();

        // if invalid key
        if ((keyBytes == null) || (!(key instanceof String))) {
            throw new InvalidRecordException("We expect all messages to have customer" +
                    "name as key");
        }

        if (((String) key).equals(topic)) {
            return numPartitions;
        }
        //其他记录散列到其他分区
        return (Math.abs(Utils.murmur2(keyBytes)) % (numPartitions - 1));
    }


    @Override
    public void close() {

    }
}

public class kafkaProducerTest {
    private static Properties kafkaProps = new Properties();

    static {
        kafkaProps.put("bootstrap.servers", "172.101.8.2:9092,172.101.8.3:9092,172.101.8.4:9092,172.101.8.5:9092,172.101.8.6:9092");
        kafkaProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    }

    private static KafkaProducer producer = new KafkaProducer<String, String>(kafkaProps);

    @Test
    public void testKafkaProducer() throws Exception {
        HashMap<String, String> mp = new HashMap<>();
        ExecutorService executorService = new ThreadPoolExecutor(5, 5, 0,
                TimeUnit.SECONDS, new ArrayBlockingQueue<>(512), new ThreadPoolExecutor.DiscardPolicy());
        while (true) {
            int i = 0;
            kafkaProducer.sendMessage(Collections.singletonMap("mykafka", "message-" + i));
            i += 1;
        }
    }

    @Test
    public void createProducerSyncSendTest() {
        //send message
        ProducerRecord<String, String> record = new ProducerRecord<>("mykafka", "Country", "China");
        try {
            Object response = producer.send(record).get();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void ProducerAsyncSendTest() {
        while (true) {
            ProducerRecord<String, String> record = new ProducerRecord<>("mykafka", "Country", "China");
            try {
                //要调用Future对象的get方法，使得发送客户端等待服务器的响应
                Object response = producer.send(record, new Callback() {
                    @Override
                    public void onCompletion(RecordMetadata recordMetadata, Exception e) {
                        System.out.println(recordMetadata);
                    }
                }).get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    private class produceTask implements Runnable {
        private int index;

        public produceTask(int i) {
            this.index = i;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    ProducerRecord<String, String> record = new ProducerRecord<>("mykafka", this.index, "country", "china");
                    try {
                        Object response = producer.send(record, new Callback() {
                            @Override
                            public void onCompletion(RecordMetadata recordMetadata, Exception e) {
                                System.out.println(recordMetadata.partition());
                            }
                        }).get();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void MultiThreadProducerTest() {
        Random r = new Random(1);
        ExecutorService pool = new ThreadPoolExecutor(5, 5, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(1024),new ThreadPoolExecutor.DiscardPolicy());
        while (true) {
            int i = r.nextInt(5);
            produceTask task = new produceTask(i);
            pool.submit(task);
        }
    }

}