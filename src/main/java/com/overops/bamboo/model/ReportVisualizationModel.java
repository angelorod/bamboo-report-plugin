package com.overops.bamboo.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.takipi.api.client.util.cicd.OOReportEvent;
import com.takipi.api.client.util.cicd.QualityGateReport;
import com.takipi.api.client.util.regression.RateRegression;
import com.takipi.api.client.util.regression.RegressionInput;

public class ReportVisualizationModel {
    private boolean unstable;
    private boolean markedUnstable;
    private boolean passedNewErrorGate;
    private boolean checkNewEvents;
    private boolean passedResurfacedErrorGate;
    private boolean checkResurfacedEvents;
    private boolean checkCriticalErrors;
    private boolean passedCriticalErrorGate;
    private boolean checkTotalErrors;
    private boolean passedTotalErrorGate;
    private boolean checkUniqueErrors;
    private boolean hasTopErrors;
    private boolean passedUniqueErrorGate;
    private boolean checkRegressedErrors;
    private boolean passedRegressedEvents;

    private String summary;
    private String newErrorSummary;
    private String resurfacedErrorSummary;
    private String criticalErrorSummary;
    private String totalErrorSummary;
    private String uniqueErrorSummary;
    private String regressionSummary;

    private List<EventVisualizationModel> newEvents = new ArrayList<>();
    private List<EventVisualizationModel> resurfacedEvents = new ArrayList<>();
    private List<EventVisualizationModel> criticalEvents = new ArrayList<>();
    private List<EventVisualizationModel> topEvents = new ArrayList<>();
    private List<EventVisualizationModel> regressedEvents = new ArrayList<>();

    // UI summary table
    private boolean hasTotal;
    private long newGateTotal;
    private long resurfacedGateTotal;
    private long criticalGateTotal;
    private long totalGateTotal;
    private long uniqueGateTotal;
    private long regressionGateTotal;

    // handle exceptions in the UI
    private Exception exception;
    private boolean hasException;
    private String exceptionMessage;
    private String stackTrace;
    private String emailMessage;

    private WebAssets assets = new WebAssets();

    public ReportVisualizationModel() {
    }

    public ReportVisualizationModel(QualityGateReport qualityGateReport, RegressionInput input, RateRegression regression, List<OOReportRegressedEvent> regressions, boolean unstable, boolean checkNewGate, boolean checkResurfacedGate, boolean checkCriticalGate, boolean checkVolumeGate, boolean checkUniqueGate, boolean checkRegressionGate, Integer maxEventVolume, Integer maxUniqueVolume, boolean markedUnstable) {
        setMarkedUnstable(markedUnstable);
        setUnstable(unstable);
        setCheckNewEvents(checkNewGate);
        setCheckResurfacedEvents(checkResurfacedGate);
        setCheckCriticalErrors(checkCriticalGate);
        setCheckUniqueErrors(checkUniqueGate);
        setCheckRegressedErrors(checkRegressionGate);
        setCheckTotalErrors(checkVolumeGate);

        setNewEvents(Optional.ofNullable(qualityGateReport.getNewErrors()).orElse(new ArrayList<>()).stream().map(e -> new EventVisualizationModel(e)).collect(Collectors.toList()));
        setResurfacedEvents(Optional.ofNullable(qualityGateReport.getResurfacedErrors()).orElse(new ArrayList<>()).stream().map(e -> new EventVisualizationModel(e)).collect(Collectors.toList()));
        setCriticalEvents(Optional.ofNullable(qualityGateReport.getCriticalErrors()).orElse(new ArrayList<>()).stream().map(e -> new EventVisualizationModel(e)).collect(Collectors.toList()));
        setTopEvents(Optional.ofNullable(qualityGateReport.getTopErrors()).orElse(new ArrayList<>()).stream().map(e -> new EventVisualizationModel(e)).collect(Collectors.toList()));

        ArrayList<OOReportEvent> allIssues = new ArrayList<>();
        if (regressions != null) {
            allIssues.addAll(regressions);
        }
        setRegressedEvents(allIssues.stream().map(e -> new EventVisualizationModel(e)).collect(Collectors.toList()));

        setPassedNewErrorGate(checkNewGate && !(getNewEvents().size() > 0));
        setPassedResurfacedErrorGate(checkResurfacedGate && (getResurfacedEvents().size() == 0));
        setPassedCriticalErrorGate(checkCriticalGate && getCriticalEvents().size() == 0);
        setPassedTotalErrorGate(checkVolumeGate && (qualityGateReport.getTotalErrorCount() > 0 && qualityGateReport.getTotalErrorCount() < maxEventVolume));
        setPassedUniqueErrorGate(checkUniqueGate && (qualityGateReport.getUniqueErrorCount() > 0 && qualityGateReport.getUniqueErrorCount() < maxUniqueVolume));
        setPassedRegressedEvents(!(checkRegressionGate && regressions != null && regressions.size() > 0));

        String deploymentName = getDeploymentName(input);

        if (isUnstable() && isMarkedUnstable()) {
            // the build is unstable when marking the build as unstable
            // teamcity has no "unstable" status like Jenkins, so we're using "failure"
            setSummary("OverOps has marked build "+ deploymentName + " as \"failure\"."); ;
        } else if (isUnstable() && !isMarkedUnstable()) {
            //unstable build stable when NOT marking the build as unstable
            setSummary("OverOps has detected issues with build "+ deploymentName + " but did not mark the build as \"failure\".");
        } else {
            //stable build when marking the build as unstable
            setSummary("Congratulations, build " + deploymentName + " has passed all quality gates!");
        }

        if (getNewEvents().size() > 0) {
            int count = getNewEvents().size();
            StringBuilder sb = new StringBuilder("New Error Gate: Failed, OverOps detected ");
            sb.append(count);
            sb.append(" new error");
            if (count != 1) {
                sb.append("s");
            }
            sb.append(" in your build.");
            setNewErrorSummary(sb.toString());
        } else if (checkNewGate) {
            setNewErrorSummary("New Error Gate: Passed, OverOps did not detect any new errors in your build.");
        }

        if (getResurfacedEvents().size() > 0) {
            setResurfacedErrorSummary("Resurfaced Error Gate: Failed, OverOps detected " + getResurfacedEvents().size() + " resurfaced errors in your build.");
        } else if (checkResurfacedGate) {
            setResurfacedErrorSummary("Resurfaced Error Gate: Passed, OverOps did not detect any resurfaced errors in your build.");
        }

        if (getCriticalEvents().size() > 0) {
            setCriticalErrorSummary("Critical Error Gate: Failed, OverOps detected " + getCriticalEvents().size() + " critical errors in your build.");
        } else if (checkCriticalGate) {
            setCriticalErrorSummary("Critical Error Gate: Passed, OverOps did not detect any critical errors in your build.");
        }

        long eventVolume = qualityGateReport.getTotalErrorCount();
        if (eventVolume > 0 && eventVolume >= maxEventVolume) {
            setTotalErrorSummary("Total Error Volume Gate: Failed, OverOps detected " + eventVolume + " total errors which is >= the max allowable of " + maxEventVolume);
        } else if (eventVolume > 0 && eventVolume < maxEventVolume) {
            setTotalErrorSummary("Total Error Volume Gate: Passed, OverOps detected " + eventVolume + " total errors which is < than max allowable of " + maxEventVolume);
        }

        long uniqueEventsCount = qualityGateReport.getUniqueErrorCount();
        setHasTopErrors(!(checkVolumeGate && (eventVolume > 0 && eventVolume < maxEventVolume)) 
            || !(checkUniqueGate && (uniqueEventsCount > 0 && uniqueEventsCount < maxUniqueVolume)));


        if (uniqueEventsCount > 0 && uniqueEventsCount >= maxUniqueVolume) {
            setUniqueErrorSummary("Unique Error Volume Gate: Failed, OverOps detected " + uniqueEventsCount + " unique errors which is >= the max allowable of " + maxUniqueVolume);
        } else if (uniqueEventsCount > 0 && uniqueEventsCount < maxUniqueVolume) {
            setUniqueErrorSummary("Unique Error Volume Gate: Passed, OverOps detected " + uniqueEventsCount + " unique errors which is < than max allowable of " + maxUniqueVolume);
        }

        String baselineTime = Objects.nonNull(input) ? input.baselineTime : "";
        if (checkRegressionGate && regressions != null && regressions.size() > 0) {
            setRegressionSummary("Increasing Quality Gate: Failed, OverOps detected increasing errors in the current build against the baseline of " + baselineTime);
        } else {
            setRegressionSummary("Increasing Quality Gate: Passed, OverOps did not detect any increasing errors in the current build against the baseline of " + baselineTime);
        } 

        setNewGateTotal(getNewEvents().size());
        setResurfacedGateTotal(getResurfacedEvents().size());
        setCriticalGateTotal(getCriticalEvents().size());
        setTotalGateTotal(eventVolume);
        setUniqueGateTotal(uniqueEventsCount);
        setRegressionGateTotal(regressions != null ? regressions.size() : 0);

        // hide "total" column in summary table on old reports which don't have this data saved
        setHasTotal(true);   
    }

    private String getDeploymentName(RegressionInput input) {
        if (Objects.nonNull(input) && Objects.nonNull(input.deployments)) {
            String value = input.deployments.toString();
            value = value.replace("[", "");
            value = value.replace("]", "");
            return value;
        }
        return "";
    }

    /**
     * Logic block for handlebars
     *
     * @return boolean
     */
    public boolean getCheckTotalErrorsOrCheckUniqueErrors() {
        return checkTotalErrors || checkUniqueErrors;
    }

    public boolean isUnstable() {
        return unstable;
    }

    public void setUnstable(boolean unstable) {
        this.unstable = unstable;
    }

    public boolean isMarkedUnstable() {
        return markedUnstable;
    }

    public void setMarkedUnstable(boolean markedUnstable) {
        this.markedUnstable = markedUnstable;
    }

    public boolean isPassedNewErrorGate() {
        return passedNewErrorGate;
    }

    public void setPassedNewErrorGate(boolean passedNewErrorGate) {
        this.passedNewErrorGate = passedNewErrorGate;
    }

    public boolean isCheckNewEvents() {
        return checkNewEvents;
    }

    public void setCheckNewEvents(boolean checkNewEvents) {
        this.checkNewEvents = checkNewEvents;
    }

    public boolean isPassedResurfacedErrorGate() {
        return passedResurfacedErrorGate;
    }

    public void setPassedResurfacedErrorGate(boolean passedResurfacedErrorGate) {
        this.passedResurfacedErrorGate = passedResurfacedErrorGate;
    }

    public boolean isCheckResurfacedEvents() {
        return checkResurfacedEvents;
    }

    public void setCheckResurfacedEvents(boolean checkResurfacedEvents) {
        this.checkResurfacedEvents = checkResurfacedEvents;
    }

    public boolean isCheckCriticalErrors() {
        return checkCriticalErrors;
    }

    public void setCheckCriticalErrors(boolean checkCriticalErrors) {
        this.checkCriticalErrors = checkCriticalErrors;
    }

    public boolean isPassedCriticalErrorGate() {
        return passedCriticalErrorGate;
    }

    public void setPassedCriticalErrorGate(boolean passedCriticalErrorGate) {
        this.passedCriticalErrorGate = passedCriticalErrorGate;
    }

    public boolean isCheckTotalErrors() {
        return checkTotalErrors;
    }

    public void setCheckTotalErrors(boolean checkTotalErrors) {
        this.checkTotalErrors = checkTotalErrors;
    }

    public boolean isPassedTotalErrorGate() {
        return passedTotalErrorGate;
    }

    public void setPassedTotalErrorGate(boolean passedTotalErrorGate) {
        this.passedTotalErrorGate = passedTotalErrorGate;
    }

    public boolean isCheckUniqueErrors() {
        return checkUniqueErrors;
    }

    public void setCheckUniqueErrors(boolean checkUniqueErrors) {
        this.checkUniqueErrors = checkUniqueErrors;
    }

    public boolean isHasTopErrors() {
        return hasTopErrors;
    }

    public void setHasTopErrors(boolean hasTopErrors) {
        this.hasTopErrors = hasTopErrors;
    }

    public boolean isPassedUniqueErrorGate() {
        return passedUniqueErrorGate;
    }

    public void setPassedUniqueErrorGate(boolean passedUniqueErrorGate) {
        this.passedUniqueErrorGate = passedUniqueErrorGate;
    }

    public boolean isCheckRegressedErrors() {
        return checkRegressedErrors;
    }

    public void setCheckRegressedErrors(boolean checkRegressedErrors) {
        this.checkRegressedErrors = checkRegressedErrors;
    }

    public boolean isPassedRegressedEvents() {
        return passedRegressedEvents;
    }

    public void setPassedRegressedEvents(boolean passedRegressedEvents) {
        this.passedRegressedEvents = passedRegressedEvents;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getNewErrorSummary() {
        return newErrorSummary;
    }

    public void setNewErrorSummary(String newErrorSummary) {
        this.newErrorSummary = newErrorSummary;
    }

    public String getResurfacedErrorSummary() {
        return resurfacedErrorSummary;
    }

    public void setResurfacedErrorSummary(String resurfacedErrorSummary) {
        this.resurfacedErrorSummary = resurfacedErrorSummary;
    }

    public String getCriticalErrorSummary() {
        return criticalErrorSummary;
    }

    public void setCriticalErrorSummary(String criticalErrorSummary) {
        this.criticalErrorSummary = criticalErrorSummary;
    }

    public String getTotalErrorSummary() {
        return totalErrorSummary;
    }

    public void setTotalErrorSummary(String totalErrorSummary) {
        this.totalErrorSummary = totalErrorSummary;
    }

    public String getUniqueErrorSummary() {
        return uniqueErrorSummary;
    }

    public void setUniqueErrorSummary(String uniqueErrorSummary) {
        this.uniqueErrorSummary = uniqueErrorSummary;
    }

    public String getRegressionSummary() {
        return regressionSummary;
    }

    public void setRegressionSummary(String regressionSummary) {
        this.regressionSummary = regressionSummary;
    }

    public List<EventVisualizationModel> getNewEvents() {
        return newEvents;
    }

    public void setNewEvents(List<EventVisualizationModel> newEvents) {
        this.newEvents = newEvents;
    }

    public List<EventVisualizationModel> getResurfacedEvents() {
        return resurfacedEvents;
    }

    public void setResurfacedEvents(List<EventVisualizationModel> resurfacedEvents) {
        this.resurfacedEvents = resurfacedEvents;
    }

    public List<EventVisualizationModel> getCriticalEvents() {
        return criticalEvents;
    }

    public void setCriticalEvents(List<EventVisualizationModel> criticalEvents) {
        this.criticalEvents = criticalEvents;
    }

    public List<EventVisualizationModel> getTopEvents() {
        return topEvents;
    }

    public void setTopEvents(List<EventVisualizationModel> topEvents) {
        this.topEvents = topEvents;
    }

    public List<EventVisualizationModel> getRegressedEvents() {
        return regressedEvents;
    }

    public void setRegressedEvents(List<EventVisualizationModel> regressedEvents) {
        this.regressedEvents = regressedEvents;
    }

    public boolean isHasTotal() {
        return hasTotal;
    }

    public void setHasTotal(boolean hasTotal) {
        this.hasTotal = hasTotal;
    }

    public long getNewGateTotal() {
        return newGateTotal;
    }

    public void setNewGateTotal(long newGateTotal) {
        this.newGateTotal = newGateTotal;
    }

    public long getResurfacedGateTotal() {
        return resurfacedGateTotal;
    }

    public void setResurfacedGateTotal(long resurfacedGateTotal) {
        this.resurfacedGateTotal = resurfacedGateTotal;
    }

    public long getCriticalGateTotal() {
        return criticalGateTotal;
    }

    public void setCriticalGateTotal(long criticalGateTotal) {
        this.criticalGateTotal = criticalGateTotal;
    }

    public long getTotalGateTotal() {
        return totalGateTotal;
    }

    public void setTotalGateTotal(long totalGateTotal) {
        this.totalGateTotal = totalGateTotal;
    }

    public long getUniqueGateTotal() {
        return uniqueGateTotal;
    }

    public void setUniqueGateTotal(long uniqueGateTotal) {
        this.uniqueGateTotal = uniqueGateTotal;
    }

    public long getRegressionGateTotal() {
        return regressionGateTotal;
    }

    public void setRegressionGateTotal(long regressionGateTotal) {
        this.regressionGateTotal = regressionGateTotal;
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    public boolean isHasException() {
        return hasException;
    }

    public void setHasException(boolean hasException) {
        this.hasException = hasException;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public void setExceptionMessage(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    public String getEmailMessage() {
        return emailMessage;
    }

    public void setEmailMessage(String emailMessage) {
        this.emailMessage = emailMessage;
    }

    public WebAssets getAssets() {
        return assets;
    }

    public void setAssets(WebAssets assets) {
        this.assets = assets;
    }
}