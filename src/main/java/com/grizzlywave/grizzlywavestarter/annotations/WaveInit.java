package com.grizzlywave.grizzlywavestarter.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import com.grizzlywave.grizzlywavestarter.model.Order;

/**
 * annotation that allows you to initiate an instance process with id chosen by
 * the developer or generated automatically also target_event as our event
 * destination and target_topic as the topic of our event
 **/
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WaveInit {

	public String id() default "";

	public String target_event();

	public String target_topic();

}