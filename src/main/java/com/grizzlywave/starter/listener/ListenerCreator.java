package com.grizzlywave.starter.listener;

import java.lang.reflect.Method;
import java.util.Properties;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.grizzlywave.starter.handler.RecordsHandler;
/**
 *class service to create listener each listener will be created on a single thread using @Async  
 **/
@Service
public class ListenerCreator {

	private Listener listener;

	public void setbean(Object bean) {
		this.listener.setBean(bean);
	}


	@Async
	public Listener createListener(Object bean, Method method, String topicName) throws Throwable {
		Properties props = new Properties();
		props.setProperty("bootstrap.servers", "192.168.99.100:9092");
		props.setProperty("enable.auto.commit", "true");
		props.setProperty("auto.commit.interval.ms", "1000");
		props.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		props.setProperty("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		props.put("group.id", "consumer-group-1");
		props.put("enable.auto.commit", "true");
		props.put("auto.commit.interval.ms", "1000");
		props.put("auto.offset.reset", "earliest");
		props.put("session.timeout.ms", "30000");
		props.put("topicName", topicName);
		final Consumer<String, String> consumer = new KafkaConsumer<>(props);
		final RecordsHandler recordsHandler = new RecordsHandler();
		final Listener consumerApplication = new Listener(consumer, recordsHandler, bean, method);
		this.listener = consumerApplication;
		consumerApplication.runConsume(props);
		return this.listener;
	}
}
