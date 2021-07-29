package com.diozero.legocar;

import org.tinylog.Logger;

import com.diozero.api.AnalogOutputDevice;
import com.diozero.api.ServoDevice;
import com.diozero.api.ServoTrim;
import com.diozero.devices.LED;
import com.diozero.devices.PiconZero;
import com.diozero.devices.motor.AnalogOutputMotor;
import com.diozero.devices.motor.MotorInterface;
import com.diozero.sdl.joystick.GameController;
import com.diozero.sdl.joystick.Joystick;
import com.diozero.sdl.joystick.JoystickAxisMotionListener;
import com.diozero.sdl.joystick.JoystickButtonListener;
import com.diozero.sdl.joystick.JoystickDeviceListener;
import com.diozero.sdl.joystick.JoystickEvent;
import com.diozero.sdl.joystick.JoystickInfo;
import com.diozero.sdl.joystick.JoystickNative;
import com.diozero.util.Diozero;
import com.diozero.util.Hex;
import com.diozero.util.SleepUtil;

public class LegoCarApp
		implements AutoCloseable, JoystickDeviceListener, JoystickButtonListener, JoystickAxisMotionListener {
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
			return;
		}

		try (LegoCarApp app = new LegoCarApp()) {
			if (TEST_SERVO_ON_START) {
				app.testServo();
			}

			if (TEST_LEDS_ON_START) {
				app.testLEDs();
			}

			Joystick.addDeviceListener(app);
			JoystickNative.processEvents();
		} catch (Exception e) {
			Logger.error(e, "Error: {}", e);
		} finally {
			Diozero.shutdown();
		}
	}

	// Picon Zero connections
	private static final int STEERING_GPIO = 0;
	private static final int LED1_GPIO = 3;
	private static final int LED2_GPIO = 4;
	// Don't need the full servo range (typical range is 2 * 900 us)
	private static final ServoTrim SERVO_TRIM = new ServoTrim(ServoTrim.DEFAULT_MID_US, ServoTrim.DEFAULT_90_DELTA_US,
			2 * 700);

	private GameController gameController;
	private PiconZero pz;
	private ServoDevice steering;
	private LED light1;
	private LED light2;
	private MotorInterface motor;

	private LegoCarApp() {
		gameController = (GameController) JoystickNative.getJoystickOrGameController(0);
		Logger.info("gameController type: {}, GUID: {}", gameController.getGameControllerType(),
				Hex.encodeHexString(gameController.getGuid()));

		gameController.setButtonListener(this);
		gameController.setAxisMotionListener(this);

		pz = new PiconZero();
		// Configure the outputs
		steering = ServoDevice.newBuilder(STEERING_GPIO).setTrim(SERVO_TRIM).setDeviceFactory(pz).build();
		light1 = new LED(pz, LED1_GPIO);
		light2 = new LED(pz, LED2_GPIO);
		motor = new AnalogOutputMotor(AnalogOutputDevice.Builder.builder(PiconZero.PiconZeroBoardPinInfo.MOTOR2_GPIO)
				.setDeviceFactory(pz).build());
	}

	@Override
	public void accept(JoystickEvent.DeviceEvent event) {
		switch (event.getType()) {
		case GAME_CONTROLLER_ADDED:
			if (gameController == null) {
				gameController = (GameController) JoystickNative.getJoystickOrGameController(event.getJoystickId());
			}
			break;
		case GAME_CONTROLLER_REMOVED:
			if (gameController != null && gameController.getId() == event.getJoystickId()) {
				gameController.close();
				gameController = null;
			}
			break;
		default:
			Logger.info("Unhandled device event: {}", event);
			// Ignore
		}
	}

	@Override
	public void accept(JoystickEvent.ButtonEvent event) {
		GameController.Button button = event.getButton(gameController);
		Logger.debug("Got button {}", button);

		switch (button) {
		case GUIDE:
			JoystickNative.stopEventLoop();
			break;
		case A:
			light1.setOn(event.isPressed());
			break;
		case B:
			light2.setOn(event.isPressed());
			break;
		default:
			Logger.info("Unhandled button event {}", event);
		}
	}

	@Override
	public void accept(JoystickEvent.AxisMotionEvent event) {
		GameController.Axis axis = event.getAxis(gameController);
		Logger.debug("Got axis {}", axis);

		switch (axis) {
		case LEFTX:
			steering.setValue(event.getValue());
			break;
		case RIGHTY:
			// Invert the axis value
			motor.setValue(-event.getValue());
			break;
		default:
			Logger.info("Unhandled axis motion event: {}", event);
		}
	}

	@Override
	public void close() {
		if (gameController != null) {
			gameController.close();
		}
		motor.close();
		steering.close();
		light1.close();
		light2.close();
		pz.close();
	}

	private void testServo() {
		// Change speed of continuous servo on channel O
		Logger.info("Setting servo to mid");
		pz.setOutputValue(STEERING_GPIO, PiconZero.SERVO_CENTRE);
		SleepUtil.sleepSeconds(1);
		Logger.info("Setting servo to min");
		pz.setOutputValue(STEERING_GPIO, SERVO_TRIM.getMinAngle());
		SleepUtil.sleepSeconds(1);
		Logger.info("Setting servo to max");
		pz.setOutputValue(STEERING_GPIO, SERVO_TRIM.getMaxAngle());
		SleepUtil.sleepSeconds(1);
		Logger.info("Setting servo to mid");
		pz.setOutputValue(STEERING_GPIO, SERVO_TRIM.getMidAngle());
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
}
