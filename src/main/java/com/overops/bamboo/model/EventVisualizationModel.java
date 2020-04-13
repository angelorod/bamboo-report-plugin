package com.overops.bamboo.model;

import com.takipi.api.client.util.cicd.OOReportEvent;

public class EventVisualizationModel {

    private String arcLink;
    private String type;
    private String applications;
    private String introducedBy;
    private String eventSummary;
    private String eventRate;
    private long hits;
    private long calls;


    public EventVisualizationModel(OOReportEvent event) {
        this.arcLink = event.getARCLink();
        this.type = event.getType();
        this.introducedBy = event.getIntroducedBy();
        this.eventSummary = event.getEventSummary();
        this.eventRate = event.getEventRate();
        this.hits = event.getHits();
        this.calls = event.getCalls();
        this.applications = event.getApplications();
    }

    public String getArcLink() {
        return arcLink;
    }

    public void setArcLink(String arcLink) {
        this.arcLink = arcLink;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getApplications() {
        return applications;
    }

    public void setApplications(String applications) {
        this.applications = applications;
    }

    public String getIntroducedBy() {
        return introducedBy;
    }

    public void setIntroducedBy(String introducedBy) {
        this.introducedBy = introducedBy;
    }

    public String getEventSummary() {
        return eventSummary;
    }

    public void setEventSummary(String eventSummary) {
        this.eventSummary = eventSummary;
    }

    public String getEventRate() {
        return eventRate;
    }

    public void setEventRate(String eventRate) {
        this.eventRate = eventRate;
    }

    public long getHits() {
        return hits;
    }

    public void setHits(long hits) {
        this.hits = hits;
    }

    public long getCalls() {
        return calls;
    }

    public void setCalls(long calls) {
        this.calls = calls;
    }      
}