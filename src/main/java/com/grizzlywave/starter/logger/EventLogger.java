package com.grizzlywave.starter.logger;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

public class EventLogger {
	private UUID correlationId;
	private String workflow;
	private String stepName;
	private String sourceEvent;
	private String targetEvent;
	private String eventType;
	private Object payload;
	private String startTime;
	private String endTime;
	private Long duration;

	public EventLogger() {
		super();
	}

	public EventLogger(UUID correlationId, String workflow, String stepName, String sourceEvent, String targetEvent,
			String eventType, Object payload) {
		this.correlationId = correlationId;
		this.workflow = workflow;
		this.stepName = stepName;
		this.sourceEvent = sourceEvent;
		this.targetEvent = targetEvent;
		this.eventType = eventType;
		this.payload = payload;
	}

	public EventLogger(UUID correlationId, String workflow, String stepName, String sourceEvent, String targetEvent,
			String eventType, Object payload, String startTime, String endTime, Long duration) {
		this.correlationId = correlationId;
		this.workflow = workflow;
		this.stepName = stepName;
		this.sourceEvent = sourceEvent;
		this.targetEvent = targetEvent;
		this.eventType = eventType;
		this.payload = payload;
		this.startTime = startTime;
		this.endTime = endTime;
		this.duration = duration;
	}

	public UUID getCorrelationId() {
		return correlationId;
	}

	public void setCorrelationId(UUID correlationId) {
		this.correlationId = correlationId;
	}

	public String getWorkflow() {
		return workflow;
	}

	public void setWorkflow(String workflow) {
		this.workflow = workflow;
	}

	public String getStepName() {
		return stepName;
	}

	public void setStepName(String stepName) {
		this.stepName = stepName;
	}

	public String getSourceEvent() {
		return sourceEvent;
	}

	public void setSourceEvent(String sourceEvent) {
		this.sourceEvent = sourceEvent;
	}

	public String getTargetEvent() {
		return targetEvent;
	}

	public void setTargetEvent(String targetEvent) {
		this.targetEvent = targetEvent;
	}

	public String getEventType() {
		return eventType;
	}

	public void setEventType(String eventType) {
		this.eventType = eventType;
	}

	public Object getPayload() {
		return payload;
	}

	public void setPayload(Object payload) {
		this.payload = payload;
	}

	public String getStartTime() {
		return startTime;
	}

	public void setStartTime(String startTime) {
		this.startTime = startTime;
	}

	public String getEndTime() {
		return endTime;
	}

	public void setEndTime(String endTime) {
		this.endTime = endTime;
	}

	public Long getDuration() {
		return duration;
	}

	public void setDuration(Long duration) {
		this.duration = duration;
	}

	public void startEventLog() {
		this.startTime = this.getISODate(new Date());
	}

	public void stopEvent(Long durationMilli) {
		this.endTime = this.getISODate(new Date());
		this.duration = durationMilli;
	}

	public String getISODate(Date date) {
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss:SSSS'Z'");
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		return dateFormat.format(date);
	}

	public void setting(UUID correlationId, String workflow, String stepName, String sourceEvent, String targetEvent,
			String eventType, Object payload) {
		this.correlationId = correlationId;
		this.workflow = workflow;
		this.stepName=stepName;
		this.sourceEvent=sourceEvent;
		this.targetEvent=targetEvent;
		this.eventType=eventType;
		this.payload=payload;
	}
}