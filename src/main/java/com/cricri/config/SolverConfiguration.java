package com.cricri.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration externalisée pour les paramètres du solveur CP-SAT OR-Tools.
 */
@Configuration
@ConfigurationProperties(prefix = "solver")
public class SolverConfiguration {

    private boolean logSearchProgress = true;
    private int maxTimeInSeconds = 300;
    private int numWorkers = 4;
    private String searchBranching = "AUTOMATIC";
    private String preferredVariableOrder = "IN_ORDER";

    public boolean isLogSearchProgress() {
        return logSearchProgress;
    }

    public void setLogSearchProgress(boolean logSearchProgress) {
        this.logSearchProgress = logSearchProgress;
    }

    public int getMaxTimeInSeconds() {
        return maxTimeInSeconds;
    }

    public void setMaxTimeInSeconds(int maxTimeInSeconds) {
        this.maxTimeInSeconds = maxTimeInSeconds;
    }

    public int getNumWorkers() {
        return numWorkers;
    }

    public void setNumWorkers(int numWorkers) {
        this.numWorkers = numWorkers;
    }

    public String getSearchBranching() {
        return searchBranching;
    }

    public void setSearchBranching(String searchBranching) {
        this.searchBranching = searchBranching;
    }

    public String getPreferredVariableOrder() {
        return preferredVariableOrder;
    }

    public void setPreferredVariableOrder(String preferredVariableOrder) {
        this.preferredVariableOrder = preferredVariableOrder;
    }
}