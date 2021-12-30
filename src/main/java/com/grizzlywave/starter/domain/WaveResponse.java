package com.grizzlywave.starter.domain;

/**
 * Class WaveResponse Contains an object and a hash map to give more information
 * about events after sending or receiving using GrizzlyWave annotations
 * 
 * 
 **/
public class WaveResponse<T> {

	/**
	 * object of the events
	 **/
	T object;
	/**
	 * HashMap to add more information about event
	 **/
	WaveResponseHeader Headers;

	/**
	 * Constructor from super class
	 **/
	public WaveResponse() {
	}

	/**
	 * constructor using parameters
	 **/
	public WaveResponse(T object, WaveResponseHeader headers) {
		this.object = object;
		this.Headers = headers;
	}

	/**
	 * to get object
	 **/
	public Object getObject() {
		return object;
	}

	/**
	 * to set object
	 **/
	public void setObject(T object) {
		this.object = object;
	}

	/**
	 * to get Header
	 **/
	public WaveResponseHeader getHeaders() {
		return Headers;
	}

	/**
	 * to set Headers
	 **/
	public void setHeaders(WaveResponseHeader herders) {
		Headers = herders;
	}

	/**
	 * to get the string of our WaveResponse
	 **/
	@Override
	public String toString() {
		return "WaveResponse [object=" + object + ", Headers=" + Headers.toString() + "]";
	}

}
