package com.saarthi.model;

/**
 * Declarative Agronomic Crop Recipe defining micro-climate envelopes and actuation targets.
 */
public class CropRecipe {

    private String cropId;
    private String name;
    private double minCo2;
    private double maxCo2;
    private double spikeCo2Threshold;
    private double minRh;
    private double maxRh;
    private double minTemp;
    private double maxTemp;
    private int baselineFanRpm;
    private int purgeFanRpm;

    public CropRecipe() {}

    public CropRecipe(String cropId, String name, double minCo2, double maxCo2, double spikeCo2Threshold,
                      double minRh, double maxRh, double minTemp, double maxTemp,
                      int baselineFanRpm, int purgeFanRpm) {
        this.cropId = cropId;
        this.name = name;
        this.minCo2 = minCo2;
        this.maxCo2 = maxCo2;
        this.spikeCo2Threshold = spikeCo2Threshold;
        this.minRh = minRh;
        this.maxRh = maxRh;
        this.minTemp = minTemp;
        this.maxTemp = maxTemp;
        this.baselineFanRpm = baselineFanRpm;
        this.purgeFanRpm = purgeFanRpm;
    }

    public String getCropId() { return cropId; }
    public void setCropId(String cropId) { this.cropId = cropId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getMinCo2() { return minCo2; }
    public void setMinCo2(double minCo2) { this.minCo2 = minCo2; }

    public double getMaxCo2() { return maxCo2; }
    public void setMaxCo2(double maxCo2) { this.maxCo2 = maxCo2; }

    public double getSpikeCo2Threshold() { return spikeCo2Threshold; }
    public void setSpikeCo2Threshold(double spikeCo2Threshold) { this.spikeCo2Threshold = spikeCo2Threshold; }

    public double getMinRh() { return minRh; }
    public void setMinRh(double minRh) { this.minRh = minRh; }

    public double getMaxRh() { return maxRh; }
    public void setMaxRh(double maxRh) { this.maxRh = maxRh; }

    public double getMinTemp() { return minTemp; }
    public void setMinTemp(double minTemp) { this.minTemp = minTemp; }

    public double getMaxTemp() { return maxTemp; }
    public void setMaxTemp(double maxTemp) { this.maxTemp = maxTemp; }

    public int getBaselineFanRpm() { return baselineFanRpm; }
    public void setBaselineFanRpm(int baselineFanRpm) { this.baselineFanRpm = baselineFanRpm; }

    public int getPurgeFanRpm() { return purgeFanRpm; }
    public void setPurgeFanRpm(int purgeFanRpm) { this.purgeFanRpm = purgeFanRpm; }
}
