package com.diozero.legocar;

@SuppressWarnings("boxing")
public class Test {
	private static final int SERVO_MAX_ANGLE_FROM_CENTRE = 80;
	private static final int PZ_SERVO_MID = 90;
	
	public static void main(String[] args) {
		for (float value=-1.5f; value<2f; value+=0.5f) {
			int servo_angle = calcServoAngle(value);
			System.out.format("value=%f, servo_angle=%d%n", value, servo_angle);
		}
	}
	
	public static int calcServoAngle(float value) {
		float v = value;
		if (value < -1) {
			v = -1;
		} else if (value > 1) {
			v = 1;
		}
		return (int) (v * SERVO_MAX_ANGLE_FROM_CENTRE) + PZ_SERVO_MID;
	}
}
