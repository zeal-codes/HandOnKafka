import java.util.*;

class Message {
    String key;
    String value;
    long offset;

    public Message(String key, String value, long offset) {
        this.key = key;
        this.value = value;
        this.offset = offset;
    }

    @Override
    public String toString() {
        return "[" + offset + "] Key: " + key + ", Value: " + value;
    }
}

class Partition {
    int partitionId;
    List<Message> messages = new ArrayList<>();
    long nextOffset = 0;

    public Partition(int partitionId) {
        this.partitionId = partitionId;
    }

    public void addMessage(String key, String value) {
        messages.add(new Message(key, value, nextOffset++));
    }

    public List<Message> getMessagesFromOffset(long offset) {
        return messages.subList((int) offset, messages.size());
    }
}

class Topic {
    String name;
    List<Partition> partitions;

    public Topic(String name, int partitionCount) {
        this.name = name;
        this.partitions = new ArrayList<>();
        for (int i = 0; i < partitionCount; i++) {
            partitions.add(new Partition(i));
        }
    }

    public void publish(String key, String value) {
        int partitionIndex = (key != null) ? Math.abs(key.hashCode()) % partitions.size()
                : new Random().nextInt(partitions.size());
        partitions.get(partitionIndex).addMessage(key, value);
    }

    public Partition getPartition(int partitionId) {
        return partitions.get(partitionId);
    }

    public int getPartitionCount() {
        return partitions.size();
    }
}

class Consumer {
    String name;
    Map<String, Long> offsets = new HashMap<>(); // topic-partition -> offset

    public Consumer(String name) {
        this.name = name;
    }

    public void consume(Topic topic, int partitionId) {
        String key = topic.name + "-" + partitionId;
        long offset = offsets.getOrDefault(key, 0L);
        List<Message> newMessages = topic.getPartition(partitionId).getMessagesFromOffset(offset);
        for (Message msg : newMessages) {
            System.out.println(name + " consumed from P" + partitionId + ": " + msg);
            offsets.put(key, msg.offset + 1); // advance offset
        }
    }
}

class ConsumerGroup {
    String groupName;
    List<Consumer> consumers = new ArrayList<>();
    Map<Integer, Consumer> partitionAssignment = new HashMap<>();

    public ConsumerGroup(String groupName) {
        this.groupName = groupName;
    }

    public void addConsumer(Consumer consumer) {
        consumers.add(consumer);
    }

    public void assignPartitions(Topic topic) {
        int partitionCount = topic.getPartitionCount();
        for (int i = 0; i < partitionCount; i++) {
            Consumer assigned = consumers.get(i % consumers.size());
            partitionAssignment.put(i, assigned);
        }
    }

    public void consume(Topic topic) {
        for (Map.Entry<Integer, Consumer> entry : partitionAssignment.entrySet()) {
            int partitionId = entry.getKey();
            Consumer consumer = entry.getValue();
            consumer.consume(topic, partitionId);
        }
    }
}

public class KafkaModelDemo {
    public static void main(String[] args) {
        Topic topic = new Topic("order-events", 4);

        // Producers sending messages
        topic.publish("order1", "created");
        topic.publish("order2", "shipped");
        topic.publish("order3", "delivered");
        topic.publish("order4", "cancelled");
        topic.publish("order5", "returned");

        // Create consumer group
        ConsumerGroup group = new ConsumerGroup("group-orders");
        Consumer c1 = new Consumer("Consumer-1");
        Consumer c2 = new Consumer("Consumer-2");

        group.addConsumer(c1);
        group.addConsumer(c2);
        group.assignPartitions(topic); // Assign partitions round-robin

        // Group consumption
        group.consume(topic);
    }
}
