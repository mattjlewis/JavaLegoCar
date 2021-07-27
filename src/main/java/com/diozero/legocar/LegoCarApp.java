package com.diozero.legocar;

import org.tinylog.Logger;

import com.diozero.api.AnalogOutputDevice;
import com.diozero.api.ServoDevice;
import com.diozero.devices.LED;
import com.diozero.devices.PiconZero;
import com.diozero.sdl.joystick.GameController;
import com.diozero.sdl.joystick.JoystickEvent;
import com.diozero.sdl.joystick.JoystickEvent.AxisMotionEvent;
import com.diozero.sdl.joystick.JoystickEvent.ButtonEvent;
import com.diozero.sdl.joystick.JoystickEvent.DeviceEvent;
import com.diozero.sdl.joystick.JoystickEventListener;
import com.diozero.sdl.joystick.JoystickInfo;
import com.diozero.sdl.joystick.JoystickNative;
import com.diozero.util.Diozero;
import com.diozero.util.RangeUtil;
import com.diozero.util.SleepUtil;

public class LegoCarApp implements AutoCloseable, JoystickEventListener {
	// Picon Zero connections
	private static final int STEERING_GPIO = 0;
	private static final int LED1_GPIO = 3;
	private static final int LED2_GPIO = 4;
	private static final int SERVO_MAX_ANGLE_FROM_CENTRE = 80;

	private static final boolean TEST_SERVO_ON_START = false;
	private static final boolean TEST_LEDS_ON_START = false;

	public static void main(String[] args) {
		Logger.info("Compiled against SDL2 {}, linked against {}", JoystickNative.getCompiledVersion(),
				JoystickNative.getLinkedVersion());
		Logger.info("Joysticks:");
		for (JoystickInfo j_info : JoystickNative.listJoysticks()) {
			Logger.info(j_info);
		}
		if (JoystickNative.getNumJoysticks() == 0) {
			Logger.error("No joysticks");
			System.exit(1);
		}

		try (LegoCarApp app = new LegoCarApp()) {
			if (TEST_SERVO_ON_START) {
				app.testServo();
			}

			if (TEST_LEDS_ON_START) {
				app.testLEDs();
			}

			JoystickNative.processEvents();
		} catch (Exception e) {
			Logger.error(e, "Error: {}", e);
		} finally {
			Diozero.shutdown();
		}
	}

	public static int calcServoAngle(float value) {
		return (int) (RangeUtil.constrain(value, -1, 1) * SERVO_MAX_ANGLE_FROM_CENTRE) + PiconZero.SERVO_CENTRE;
	}

	private PiconZero pz;
	private ServoDevice steering;
	private LED light1;
	private LED light2;
	private AnalogOutputDevice motor;
	private GameController joystick;

	private LegoCarApp() {
		joystick = (GameController) JoystickNative.getJoystickOrGameController(0);
		joystick.setListener(this);

		pz = new PiconZero();
		// Configure the outputs
		steering = ServoDevice.newBuilder(STEERING_GPIO).setDeviceFactory(pz).build();
		light1 = new LED(pz, LED1_GPIO);
		light2 = new LED(pz, LED2_GPIO);
		motor = AnalogOutputDevice.Builder.builder(PiconZero.PiconZeroBoardPinInfo.MOTOR2_GPIO).setDeviceFactory(pz)
				.build();
	}

	@Override
	public void accept(JoystickEvent event) {
		Logger.info("Joystick event: {}", event);

		if (event.getCategory() == JoystickEvent.Category.DEVICE) {
			DeviceEvent d_event = (DeviceEvent) event;
			switch (d_event.getType()) {
			case GAME_CONTROLLER_ADDED:
				if (joystick == null) {
					joystick = (GameController) JoystickNative.getJoystickOrGameController(event.getJoystickId());
				}
				break;
			case GAME_CONTROLLER_REMOVED:
				if (joystick != null && joystick.getId() == event.getJoystickId()) {
					joystick.close();
					joystick = null;
				}
				break;
			default:
				Logger.info("Unhandled device event: {}", event);
				// Ignore
			}
		} else if (joystick != null) {
			switch (event.getCategory()) {
			case BUTTON_PRESS:
				ButtonEvent b_event = (ButtonEvent) event;
				GameController.Button button = joystick.getButtonMapping(b_event.getButton());
				if (button == null) {
					Logger.error("Button was null for {}", b_event);
				} else {
					Logger.debug("Got button {}", button);
					switch (button) {
					case GUIDE:
						JoystickNative.stopEventLoop();
						break;
					case A:
						light1.setOn(b_event.isPressed());
						break;
					case B:
						light2.setOn(b_event.isPressed());
						break;
					default:
						Logger.info("Unhandled button event {}", b_event);
					}
				}
				break;
			case AXIS_MOTION:
				AxisMotionEvent a_event = (AxisMotionEvent) event;
				GameController.Axis axis = joystick.getAxisMapping(a_event.getAxis());
				if (axis == null) {
					Logger.error("Axis was null for {}", a_event);
				} else {
					Logger.debug("Got axis {}", axis);
					switch (axis) {
					case LEFTX:
						steering.setAngle(calcServoAngle(a_event.getValue()));
						break;
					case RIGHTX:
						// Invert the axis value
						motor.setValue(-a_event.getValue());
						break;
					default:
						Logger.info("Unhandled axis motion event: {}", a_event);
					}
				}
				break;
			default:
				// Ignore
			}
		}
	}

	private void testServo() {
		// Change speed of continuous servo on channel O
		Logger.info("Setting servo to mid");
		pz.setOutputValue(STEERING_GPIO, PiconZero.SERVO_CENTRE);
		SleepUtil.sleepSeconds(1);
		Logger.info("Setting servo to min");
		pz.setOutputValue(STEERING_GPIO, PiconZero.SERVO_CENTRE - SERVO_MAX_ANGLE_FROM_CENTRE);
		SleepUtil.sleepSeconds(1);
		Logger.info("Setting servo to max");
		pz.setOutputValue(STEERING_GPIO, PiconZero.SERVO_CENTRE + SERVO_MAX_ANGLE_FROM_CENTRE);
		SleepUtil.sleepSeconds(1);
		Logger.info("Setting servo to mid");
		pz.setOutputValue(STEERING_GPIO, PiconZero.SERVO_CENTRE);
	}

	private void testLEDs() {
		int channel = LED1_GPIO;
		Logger.info("Testing channel {}", Integer.valueOf(channel));
		Logger.info("LED on for channel {}", Integer.valueOf(channel));
		pz.setOutputValue(channel, true);
		SleepUtil.sleepSeconds(1);
		Logger.info("LED off for channel {}", Integer.valueOf(channel));
		pz.setOutputValue(channel, false);
		SleepUtil.sleepSeconds(1);

		channel = LED2_GPIO;
		Logger.info("Testing channel {}", Integer.valueOf(channel));
		Logger.info("LED on for channel {}", Integer.valueOf(channel));
		pz.setOutputValue(channel, true);
		SleepUtil.sleepSeconds(1);
		Logger.info("LED off for channel {}", Integer.valueOf(channel));
		pz.setOutputValue(channel, false);
		SleepUtil.sleepSeconds(1);
	}

	@Override
	public void close() {
		if (joystick != null) {
			joystick.close();
		}
		motor.close();
		steering.close();
		light1.close();
		light2.close();
		pz.close();
	}
}
