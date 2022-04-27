package com.ioevent.starter.configuration.aspect.v2;

import java.text.ParseException;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.StopWatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ioevent.starter.annotations.IOEvent;
import com.ioevent.starter.annotations.IOFlow;
import com.ioevent.starter.annotations.IOResponse;
import com.ioevent.starter.annotations.OutputEvent;
import com.ioevent.starter.configuration.properties.IOEventProperties;
import com.ioevent.starter.domain.IOEventHeaders;
import com.ioevent.starter.domain.IOEventType;
import com.ioevent.starter.handler.IOEventRecordInfo;
import com.ioevent.starter.logger.EventLogger;
import com.ioevent.starter.service.IOEventContextHolder;
import com.ioevent.starter.service.IOEventService;

import lombok.extern.slf4j.Slf4j;

/**
 * Aspect class for advice associated with a @IOEvent calls for Transition task
 * event type
 */
@Slf4j
@Aspect
@Configuration
public class IOEventTransitionAspect {

	@Autowired
	private KafkaTemplate<String, Object> kafkaTemplate;

	private ObjectMapper mapper = new ObjectMapper();

	@Autowired
	private IOEventProperties iOEventProperties;

	@Autowired
	private IOEventService ioEventService;

	/**
	 * Method AfterReturning advice runs after a successful completion of a
	 * Transition task with IOEvent annotation,
	 * 
	 * @param joinPoint    for the join point during the execution of the program,
	 * @param ioEvent      for ioevent annotation which include task information,
	 * @param returnObject for the returned object,
	 */
	@AfterReturning(value = "@annotation(anno)", argNames = "jp, anno,return", returning = "return")
	public void transitionAspect(JoinPoint joinPoint, IOEvent ioEvent, Object returnObject)
			throws ParseException, JsonProcessingException {

		if (ioEventService.isTransition(ioEvent)) {
			IOEventRecordInfo ioeventRecordInfo = IOEventContextHolder.getContext();
			EventLogger eventLogger = new EventLogger();
			eventLogger.startEventLog();
			StopWatch watch = ioeventRecordInfo.getWatch();
			IOFlow ioFlow = joinPoint.getTarget().getClass().getAnnotation(IOFlow.class);
			ioeventRecordInfo.setWorkFlowName(
					ioEventService.getProcessName(ioEvent, ioFlow, ioeventRecordInfo.getWorkFlowName()));
			String outputs = "";
			IOEventType ioEventType = ioEventService.checkTaskType(ioEvent);
			IOResponse<Object> response = ioEventService.getpayload(joinPoint, returnObject);

			if (ioEvent.gatewayOutput().output().length != 0) {

				if (ioEvent.gatewayOutput().parallel()) {
					ioEventType = IOEventType.GATEWAY_PARALLEL;
					outputs = parallelEventSendProcess(ioEvent, ioFlow, response, outputs, ioeventRecordInfo);

				} else if (ioEvent.gatewayOutput().exclusive()) {
					ioEventType = IOEventType.GATEWAY_EXCLUSIVE;
					outputs = exclusiveEventSendProcess(ioEvent, ioFlow, returnObject, outputs, ioeventRecordInfo);

				}
			} else {

				outputs = simpleEventSendProcess(ioEvent, ioFlow, response, outputs, ioeventRecordInfo, ioEventType);
			}

			prepareAndDisplayEventLogger(eventLogger, ioeventRecordInfo, ioEvent, outputs, watch, response.getBody(),
					ioEventType);
		}
	}

	/**
	 * Method that build and send the event of a simple task,
	 * 
	 * @param ioEvent           for ioevent annotation which include task
	 *                          information,
	 * @param ioflow            for ioflow annotation which include general
	 *                          information,
	 * @param returnObject      for the returned object,
	 * @param outputs           for the list of outputs of the event separated by
	 *                          ",",
	 * @param ioeventRecordInfo for the record information from the consumed event,
	 * @param ioEventType       for the event type,
	 * @return string format list of outputs of the event separated by "," ,
	 */
	public String simpleEventSendProcess(IOEvent ioEvent, IOFlow ioFlow, IOResponse<Object> response, String outputs,
			IOEventRecordInfo ioeventRecordInfo, IOEventType ioEventType) throws ParseException {

		for (OutputEvent outputEvent : ioEventService.getOutputs(ioEvent)) {

			Message<Object> message;
			Map<String, Object> headers = ioEventService.prepareHeaders(ioeventRecordInfo.getHeaderList(),
					response.getHeaders());
			if (!StringUtils.isBlank(outputEvent.suffix())) {

				message = this.buildSuffixMessage(ioEvent, ioFlow, response, outputEvent, ioeventRecordInfo,
						ioeventRecordInfo.getStartTime(), ioEventType, headers);
				kafkaTemplate.send(message);

				outputs += ioeventRecordInfo.getOutputConsumedName() + outputEvent.suffix();
			} else {
				message = this.buildTransitionTaskMessage(ioEvent, ioFlow, response, outputEvent, ioeventRecordInfo,
						ioeventRecordInfo.getStartTime(), ioEventType, headers);
				kafkaTemplate.send(message);

				outputs += ioEventService.getOutputKey(outputEvent) + ",";
			}

		}
		return outputs;
	}

	/**
	 * Method that build and send the event of a Exclusive Event task,
	 * 
	 * @param ioEvent           for ioevent annotation which include task
	 *                          information,
	 * @param ioflow            for ioflow annotation which include general
	 *                          information,
	 * @param returnObject      for the returned object,
	 * @param outputs           for the list of outputs of the event separated by
	 *                          ",",
	 * @param ioeventRecordInfo for the record information from the consumed event,
	 * @return string format list of outputs of the event separated by "," ,
	 */
	public String exclusiveEventSendProcess(IOEvent ioEvent, IOFlow ioFlow, Object returnObject, String outputs,
			IOEventRecordInfo ioeventRecordInfo) throws ParseException {

		IOResponse<Object> ioEventResponse = IOResponse.class.cast(returnObject);
		Map<String, Object> headers = ioEventService.prepareHeaders(ioeventRecordInfo.getHeaderList(),
				ioEventResponse.getHeaders());
		for (OutputEvent outputEvent : ioEventService.getOutputs(ioEvent)) {
			if (ioEventResponse.getString().equals(ioEventService.getOutputKey(outputEvent))) {
				Message<Object> message = this.buildTransitionGatewayExclusiveMessage(ioEvent, ioFlow, ioEventResponse,
						outputEvent, ioeventRecordInfo, ioeventRecordInfo.getStartTime(), headers);
				kafkaTemplate.send(message);

				outputs += ioEventService.getOutputKey(outputEvent) + ",";
				log.info("sent to : {}", ioEventService.getOutputKey(outputEvent));
			}

		}
		return outputs;
	}

	/**
	 * Method that build and send the event of a Parallel Event task,
	 * 
	 * @param ioEvent           for ioevent annotation which include task
	 *                          information,
	 * @param ioflow            for ioflow annotation which include general
	 *                          information,
	 * @param returnObject      for the returned object,
	 * @param outputs           for the list of outputs of the event separated by
	 *                          ",",
	 * @param ioeventRecordInfo for the record information from the consumed event,
	 * @return string format list of outputs of the event separated by "," ,
	 */
	public String parallelEventSendProcess(IOEvent ioEvent, IOFlow ioFlow, IOResponse<Object> response, String outputs,
			IOEventRecordInfo ioeventRecordInfo) {
		Map<String, Object> headers = ioEventService.prepareHeaders(ioeventRecordInfo.getHeaderList(),
				response.getHeaders());
		for (OutputEvent outputEvent : ioEventService.getOutputs(ioEvent)) {
			Message<Object> message = this.buildTransitionGatewayParallelMessage(ioEvent, ioFlow, response, outputEvent,
					ioeventRecordInfo, ioeventRecordInfo.getStartTime(), headers);
			kafkaTemplate.send(message);

			outputs += ioEventService.getOutputKey(outputEvent) + ",";
		}
		return outputs;
	}

	/**
	 * Method that display logs after task completed ,
	 * 
	 * @param eventLogger       for the log info dto display,
	 * @param ioeventRecordInfo for the record information from the consumed event,
	 * @param ioEvent           for ioevent annotation which include task
	 *                          information,
	 * @param output            for the output where the event will send ,
	 * @param watch             for capturing time,
	 * @param payload           for the payload of the event,
	 * @param ioEventType       for the event type,
	 */
	public void prepareAndDisplayEventLogger(EventLogger eventLogger, IOEventRecordInfo ioeventRecordInfo,
			IOEvent ioEvent, String output, StopWatch watch, Object payload, IOEventType ioEventType)
			throws JsonProcessingException {
		watch.stop();
		eventLogger.loggerSetting(ioeventRecordInfo.getId(), ioeventRecordInfo.getWorkFlowName(), ioEvent.key(),
				ioeventRecordInfo.getOutputConsumedName(), output, ioEventType.toString(), payload);
		eventLogger.stopEvent(watch.getTotalTimeMillis());
		String jsonObject = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(eventLogger);
		log.info(jsonObject);
	}

	/**
	 * Method that build the event message of simple Transition task to be send in
	 * kafka topic,
	 * 
	 * @param ioEvent         for ioevent annotation which include task information,
	 * @param ioflow          for ioflow annotation which include general
	 *                        information,
	 * @param payload         for the payload of the event,
	 * @param processName     for the process name
	 * @param uuid            for the correlation_id,
	 * @param outputEventName for the output Event where the event will send ,
	 * @param outputTopic     for the name of the output topic ,
	 * @param startTime       for the start time of the event,
	 * @return message type of Message,
	 */
	public Message<Object> buildTransitionTaskMessage(IOEvent ioEvent, IOFlow ioFlow, IOResponse<Object> response,
			OutputEvent outputEvent, IOEventRecordInfo ioeventRecordInfo, Long startTime, IOEventType ioEventType,
			Map<String, Object> headers) {
		String topicName = ioEventService.getOutputTopicName(ioEvent, ioFlow, outputEvent.topic());
		String apiKey = ioEventService.getApiKey(iOEventProperties, ioFlow);

		return MessageBuilder.withPayload(response.getBody()).copyHeaders(headers)
				.setHeader(KafkaHeaders.TOPIC, iOEventProperties.getPrefix() + topicName)
				.setHeader(KafkaHeaders.MESSAGE_KEY, ioeventRecordInfo.getId())
				.setHeader(IOEventHeaders.PROCESS_NAME.toString(), ioeventRecordInfo.getWorkFlowName())
				.setHeader(IOEventHeaders.CORRELATION_ID.toString(), ioeventRecordInfo.getId())
				.setHeader(IOEventHeaders.EVENT_TYPE.toString(), ioEventType.toString())
				.setHeader(IOEventHeaders.INPUT.toString(), ioEventService.getInputNames(ioEvent))
				.setHeader(IOEventHeaders.OUTPUT_EVENT.toString(), ioEventService.getOutputKey(outputEvent))
				.setHeader(IOEventHeaders.STEP_NAME.toString(), ioEvent.key())
				.setHeader(IOEventHeaders.API_KEY.toString(), apiKey)
				.setHeader(IOEventHeaders.START_TIME.toString(), startTime)
				.setHeader(IOEventHeaders.START_INSTANCE_TIME.toString(), ioeventRecordInfo.getInstanceStartTime())
				.build();
	}

	/**
	 * Method that build the event message of Parallel task to be send in kafka
	 * topic,
	 * 
	 * @param ioEvent         for ioevent annotation which include task information,
	 * @param ioflow          for ioflow annotation which include general
	 *                        information,
	 * @param payload         for the payload of the event,
	 * @param processName     for the process name
	 * @param uuid            for the correlation_id,
	 * @param outputEventName for the output Event where the event will send ,
	 * @param outputTopic     for the name of the output topic ,
	 * @param startTime       for the start time of the event,
	 * @return message type of Message,
	 */
	public Message<Object> buildTransitionGatewayParallelMessage(IOEvent ioEvent, IOFlow ioFlow,
			IOResponse<Object> response, OutputEvent outputEvent, IOEventRecordInfo ioeventRecordInfo, Long startTime,
			Map<String, Object> headers) {
		String topicName = ioEventService.getOutputTopicName(ioEvent, ioFlow, outputEvent.topic());
		String apiKey = ioEventService.getApiKey(iOEventProperties, ioFlow);

		return MessageBuilder.withPayload(response.getBody()).copyHeaders(headers)
				.setHeader(KafkaHeaders.TOPIC, iOEventProperties.getPrefix() + topicName)
				.setHeader(KafkaHeaders.MESSAGE_KEY, ioeventRecordInfo.getId())
				.setHeader(IOEventHeaders.PROCESS_NAME.toString(), ioeventRecordInfo.getWorkFlowName())
				.setHeader(IOEventHeaders.CORRELATION_ID.toString(), ioeventRecordInfo.getId())
				.setHeader(IOEventHeaders.EVENT_TYPE.toString(), IOEventType.GATEWAY_PARALLEL.toString())
				.setHeader(IOEventHeaders.INPUT.toString(), ioEventService.getInputNames(ioEvent))
				.setHeader(IOEventHeaders.OUTPUT_EVENT.toString(), ioEventService.getOutputKey(outputEvent))
				.setHeader(IOEventHeaders.STEP_NAME.toString(), ioEvent.key())
				.setHeader(IOEventHeaders.API_KEY.toString(), apiKey)
				.setHeader(IOEventHeaders.START_TIME.toString(), startTime)
				.setHeader(IOEventHeaders.START_INSTANCE_TIME.toString(), ioeventRecordInfo.getInstanceStartTime())
				.build();
	}

	/**
	 * Method that build the event message of Exclusive task to be send in kafka
	 * topic,
	 * 
	 * @param ioEvent         for ioevent annotation which include task information,
	 * @param ioflow          for ioflow annotation which include general
	 *                        information,
	 * @param payload         for the payload of the event,
	 * @param processName     for the process name
	 * @param uuid            for the correlation_id,
	 * @param outputEventName for the output Event where the event will send ,
	 * @param outputTopic     for the name of the output topic ,
	 * @param startTime       for the start time of the event,
	 * @return message type of Message,
	 */
	public Message<Object> buildTransitionGatewayExclusiveMessage(IOEvent ioEvent, IOFlow ioFlow,
			IOResponse<Object> response, OutputEvent outputEvent, IOEventRecordInfo ioeventRecordInfo, Long startTime,
			Map<String, Object> headers) {
		String topicName = ioEventService.getOutputTopicName(ioEvent, ioFlow, outputEvent.topic());
		String apiKey = ioEventService.getApiKey(iOEventProperties, ioFlow);

		return MessageBuilder.withPayload(response.getBody()).copyHeaders(headers)
				.setHeader(KafkaHeaders.TOPIC, iOEventProperties.getPrefix() + topicName)
				.setHeader(KafkaHeaders.MESSAGE_KEY, ioeventRecordInfo.getId())
				.setHeader(IOEventHeaders.PROCESS_NAME.toString(), ioeventRecordInfo.getWorkFlowName())
				.setHeader(IOEventHeaders.CORRELATION_ID.toString(), ioeventRecordInfo.getId())
				.setHeader(IOEventHeaders.EVENT_TYPE.toString(), IOEventType.GATEWAY_EXCLUSIVE.toString())
				.setHeader(IOEventHeaders.INPUT.toString(), ioEventService.getInputNames(ioEvent))
				.setHeader(IOEventHeaders.OUTPUT_EVENT.toString(), ioEventService.getOutputKey(outputEvent))
				.setHeader(IOEventHeaders.STEP_NAME.toString(), ioEvent.key())
				.setHeader(IOEventHeaders.API_KEY.toString(), apiKey)
				.setHeader(IOEventHeaders.START_TIME.toString(), startTime)
				.setHeader(IOEventHeaders.START_INSTANCE_TIME.toString(), ioeventRecordInfo.getInstanceStartTime())
				.build();
	}

	/**
	 * Method that build the event message of add suffix task to be send in kafka
	 * topic,
	 * 
	 * @param ioEvent         for ioevent annotation which include task information,
	 * @param ioflow          for ioflow annotation which include general
	 *                        information,
	 * @param payload         for the payload of the event,
	 * @param processName     for the process name
	 * @param uuid            for the correlation_id,
	 * @param outputEventName for the output Event where the event will send ,
	 * @param outputTopic     for the name of the output topic ,
	 * @param startTime       for the start time of the event,
	 * @return message type of Message,
	 */
	public Message<Object> buildSuffixMessage(IOEvent ioEvent, IOFlow ioFlow, IOResponse<Object> response,
			OutputEvent outputEvent, IOEventRecordInfo ioeventRecordInfo, Long startTime, IOEventType ioEventType,
			Map<String, Object> headers) {
		String inputtopic = ioEventService.getInputEventByName(ioEvent, ioeventRecordInfo.getOutputConsumedName()).topic();
		String topicName = ioEventService.getOutputTopicName(ioEvent, ioFlow, inputtopic);
		String apiKey = ioEventService.getApiKey(iOEventProperties, ioFlow);

		return MessageBuilder.withPayload(response.getBody()).copyHeaders(headers)
				.setHeader(KafkaHeaders.TOPIC, iOEventProperties.getPrefix() + topicName)
				.setHeader(KafkaHeaders.MESSAGE_KEY, ioeventRecordInfo.getId())
				.setHeader(IOEventHeaders.PROCESS_NAME.toString(), ioeventRecordInfo.getWorkFlowName())
				.setHeader(IOEventHeaders.CORRELATION_ID.toString(), ioeventRecordInfo.getId())
				.setHeader(IOEventHeaders.EVENT_TYPE.toString(), ioEventType.toString())
				.setHeader(IOEventHeaders.INPUT.toString(), ioeventRecordInfo.getOutputConsumedName())
				.setHeader(IOEventHeaders.OUTPUT_EVENT.toString(),
						ioeventRecordInfo.getOutputConsumedName() + outputEvent.suffix())
				.setHeader(IOEventHeaders.STEP_NAME.toString(), ioEvent.key())
				.setHeader(IOEventHeaders.API_KEY.toString(), apiKey)
				.setHeader(IOEventHeaders.START_TIME.toString(), startTime)
				.setHeader(IOEventHeaders.START_INSTANCE_TIME.toString(), ioeventRecordInfo.getInstanceStartTime())
				.build();
	}
}