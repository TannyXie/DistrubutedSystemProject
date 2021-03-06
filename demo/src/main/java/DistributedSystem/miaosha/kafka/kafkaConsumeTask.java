package DistributedSystem.miaosha.kafka;


import DistributedSystem.miaosha.pojo.Stock;
import DistributedSystem.miaosha.service.api.OrderService;
import DistributedSystem.miaosha.util.SpringBeanFactory;
import com.google.gson.Gson;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;

public class kafkaConsumeTask implements Runnable {
    private KafkaConsumer<String, String> consumer;
    private Gson gson;
    private OrderService orderService;
    private final String topic = "mykafka";


    public kafkaConsumeTask(int partitionIdx) {
        this.gson = SpringBeanFactory.getBean(Gson.class);
        this.orderService = SpringBeanFactory.getBean(OrderService.class);
        Properties properties = new Properties();
        properties.put("bootstrap.servers", "172.101.8.2:9092,172.101.8.3:9092,172.101.8.4:9092,172.101.8.5:9092,172.101.8.6:9092");
        properties.put("key.deserializer","org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("value.deserializer","org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("enable.auto.commit", "true");
        this.consumer = new KafkaConsumer<String, String>(properties);
        TopicPartition topicPartition = new TopicPartition(topic, partitionIdx);
        consumer.assign(Arrays.asList(topicPartition));
    }

    @Override
    public void run() {
        while(true) {
            ConsumerRecords<String, String> records = consumer.poll(200);
            for(ConsumerRecord<String, String> record : records) {
                try {
                    System.out.printf("thread = %s, offset = %d, key = %s, partition = %s, " +
                            "value = %s \n", Thread.currentThread().getName(),
                            record.offset(), record.key(), record.partition(), record.value());
                    processMessage(record.value());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void processMessage(String value)throws Exception {
        Optional<?> kafkaMessage = Optional.ofNullable(value);
        // 获取消息
        //序列化 object -> String
        String message = (String) kafkaMessage.get();
        //System.out.println(message);

        //TODO 反序列化
        //Class object = gson.fromJson((String)message, Class.class)
        Stock stock = gson.fromJson((String) message, Stock.class);

        orderService.createOrderAndSendToDB(stock);
    }

}
