package com.grizzlywave.starter.configuration.postprocessor;

import java.lang.reflect.Method;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

import com.grizzlywave.starter.annotations.v2.IOEvent;
import com.grizzlywave.starter.annotations.v2.IOFlow;
import com.grizzlywave.starter.configuration.properties.WaveProperties;
import com.grizzlywave.starter.domain.IOEventBpmnPart;
import com.grizzlywave.starter.listener.Listener;
import com.grizzlywave.starter.listener.ListenerCreator;
import com.grizzlywave.starter.service.IOEventService;

import lombok.extern.slf4j.Slf4j;

/**
 * class configuration for Wave Bpmn Part Creation using Bean Post Processor
 **/
@Slf4j
@Configuration
public class WaveBpmnPostProcessor implements BeanPostProcessor, WavePostProcessors {
	public static Boolean listenerCreatorStatus=true;

	@Autowired
	private WaveProperties waveProperties;

	@Autowired
	private List<IOEventBpmnPart> iobpmnlist;
	@Autowired
	private ListenerCreator listenerCreator;
	@Autowired
	private List<Listener> listeners;

	@Autowired
	private IOEventService ioEventService;

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		try {

			this.process(bean, beanName);
		} catch (Exception e) {
			e.printStackTrace();
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		return bean;
	}

	/**
	 * process method to check for annotations in the bean and create the Bpmn parts
	 **/
	@Override
	public void process(Object bean, String beanName) throws Throwable {
	IOFlow ioFlow=	bean.getClass().getAnnotation(IOFlow.class);
		for (Method method : bean.getClass().getMethods()) {

			IOEvent[] ioEvents = method.getAnnotationsByType(IOEvent.class);

				for (IOEvent ioEvent : ioEvents) {
					
					if (ioEvent.startEvent().key().isEmpty()) {
					
						for (String topicName : ioEventService.getSourceTopic(ioEvent,ioFlow)) {
							if (!listenerExist(topicName, bean, method, ioEvent)) {
								synchronized (method) {
									Thread listenerThread = new Thread() {
									    @Override
									    public void run() {	try {
											listenerCreator.createListener(bean, method, ioEvent,
												waveProperties.getPrefix() + topicName, waveProperties.getGroup_id(),Thread.currentThread());
										} catch (Throwable e) {
											log.error("failed to create Listener !!!");
										}
									    }};
									    listenerThread.start();
									    
									    method.wait();
								}
							}
						}
					}
					String generateID= ioEventService.generateID(ioEvent);
					iobpmnlist.add(createIOEventBpmnPart(ioEvent,ioFlow, bean.getClass().getName(), generateID, method.getName()));
				
			}
		}
	}

	/** check if the listener already exist */
	public boolean listenerExist(String topicName, Object bean, Method method, IOEvent ioEvent)
			 {
		for (Listener listener : listeners) {
			if (listener != null) {
				String t = listener.getTopic();
				if (t.equals(waveProperties.getPrefix() + topicName)) {

					listener.addBeanMethod(new BeanMethodPair(bean, method, ioEvent));
					
					return true;
				}
			}
		}
		return false;
	}

	

	/** methods to create IOEvent BPMN Parts from annotations 
	 * @param ioFlow **/
	public IOEventBpmnPart createIOEventBpmnPart(IOEvent ioEvent, IOFlow ioFlow, String className, String partID, String methodName) {
		String processName = "";
		if ((ioFlow!=null)&&!StringUtils.isBlank(ioFlow.topic())) {
			processName=ioFlow.name();
		}
		if (!StringUtils.isBlank(ioEvent.startEvent().key())) {
			processName = ioEvent.startEvent().key();
		} else if (!StringUtils.isBlank(ioEvent.endEvent().key())) {
			processName = ioEvent.endEvent().key();
		}
		return new IOEventBpmnPart(ioEvent, partID, processName,
				ioEventService.getIOEventType(ioEvent), ioEvent.name(), className, methodName);
		
	}

}
