package com.grizzlywave.starter.listener;

import java.lang.reflect.Method;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.grizzlywave.starter.configuration.context.AppContext;
import com.grizzlywave.starter.domain.IOEventHeaders;
import com.grizzlywave.starter.domain.WaveParallelEventInformation;
import com.grizzlywave.starter.handler.WaveRecordInfo;
import com.grizzlywave.starter.service.IOEventService;
import com.grizzlywave.starter.service.WaveContextHolder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class WaveParrallelListener {

	ObjectMapper mapper = new ObjectMapper();

	@Autowired
	private List<Listener> listeners;

	@Autowired
	private AppContext ctx;

	@Autowired
	private IOEventService ioEventService;
	

	@KafkaListener(topics = "resultTopic", containerFactory = "userKafkaListenerFactory", groupId = "${ioevent.group_id}")
	public void consumeParallelEvent(String s) throws JsonMappingException, JsonProcessingException, ClassNotFoundException,
			NoSuchMethodException, SecurityException {
		Gson gson = new Gson();
		WaveParallelEventInformation waveParallelEventInformation = gson.fromJson(s,
				WaveParallelEventInformation.class);
		if ((waveParallelEventInformation!=null) && (sameList(waveParallelEventInformation.getSourceRequired(),
				waveParallelEventInformation.getTargetsArrived()))) {
			
				try {
					Object beanmObject = ctx.getApplicationContext()
							.getBean(Class.forName(waveParallelEventInformation.getClassName()));
					if (beanmObject != null) {
					StopWatch watch=new StopWatch();
					watch.start(waveParallelEventInformation.getHeaders().get(IOEventHeaders.CORRELATION_ID.toString()));
					WaveRecordInfo waveRecordInfo = new WaveRecordInfo(waveParallelEventInformation.getHeaders().get(IOEventHeaders.CORRELATION_ID.toString()),
							waveParallelEventInformation.getHeaders().get(IOEventHeaders.PROCESS_NAME.toString()),
							waveParallelEventInformation.getTargetsArrived().toString(),watch);
					WaveContextHolder.setContext(waveRecordInfo);

					InvokeWithOneParameter(waveParallelEventInformation.getMethod(), beanmObject,
							waveParallelEventInformation.getValue());

					}} catch (Throwable e) {
					//e.printStackTrace();
				}

			}
	 else {
			log.info("Parallel Event Source Not Completed, target arrived : "+waveParallelEventInformation.getTargetsArrived() );
		}

	}

	/** method to invoke the method from a specific bean **/
	public void InvokeWithOneParameter(String method, Object beanmObject, Object args) throws Throwable {
		if (beanmObject != null) {
			for (Method met : beanmObject.getClass().getDeclaredMethods()) {
				if (met.getName().equals(method)) {
					Class<?>[] params = met.getParameterTypes();
					met.invoke(beanmObject, parseConsumedValue(args, params[0]));

				}
			}

		}
	}

	public void InvokeWithtwoParameter(Method method, Object bean, Object arg1, Object arg2) throws Throwable {
		Object beanmObject = ctx.getApplicationContext().getBean(bean.getClass());
		if (beanmObject != null) {
			for (Method met : beanmObject.getClass().getDeclaredMethods()) {
				if (met.getName().equals(method.getName())) {
					Class<?>[] params = method.getParameterTypes();
					met.invoke(ctx.getApplicationContext().getBean(bean.getClass()),
							parseConsumedValue(arg1, params[0]), arg2);

				}

			}
		}
	}

	public Object parseConsumedValue(Object consumedValue, Class<?> type)
			throws JsonMappingException, JsonProcessingException {
		if (type.equals(String.class)) {
			return consumedValue;
		} else {
			return mapper.readValue(consumedValue.toString(), type);
		}
	}

	public boolean sameList(List<String> firstList, List<String> secondList) {
		return (firstList.size() == secondList.size() && firstList.containsAll(secondList)
				&& secondList.containsAll(firstList));
	}
}
