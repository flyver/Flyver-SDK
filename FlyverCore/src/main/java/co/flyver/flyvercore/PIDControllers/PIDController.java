/*
 * Copyright (c) 2014. Flyver
 */

package co.flyver.flyvercore.PIDControllers;

/**
 * PID Controller used for vertical stabilization and other
 * Source from Androcopter project: https://code.google.com/p/andro-copter/
 */

public class PIDController {
    

    /* Variables */

    private float kp, ki, kd, integrator, smoothingStrength, differencesMean,
            previousDifference, aPriori;

    /* End of variables */


    public PIDController(float kp, float ki, float kd, float smoothingStrength,
                         float aPriori) {
        this.kp = kp;
        this.ki = ki;
        this.kd = kd;
        this.smoothingStrength = smoothingStrength;
        this.aPriori = aPriori;

        previousDifference = 0.0f;

        integrator = 0.0f;
        differencesMean = 0.0f;
    }

    public float getInput(float targetAngle, float currentAngle, float dt) {
        float difference = targetAngle - currentAngle;

        // Now, the PID computation can be done.
        float input = aPriori;

        // Proportional part.
        input += difference * kp;

        // Integral part.
        integrator += difference * ki * dt;
        input += integrator;

        // Derivative part, with filtering.
        differencesMean = differencesMean * smoothingStrength
                + difference * (1 - smoothingStrength);
        float derivative = (differencesMean - previousDifference) / dt;
        previousDifference = differencesMean;
        input += derivative * kd;

        return input;
    }

    public void setCoefficients(float kp, float ki, float kd) {
        this.kp = kp;
        this.ki = ki;
        this.kd = kd;
    }

    public void setAPriori(float aPriori) {
        this.aPriori = aPriori;
    }

    public void resetIntegrator() {
        integrator = 0.0f;
    }
}