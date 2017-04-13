package com.diozero.legocar;

import java.io.IOException;

import org.pmw.tinylog.Logger;

import com.diozero.PiconZero;
import com.diozero.sdl.joystick.*;
import com.diozero.sdl.joystick.JoystickEvent.AxisMotionEvent;
import com.diozero.sdl.joystick.JoystickEvent.ButtonEvent;
import com.diozero.util.RangeUtil;
import com.diozero.util.SleepUtil;

public class LegoCarApp {
	// Input mapping
	private static final int JS_STEERING_AXIS = PS3DualshockController.LEFT_STICK_HORIZ_AXIS;
	private static final int JS_ENGINE_AXIS = PS3DualshockController.RIGHT_STICK_VERT_AXIS;
	private static final int JS_EXIT_BUTTON = PS3DualshockController.PS3_START_BUTTON;
	private static final int JS_LIGHT1_BUTTON = PS3DualshockController.PS3_SQUARE_BUTTON;
	private static final int JS_LIGHT2_BUTTON = PS3DualshockController.PS3_X_BUTTON;
	private static final int JS_SHUTDOWN_BUTTON = PS3DualshockController.PS3_O_BUTTON;

	// Picon Zero connections
	private static final int MOTOR = 1;
	private static final int SERVO_CHANNEL = 0;
	private static final int LIGHT1_CHANNEL = 3;
	private static final int LIGHT2_CHANNEL = 4;
	private static final int SERVO_MAX_ANGLE_FROM_CENTRE = 80;

	private static final boolean TEST_SERVO_ON_START = false;
	private static final boolean TEST_LEDS_ON_START = false;
	
	// In milliseconds
	private static final int SHUTDOWN_BUTTON_PRESS_TIME = 4000;
	
	private static PiconZero pz;
	private static Joystick joystick;

	public static void main(String[] args) {
		init();
		
		try {
			if (TEST_SERVO_ON_START) {
				testServo();
			}
			if (TEST_LEDS_ON_START) {
				testLEDs();
			}
			
			Long sd_button_down_time = null;
			boolean running = true;
			while (running) {
				JoystickEvent event = JoystickNative.waitForEvent();
				if (event.getJoystickId() == joystick.getId()) {
					switch (event.getType()) {
					case BUTTON_PRESS:
						ButtonEvent b_event = (ButtonEvent) event;
						switch (b_event.getButton()) {
						case JS_EXIT_BUTTON:
							running = false;
							break;
						case JS_LIGHT1_BUTTON:
							pz.setValue(LIGHT1_CHANNEL, b_event.isPressed());
							break;
						case JS_LIGHT2_BUTTON:
							pz.setValue(LIGHT2_CHANNEL, b_event.isPressed());
							break;
						case JS_SHUTDOWN_BUTTON:
							if (b_event.isPressed()) {
								sd_button_down_time = Long.valueOf(System.currentTimeMillis());
							} else {
								if (sd_button_down_time != null &&
										(System.currentTimeMillis() - sd_button_down_time.longValue()) > SHUTDOWN_BUTTON_PRESS_TIME) {
									shutdown();
								} else {
									sd_button_down_time = null;
								}
							}
							break;
						default:
							Logger.info("Unhandled button event {}", b_event);
						}
						break;
					case AXIS_MOTION:
						AxisMotionEvent a_event = (AxisMotionEvent) event;
						switch (a_event.getAxis()) {
						case JS_STEERING_AXIS:
							setSteering(a_event.getValue());
							break;
						case JS_ENGINE_AXIS:
							// Invert the axis value
							setEngineSpeed(-a_event.getValue());
							break;
						default:
							Logger.info("Unhandled axis motion event: {}", a_event);
						}
						break;
					case DEVICE:
						Logger.info("Unhandled device event: {}", event);
						break;
					case BALL_MOTION:
						Logger.info("Unhandled ball motion event: {}", event);
						break;
					case HAT_MOTION:
						Logger.info("Unhandled hat motion event: {}", event);
						break;
					}
				}
			}
		} finally {
			pz.close();
			joystick.close();
		}
	}
	
	private static void shutdown() {
		Logger.debug("Shutdown initiated!");
		// Close resources
		try {
			joystick.close();
			pz.reset();
			pz.close();
		} catch (Throwable t) {
			Logger.error(t, "Error cleaning up: {}", t);
		}
		// Poweroff
		try {
			Logger.debug("Shutting down!");
			Runtime.getRuntime().exec("sudo poweroff");
		} catch (IOException e) {
			Logger.error(e, "Error running poweroff command: {}", e);
		}
	}

	private static void init() {
		Logger.info("Joysticks:");
		for (JoystickInfo j_info : JoystickNative.listJoysticks()) {
			Logger.info(j_info);
		}
		if (JoystickNative.getNumJoysticks() == 0) {
			Logger.error("No joysticks");
			System.exit(1);
		}
		joystick = JoystickNative.getJoystick(0);
		
		pz = new PiconZero();
		// Configure the outputs
		pz.setOutputConfig(SERVO_CHANNEL, PiconZero.OutputConfig.SERVO);
		pz.setOutputConfig(LIGHT1_CHANNEL, PiconZero.OutputConfig.DIGITAL);
		pz.setOutputConfig(LIGHT2_CHANNEL, PiconZero.OutputConfig.DIGITAL);
	}
	
	private static void setSteering(float value) {
		pz.setOutputValue(SERVO_CHANNEL, calcServoAngle(value));
	}
	
	public static int calcServoAngle(float value) {
		return (int) (RangeUtil.constrain(value, -1, 1) * SERVO_MAX_ANGLE_FROM_CENTRE) + PiconZero.SERVO_CENTRE;
	}
	
	public static void setEngineSpeed(float value) {
		pz.setMotor(MOTOR, RangeUtil.constrain(value, -1, 1));
	}
	
	private static void testServo() {
		// Change speed of continuous servo on channel O
		Logger.info("Setting servo to mid");
		pz.setOutputValue(SERVO_CHANNEL, PiconZero.SERVO_CENTRE);
		SleepUtil.sleepSeconds(1);
		Logger.info("Setting servo to min");
		pz.setOutputValue(SERVO_CHANNEL, PiconZero.SERVO_CENTRE - SERVO_MAX_ANGLE_FROM_CENTRE);
		SleepUtil.sleepSeconds(1);
		Logger.info("Setting servo to max");
		pz.setOutputValue(SERVO_CHANNEL, PiconZero.SERVO_CENTRE + SERVO_MAX_ANGLE_FROM_CENTRE);
		SleepUtil.sleepSeconds(1);
		Logger.info("Setting servo to mid");
		pz.setOutputValue(SERVO_CHANNEL, PiconZero.SERVO_CENTRE);
	}

	private static void testLEDs() {
		int channel = LIGHT1_CHANNEL;
		Logger.info("Testing channel {}", Integer.valueOf(channel));
		Logger.info("LED on for channel {}", Integer.valueOf(channel));
		pz.setValue(channel, true);
		SleepUtil.sleepSeconds(1);
		Logger.info("LED off for channel {}", Integer.valueOf(channel));
		pz.setValue(channel, false);
		SleepUtil.sleepSeconds(1);
	
		channel = LIGHT2_CHANNEL;
		Logger.info("Testing channel {}", Integer.valueOf(channel));
		Logger.info("LED on for channel {}", Integer.valueOf(channel));
		pz.setValue(channel, true);
		SleepUtil.sleepSeconds(1);
		Logger.info("LED off for channel {}", Integer.valueOf(channel));
		pz.setValue(channel, false);
		SleepUtil.sleepSeconds(1);
	}
}
