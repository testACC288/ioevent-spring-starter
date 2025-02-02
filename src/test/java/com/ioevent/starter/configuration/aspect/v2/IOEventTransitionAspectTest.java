/*
 * Copyright © 2021 CodeOnce Software (https://www.codeonce.fr/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */




package com.ioevent.starter.configuration.aspect.v2;






import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.aspectj.lang.JoinPoint;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.StopWatch;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SettableListenableFuture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ioevent.starter.annotations.EndEvent;
import com.ioevent.starter.annotations.GatewayOutputEvent;
import com.ioevent.starter.annotations.IOEvent;
import com.ioevent.starter.annotations.IOResponse;
import com.ioevent.starter.annotations.InputEvent;
import com.ioevent.starter.annotations.OutputEvent;
import com.ioevent.starter.annotations.StartEvent;
import com.ioevent.starter.configuration.properties.IOEventProperties;
import com.ioevent.starter.domain.IOEventHeaders;
import com.ioevent.starter.domain.IOEventType;
import com.ioevent.starter.handler.IOEventRecordInfo;
import com.ioevent.starter.logger.EventLogger;
import com.ioevent.starter.service.IOEventContextHolder;
import com.ioevent.starter.service.IOEventMessageBuilderService;
import com.ioevent.starter.service.IOEventService;

class IOEventTransitionAspectTest {

	@InjectMocks
	IOEventTransitionAspect transitionAspect = new IOEventTransitionAspect();
	@InjectMocks
	IOEventMessageBuilderService messageBuilderService  = new IOEventMessageBuilderService();
	@Mock
	IOEventService ioEventService;
	@Mock
	IOEventProperties iOEventProperties;
	@Mock
	JoinPoint joinPoint;
	@Mock
	KafkaTemplate<String, Object> kafkaTemplate;
	@Mock
	IOEventRecordInfo ioeventRecordInfo;

	@BeforeEach
	public void init() {

		MockitoAnnotations.initMocks(this);
	}

	/** method to test annotations **/
	@IOEvent(key = "terminate the event", topic = "Topic", startEvent = @StartEvent(key = "process name")//
			, output = @OutputEvent(key = "output"))
	public boolean startAnnotationMethod() {
		return true;
	}

	/** method to test annotations **/
	@IOEvent(key = "stepname", input = @InputEvent(key = "previous Task"), //
			endEvent = @EndEvent(key = "process name"))
	public boolean endAnnotationMethod() {
		return true;
	}

	/** method to test annotations **/
	@IOEvent(key = "test annotation", topic = "GeneralTopic", //
			input = @InputEvent(key = "input", topic = "topic"), output = @OutputEvent(key = "output", topic = "topic"))
	public boolean simpleTaskAnnotationMethod() {
		return true;
	}

	@IOEvent(key = "parallel gatway task", topic = "topic", //
			input = @InputEvent(key = "inputEvent"), //
			gatewayOutput = @GatewayOutputEvent(parallel = true, output = { //
					@OutputEvent(key = "Output1"), //
					@OutputEvent(key = "Output2")//
			}))
	public boolean parralelTaskAnnotationMethod() {
		return true;
	}

	@IOEvent(key  = "exclusive gatway task", topic = "topic", //
			input = @InputEvent(key = "inputEvent"), //
			gatewayOutput = @GatewayOutputEvent(exclusive = true, output = { //
					@OutputEvent(key = "Output2"), //
					@OutputEvent(key = "Output1")//
			}))
	public boolean exclusiveTaskAnnotationMethod() {
		return true;
	}

	@IOEvent(key = "add suffix task", topic = "topic", //
			input = { @InputEvent(key = "previous output"), //
			}, //
			output = @OutputEvent(suffix = "_suffixAdded"))
	public boolean suffixTaskAnnotation() {
		return true;
	}

	@Test
	void testTryAnnotationmethod() {
		IOEventTransitionAspectTest serviceTest = Mockito.spy(this);
		Assert.assertEquals(true, startAnnotationMethod());
		Assert.assertEquals(true, endAnnotationMethod());
		Assert.assertEquals(true, simpleTaskAnnotationMethod());
		Assert.assertEquals(true, parralelTaskAnnotationMethod());
		Assert.assertEquals(true, exclusiveTaskAnnotationMethod());
		Assert.assertEquals(true, suffixTaskAnnotation());

	}

	@Test
	void isTransitionTest() throws NoSuchMethodException, SecurityException {
		Method method = this.getClass().getMethod("simpleTaskAnnotationMethod", null);
		IOEvent ioEvent = method.getAnnotation(IOEvent.class);
		Method startMethod = this.getClass().getMethod("startAnnotationMethod", null);
		IOEvent startIOEvent = startMethod.getAnnotation(IOEvent.class);
		Method endMethod = this.getClass().getMethod("endAnnotationMethod", null);
		IOEvent endIOEvent = endMethod.getAnnotation(IOEvent.class);
		// Assert.assertTrue(transitionAspect.isTransition(ioEvent));
		// Assert.assertFalse(transitionAspect.isTransition(startIOEvent));
		// Assert.assertFalse(transitionAspect.isTransition(endIOEvent));

	}

	@Test
	void buildTransitionTaskMessageTest() throws NoSuchMethodException, SecurityException {
		when(iOEventProperties.getPrefix()).thenReturn("test-");
		Map<String, Object> headersMap=new HashMap<>();
		IOResponse<Object> ioEventResponse = new IOResponse<>(null, "payload", null);
		Method method = this.getClass().getMethod("simpleTaskAnnotationMethod", null);
		IOEvent ioEvent = method.getAnnotation(IOEvent.class);
		IOEventRecordInfo ioeventRecordInfo = new IOEventRecordInfo("1155", "process name", "recordOutput", new StopWatch(),1000L,null);
		Message messageResult = transitionAspect.buildTransitionTaskMessage(ioEvent, null, ioEventResponse,
				ioEvent.output()[0], ioeventRecordInfo, (long) 123546, IOEventType.TASK,headersMap);
		Message<String> message = MessageBuilder.withPayload("payload").setHeader(KafkaHeaders.TOPIC, "test-topic")
				.setHeader(KafkaHeaders.MESSAGE_KEY, "1155").setHeader(IOEventHeaders.CORRELATION_ID.toString(), "1155")
				.setHeader(IOEventHeaders.STEP_NAME.toString(), "test annotation")
				.setHeader(IOEventHeaders.EVENT_TYPE.toString(), IOEventType.TASK.toString())
				.setHeader(IOEventHeaders.INPUT.toString(), new ArrayList<String>(Arrays.asList("previous Task")))
				.setHeader(IOEventHeaders.OUTPUT_EVENT.toString(), "output")
				.setHeader(IOEventHeaders.PROCESS_NAME.toString(), "process name")
				.setHeader(IOEventHeaders.START_TIME.toString(), (long) 123546).build();

		assertEquals(message.getHeaders().get(IOEventHeaders.STEP_NAME.toString()),
				messageResult.getHeaders().get(IOEventHeaders.STEP_NAME.toString()));
		assertEquals(message.getHeaders().get("kafka_messageKey"), messageResult.getHeaders().get("kafka_messageKey"));

		/*
		 * Method method2 = this.getClass().getMethod("endAnnotationMethod2", null);
		 * IOEvent ioEvent2 = method2.getAnnotation(IOEvent.class); Message
		 * messageResult2 = endAspect.buildEventMessage(ioEvent2, "payload", "END",
		 * ioeventRecordInfo, (long) 123546); assertEquals("test-",
		 * messageResult2.getHeaders().get("kafka_topic"));
		 */
	}

	@Test
	void buildSuffixMessageTest() throws NoSuchMethodException, SecurityException {
		when(iOEventProperties.getPrefix()).thenReturn("test-");
		Method method = this.getClass().getMethod("suffixTaskAnnotation", null);
		Map<String, Object> headersMap=new HashMap<>();
		IOResponse<Object> ioEventResponse = new IOResponse<>(null, "payload", null);
		IOEvent ioEvent = method.getAnnotation(IOEvent.class);
		when(ioEventService.getInputEventByName(Mockito.any(), Mockito.any())).thenReturn(ioEvent.input()[0]);
		IOEventRecordInfo ioeventRecordInfo = new IOEventRecordInfo("1155", "process name", "previous targe", new StopWatch(),1000L,null);
		Message messageResult = transitionAspect.buildSuffixMessage(ioEvent, null, ioEventResponse, ioEvent.output()[0],
				ioeventRecordInfo, (long) 123546, IOEventType.TASK,headersMap);
		Message<String> message = MessageBuilder.withPayload("payload").setHeader(KafkaHeaders.TOPIC, "test-topic")
				.setHeader(KafkaHeaders.MESSAGE_KEY, "1155").setHeader(IOEventHeaders.CORRELATION_ID.toString(), "1155")
				.setHeader(IOEventHeaders.STEP_NAME.toString(), "test annotation")
				.setHeader(IOEventHeaders.EVENT_TYPE.toString(), IOEventType.TASK.toString())
				.setHeader(IOEventHeaders.INPUT.toString(), new ArrayList<String>(Arrays.asList("previous Task")))
				.setHeader(IOEventHeaders.OUTPUT_EVENT.toString(), ioeventRecordInfo.getOutputConsumedName() + "_suffixAdded")
				.setHeader(IOEventHeaders.PROCESS_NAME.toString(), "process name")
				.setHeader(IOEventHeaders.START_TIME.toString(), (long) 123546).build();

		assertEquals(message.getHeaders().get(IOEventHeaders.OUTPUT_EVENT.toString()),
				messageResult.getHeaders().get(IOEventHeaders.OUTPUT_EVENT.toString()));
		assertEquals(message.getHeaders().get("kafka_messageKey"), messageResult.getHeaders().get("kafka_messageKey"));

		/*
		 * Method method2 = this.getClass().getMethod("endAnnotationMethod2", null);
		 * IOEvent ioEvent2 = method2.getAnnotation(IOEvent.class); Message
		 * messageResult2 = endAspect.buildEventMessage(ioEvent2, "payload", "END",
		 * ioeventRecordInfo, (long) 123546); assertEquals("test-",
		 * messageResult2.getHeaders().get("kafka_topic"));
		 */
	}

	@Test
	void buildTransitionGatewayParallelMessageTest() throws NoSuchMethodException, SecurityException {
		when(iOEventProperties.getPrefix()).thenReturn("test-");
		when(ioEventService.getOutputKey(Mockito.any())).thenReturn("Output1");
		Method method = this.getClass().getMethod("parralelTaskAnnotationMethod", null);
		Map<String, Object> headersMap=new HashMap<>();
		IOResponse<Object> ioEventResponse = new IOResponse<>(null, "payload", null);
		IOEvent ioEvent = method.getAnnotation(IOEvent.class);
		IOEventRecordInfo ioeventRecordInfo = new IOEventRecordInfo("1155", "process name", "recordOutput", new StopWatch(),1000L,null);
		Message messageResult = messageBuilderService.buildTransitionGatewayParallelMessage(ioEvent, null, ioEventResponse,
				ioEvent.gatewayOutput().output()[0], ioeventRecordInfo, (long) 123546,headersMap,false);
		Message<String> message = MessageBuilder.withPayload("payload").setHeader(KafkaHeaders.TOPIC, "test-topic")
				.setHeader(KafkaHeaders.MESSAGE_KEY, "1155").setHeader(IOEventHeaders.CORRELATION_ID.toString(), "1155")
				.setHeader(IOEventHeaders.STEP_NAME.toString(), "test annotation")
				.setHeader(IOEventHeaders.EVENT_TYPE.toString(), IOEventType.GATEWAY_PARALLEL.toString())
				.setHeader(IOEventHeaders.INPUT.toString(), new ArrayList<String>(Arrays.asList("previous Task")))
				.setHeader(IOEventHeaders.OUTPUT_EVENT.toString(), "Output1")
				.setHeader(IOEventHeaders.PROCESS_NAME.toString(), "process name")
				.setHeader(IOEventHeaders.START_TIME.toString(), (long) 123546).build();

		assertEquals(message.getHeaders().get(IOEventHeaders.OUTPUT_EVENT.toString()),
				messageResult.getHeaders().get(IOEventHeaders.OUTPUT_EVENT.toString()));
		assertEquals(message.getHeaders().get("kafka_messageKey"), messageResult.getHeaders().get("kafka_messageKey"));

		/*
		 * Method method2 = this.getClass().getMethod("endAnnotationMethod2", null);
		 * IOEvent ioEvent2 = method2.getAnnotation(IOEvent.class); Message
		 * messageResult2 = endAspect.buildEventMessage(ioEvent2, "payload", "END",
		 * ioeventRecordInfo, (long) 123546); assertEquals("test-",
		 * messageResult2.getHeaders().get("kafka_topic"));
		 */
	}

	@Test
	void buildTransitionGatewayExclusiveMessage() throws NoSuchMethodException, SecurityException {
		when(iOEventProperties.getPrefix()).thenReturn("test-");
		Map<String, Object> headersMap=new HashMap<>();
		IOResponse<Object> ioEventResponse = new IOResponse<>(null, "payload", null);
		Method method = this.getClass().getMethod("exclusiveTaskAnnotationMethod", null);
		IOEvent ioEvent = method.getAnnotation(IOEvent.class);
		IOEventRecordInfo ioeventRecordInfo = new IOEventRecordInfo("1155", "process name", "recordOutput", new StopWatch(),1000L,null);
		when(ioEventService.getOutputKey(ioEvent.gatewayOutput().output()[0])).thenReturn("Output2");		
		Message messageResult = messageBuilderService.buildTransitionGatewayExclusiveMessage(ioEvent, null, ioEventResponse,
				ioEvent.gatewayOutput().output()[0], ioeventRecordInfo, (long) 123546,headersMap,false);
		Message<String> message = MessageBuilder.withPayload("payload").setHeader(KafkaHeaders.TOPIC, "test-topic")
				.setHeader(KafkaHeaders.MESSAGE_KEY, "1155").setHeader(IOEventHeaders.CORRELATION_ID.toString(), "1155")
				.setHeader(IOEventHeaders.STEP_NAME.toString(), "test annotation")
				.setHeader(IOEventHeaders.EVENT_TYPE.toString(), IOEventType.GATEWAY_PARALLEL.toString())
				.setHeader(IOEventHeaders.INPUT.toString(), new ArrayList<String>(Arrays.asList("previous Task")))
				.setHeader(IOEventHeaders.OUTPUT_EVENT.toString(), "Output2")
				.setHeader(IOEventHeaders.PROCESS_NAME.toString(), "process name")
				.setHeader(IOEventHeaders.START_TIME.toString(), (long) 123546).build();
		
		assertEquals(message.getHeaders().get(IOEventHeaders.OUTPUT_EVENT.toString()),
				messageResult.getHeaders().get(IOEventHeaders.OUTPUT_EVENT.toString()));
		assertEquals(message.getHeaders().get("kafka_messageKey"), messageResult.getHeaders().get("kafka_messageKey"));

		/*
		 * Method method2 = this.getClass().getMethod("endAnnotationMethod2", null);
		 * IOEvent ioEvent2 = method2.getAnnotation(IOEvent.class); Message
		 * messageResult2 = endAspect.buildEventMessage(ioEvent2, "payload", "END",
		 * ioeventRecordInfo, (long) 123546); assertEquals("test-",
		 * messageResult2.getHeaders().get("kafka_topic"));
		 */
	}

	@Test
	void prepareAndDisplayEventLoggerTest() throws JsonProcessingException, NoSuchMethodException, SecurityException, ParseException {

		Method method = this.getClass().getMethod("suffixTaskAnnotation", null);
		IOEvent ioEvent = method.getAnnotation(IOEvent.class);
		StopWatch watch = new StopWatch();
		watch.start("IOEvent annotation Task Aspect");
		EventLogger eventLogger = new EventLogger();
		eventLogger.startEventLog();
		IOEventRecordInfo ioeventRecordInfo = new IOEventRecordInfo("1155", "process name", "output", watch,1000L,null);
		eventLogger.setEndTime(eventLogger.getISODate(new Date()));

		transitionAspect.prepareAndDisplayEventLogger(eventLogger, ioeventRecordInfo, ioEvent, "output", watch, "payload",
				IOEventType.TASK);

		assertThatNoException();

	}

	//@Test
	void simpleEventSendProcessTest() throws ParseException, NoSuchMethodException, SecurityException, InterruptedException, ExecutionException {
		Method method = this.getClass().getMethod("simpleTaskAnnotationMethod", null);
		IOEvent ioEvent = method.getAnnotation(IOEvent.class);
		Method method2 = this.getClass().getMethod("suffixTaskAnnotation", null);
		IOEvent ioEvent2 = method2.getAnnotation(IOEvent.class);
		ListenableFuture<SendResult<String, Object>> future = new SettableListenableFuture<>();
		when(kafkaTemplate.send(Mockito.any(Message.class))).thenReturn(future);
		when(ioEventService.getOutputs(ioEvent)).thenReturn(Arrays.asList(ioEvent.output()));
		when(ioEventService.getOutputs(ioEvent2)).thenReturn(Arrays.asList(ioEvent2.output()));
		when(ioEventService.getOutputKey(ioEvent.output()[0])).thenReturn("output");		
		when(ioEventService.getInputEventByName(Mockito.any(), Mockito.any())).thenReturn(ioEvent.input()[0]);
		when(iOEventProperties.getPrefix()).thenReturn("test-");
		IOEventRecordInfo ioeventRecordInfoForSuffix = new IOEventRecordInfo("1155", "process name", "previous output",
				new StopWatch(),1000L,null);
		StopWatch watch = new StopWatch();
		EventLogger eventLogger = new EventLogger();
		eventLogger.startEventLog();
		watch.start("IOEvent annotation Start Aspect");
		Map<String, Object> headersMap=new HashMap<>();
		IOResponse<Object> ioEventResponse = new IOResponse<>(null, "payload", null);
		String simpleTaskoutput = transitionAspect.simpleEventSendProcess(eventLogger,ioEvent, null, ioEventResponse, "", ioeventRecordInfo,
				IOEventType.TASK);
		String suffixTaskoutput = transitionAspect.simpleEventSendProcess(eventLogger,ioEvent2, null, ioEventResponse, "",
				ioeventRecordInfoForSuffix, IOEventType.TASK);
		assertEquals("output,", simpleTaskoutput);
		assertEquals("previous output_suffixAdded", suffixTaskoutput);

	}

	//@Test
	void parallelEventSendProcessTest() throws ParseException, NoSuchMethodException, SecurityException, InterruptedException, ExecutionException {
		Map<String, Object> headersMap=new HashMap<>();
		IOResponse<Object> ioEventResponse = new IOResponse<>(null, "payload", null);
		Method method = this.getClass().getMethod("parralelTaskAnnotationMethod", null);
		IOEvent ioEvent = method.getAnnotation(IOEvent.class);
		ListenableFuture<SendResult<String, Object>> future = new SettableListenableFuture<>();
		when(kafkaTemplate.send(Mockito.any(Message.class))).thenReturn(future);
		when(ioEventService.getOutputs(ioEvent)).thenReturn(Arrays.asList(ioEvent.gatewayOutput().output()));
		when(iOEventProperties.getPrefix()).thenReturn("test-");
		when(ioEventService.getOutputKey(ioEvent.gatewayOutput().output()[0])).thenReturn("Output1");
		when(ioEventService.getOutputKey(ioEvent.gatewayOutput().output()[1])).thenReturn("Output2");
		when(future.get().getRecordMetadata().timestamp()).thenReturn((new Date()).getTime());

		IOEventRecordInfo ioeventRecordInfoForSuffix = new IOEventRecordInfo("1155", "process name", "previous output",
				new StopWatch(),1000L,null);
		StopWatch watch = new StopWatch();
		EventLogger eventLogger = new EventLogger();
		eventLogger.startEventLog();
		eventLogger.setEndTime(eventLogger.getISODate(new Date()));
		watch.start("IOEvent annotation Start Aspect");
		String simpleTaskoutput = messageBuilderService.parallelEventSendProcess(eventLogger,ioEvent, null, ioEventResponse, "",
				ioeventRecordInfo,false);
		assertEquals("Output1,Output2,", simpleTaskoutput);

	}

	//@Test
	void exclusiveEventSendProcessTest() throws NoSuchMethodException, SecurityException, ParseException, InterruptedException, ExecutionException {

		Method method = this.getClass().getMethod("exclusiveTaskAnnotationMethod", null);
		IOEvent ioEvent = method.getAnnotation(IOEvent.class);
		ListenableFuture<SendResult<String, Object>> future = new SettableListenableFuture<>();
		when(kafkaTemplate.send(Mockito.any(Message.class))).thenReturn(future);
		when(ioEventService.getOutputs(ioEvent)).thenReturn(Arrays.asList(ioEvent.gatewayOutput().output()));
		when(ioEventService.getOutputKey(ioEvent.gatewayOutput().output()[0])).thenReturn("Output1");		
		when(ioEventService.getOutputKey(ioEvent.gatewayOutput().output()[1])).thenReturn("Output2");
		when(ioEventService.validExclusiveOutput(ioEvent, new IOResponse<>("Output2", "payload"))).thenReturn(true);
		IOEventRecordInfo ioeventRecordInfoForSuffix = new IOEventRecordInfo("1155", "process name", "previous output",
				new StopWatch(),1000L,null);
		StopWatch watch = new StopWatch();
		EventLogger eventLogger = new EventLogger();
		eventLogger.startEventLog();
		watch.start("IOEvent annotation Start Aspect");
		String simpleTaskoutput = messageBuilderService.exclusiveEventSendProcess(eventLogger,ioEvent, null,
				new IOResponse<String>("Output2", "payload"), "", ioeventRecordInfo,false);
		assertEquals("Output2,", simpleTaskoutput);

	}

	@Test
	void iOEventAnnotationAspectTest() throws Throwable {
		Method methodSimpleTask = this.getClass().getMethod("simpleTaskAnnotationMethod", null);
		IOEvent ioEventSimpleTask = methodSimpleTask.getAnnotation(IOEvent.class);
		Method methodExclusive = this.getClass().getMethod("exclusiveTaskAnnotationMethod", null);
		IOEvent ioEventExclusive = methodExclusive.getAnnotation(IOEvent.class);
		Method methodParallel = this.getClass().getMethod("parralelTaskAnnotationMethod", null);
		IOEvent ioEventParallel = methodParallel.getAnnotation(IOEvent.class);
		Method endMethod = this.getClass().getMethod("endAnnotationMethod", null);
		IOEvent ioEventEnd = endMethod.getAnnotation(IOEvent.class);

		ListenableFuture<SendResult<String, Object>> future = new SettableListenableFuture<>();
		StopWatch watch = new StopWatch();
		watch.start("IOEvent annotation Task Aspect");
		IOEventContextHolder.setContext(ioeventRecordInfo);
		when(ioeventRecordInfo.getWatch()).thenReturn(watch);
		when(kafkaTemplate.send(Mockito.any(Message.class))).thenReturn(future);
		when(ioEventService.getOutputs(ioEventSimpleTask)).thenReturn(Arrays.asList(ioEventSimpleTask.output()));
		when(ioEventService.checkTaskType(ioEventSimpleTask)).thenReturn(IOEventType.TASK);
		when(iOEventProperties.getPrefix()).thenReturn("test-");
		when(joinPoint.getArgs()).thenReturn(new String[] { "payload" });

		transitionAspect.transitionAspect(joinPoint, ioEventSimpleTask, "payload");
		transitionAspect.transitionAspect(joinPoint, ioEventExclusive,
				new IOResponse<String>("Output2", "payload"));
		transitionAspect.transitionAspect(joinPoint, ioEventParallel, "payload");
		transitionAspect.transitionAspect(joinPoint, ioEventEnd, "payload");

	}

}
