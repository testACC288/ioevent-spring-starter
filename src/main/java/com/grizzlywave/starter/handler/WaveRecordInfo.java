package com.grizzlywave.starter.handler;

public class WaveRecordInfo {
	private String id;

	private String workFlowName;
	
	private String targetName;

	public WaveRecordInfo() {
	}

	public WaveRecordInfo(String id, String workFlowName, String targetName) {
		this.id = id;
		this.workFlowName = workFlowName;
		this.targetName = targetName;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getWorkFlowName() {
		return workFlowName;
	}

	public void setWorkFlowName(String workFlowName) {
		this.workFlowName = workFlowName;
	}

	public String getTargetName() {
		return targetName;
	}

	public void setTargetName(String targetName) {
		this.targetName = targetName;
	}

}