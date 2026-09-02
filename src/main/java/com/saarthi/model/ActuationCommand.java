package com.saarthi.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Actuation Command DTO for Hardware Relays, Exhaust Blowers & System Simulators.
 */
public class ActuationCommand {

    @NotBlank
    @Size(max = 32)
    private String target; // 'FAN_01', 'RELAY_23', 'CROP_PROFILE', 'SIMULATION'

    @NotBlank
    @Size(max = 32)
    private String action; // 'RELAY_ON', 'RELAY_OFF', 'SET_RPM', 'SWITCH_CROP', 'SPIKE', 'RESET'

    @Min(0)
    @Max(3600)
    private Integer durationSeconds;

    @Min(0)
    @Max(3000)
    private Integer rpm;

    @Size(max = 32)
    private String crop;   // 'mushroom' or 'hydro'
    private String reason;

    public ActuationCommand() {}

    public ActuationCommand(String target, String action, Integer durationSeconds, Integer rpm, String reason) {
        this.target = target;
        this.action = action;
        this.durationSeconds = durationSeconds;
        this.rpm = rpm;
        this.reason = reason;
    }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public Integer getRpm() { return rpm; }
    public void setRpm(Integer rpm) { this.rpm = rpm; }

    public String getCrop() { return crop; }
    public void setCrop(String crop) { this.crop = crop; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
