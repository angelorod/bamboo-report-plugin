package com.overops.bamboo;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.overops.bamboo.model.OOReportRegressedEvent;
import com.overops.bamboo.model.ReportVisualizationModel;
import com.overops.bamboo.model.WebAssets;
import com.takipi.api.client.ApiClient;
import com.takipi.api.client.RemoteApiClient;
import com.takipi.api.client.data.view.SummarizedView;
import com.takipi.api.client.observe.Observer;
import com.takipi.api.client.result.event.EventResult;
import com.takipi.api.client.util.cicd.OOReportEvent;
import com.takipi.api.client.util.cicd.ProcessQualityGates;
import com.takipi.api.client.util.cicd.QualityGateReport;
import com.takipi.api.client.util.regression.RateRegression;
import com.takipi.api.client.util.regression.RegressionInput;
import com.takipi.api.client.util.regression.RegressionResult;
import com.takipi.api.client.util.regression.RegressionStringUtil;
import com.takipi.api.client.util.regression.RegressionUtil;
import com.takipi.api.client.util.view.ViewUtil;

import org.apache.commons.io.IOUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

public class QualityReportHTMLGenerator {
    public static void main(String[] args) {
        String endPoint = null;
        String apiKey = null;
        String sid = null;

        QualityReportParams reportParams = new QualityReportParams();
        reportParams.setApplicationName("application1");
        reportParams.setDeploymentName("deployment1");
        reportParams.setDebug(true);
        reportParams.setNewEvents(true);
        reportParams.setResurfacedErrors(true);

        try {
            // String html = QualityReportHTMLGenerator.generateHTML(endPoint, apiKey, sid, reportParams);
            String html = QualityReportHTMLGenerator.generateHTML("reportVisualizationModel.json");
            BufferedWriter writer = new BufferedWriter(new FileWriter("myReport.html"));
            writer.write(html);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static Gson gson = new Gson();


    public static String generateHTML(String pathToReportVisualizationModelJson) {
        ReportVisualizationModel visualizationModel = createReportVisualizationModel(pathToReportVisualizationModelJson);
        return renderReport(visualizationModel);
    }

    public static String generateHTML(String endPoint, String apiKey, String sid, QualityReportParams reportParams) {
        ReportVisualizationModel visualizationModel = createReportVisualizationModel(endPoint, apiKey, sid, reportParams);
        return renderReport(visualizationModel);
    }

    private static String renderReport(ReportVisualizationModel visualizationModel) {
        VelocityEngine ve = new VelocityEngine();
        ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath"); 
        ve.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        ve.init();
        Template template = ve.getTemplate("template/main.vm");
        VelocityContext context = new VelocityContext();
        context.put("model", visualizationModel);
        StringWriter writer = new StringWriter();
        template.merge(context, writer);
        return writer.toString();
    }

    // for each event, replace the source ID in the ARC link with 58 (which means TeamCity)
    // see: https://overopshq.atlassian.net/wiki/spaces/PP/pages/1529872385/Hit+Sources
    private static void replaceSourceId(List<? extends OOReportEvent> events) {
        String match = "&source=(\\d)+";  // matches at least one digit
        String replace = "&source=58";    // replace with 58

        for (OOReportEvent event : events) {
            String arcLink = event.getARCLink().replaceAll(match, replace);
            event.setArcLink(arcLink);
        }
    }

    protected static class ApiClientObserver implements Observer {

        private final PrintStream printStream;
        private final boolean verbose;

        public ApiClientObserver(PrintStream printStream, boolean verbose) {
            this.printStream = printStream;
            this.verbose = verbose;
        }

        @Override
        public void observe(Operation operation, String url, String request, String response, int responseCode, long time) {
            StringBuilder output = new StringBuilder();

            output.append(String.valueOf(operation));
            output.append(" took ");
            output.append(time / 1000);
            output.append("ms for ");
            output.append(url);

            if (verbose) {
                output.append(". Response: ");
                output.append(response);
            }

            printStream.println(output.toString());
        }
    }

    private static List<OOReportRegressedEvent> getAllRegressions(ApiClient apiClient, RegressionInput input, RateRegression rateRegression, Collection<EventResult> filter) {

        List<OOReportRegressedEvent> result = new ArrayList<OOReportRegressedEvent>();

        for (RegressionResult regressionResult : rateRegression.getCriticalRegressions().values()) {

        if ((filter != null) && (!filter.contains(regressionResult.getEvent()))) {
            continue;
        }

        String arcLink = ProcessQualityGates.getArcLink(apiClient, regressionResult.getEvent().id, input, rateRegression.getActiveWndowStart());

        OOReportRegressedEvent regressedEvent = new OOReportRegressedEvent(regressionResult.getEvent(), regressionResult.getBaselineHits(), regressionResult.getBaselineInvocations(), RegressionStringUtil.SEVERE_REGRESSION, arcLink);

        result.add(regressedEvent);
        }

        for (RegressionResult regressionResult : rateRegression.getAllRegressions().values()) {

            if (rateRegression.getCriticalRegressions().containsKey(regressionResult.getEvent().id)) {
                continue;
            }

            String arcLink = ProcessQualityGates.getArcLink(apiClient, regressionResult.getEvent().id, input, rateRegression.getActiveWndowStart());

            OOReportRegressedEvent regressedEvent = new OOReportRegressedEvent(regressionResult.getEvent(),
            regressionResult.getBaselineHits(), regressionResult.getBaselineInvocations(), RegressionStringUtil.REGRESSION, arcLink);

            result.add(regressedEvent);
        }

        return result;
    }

    private static boolean allowEvent(EventResult event, Pattern pattern) {
        if (pattern == null) {
            return true;
        }

        String json = gson.toJson(event);
        boolean result = !pattern.matcher(json).find();

        return result;
    }

    private static void addEvent(Set<EventResult> events, EventResult event, Pattern pattern, PrintStream output, boolean verbose) {
        if (allowEvent(event, pattern)) {
            events.add(event);
        } else if ((output != null) && (verbose)) {
            output.println(event + " did not match regexFilter and was skipped");
        }
    }

    private static Collection<EventResult> filterEvents(RateRegression rateRegression, Pattern pattern, PrintStream output, boolean verbose) {
        Set<EventResult> result = new HashSet<>();
        if (pattern != null) {

            for (EventResult event : rateRegression.getNonRegressions()) {
                addEvent(result, event, pattern, output, verbose);
            }

            for (EventResult event : rateRegression.getAllNewEvents().values()) {
                addEvent(result, event, pattern, output, verbose);
            }

            for (RegressionResult regressionResult : rateRegression.getAllRegressions().values()) {
                addEvent(result, regressionResult.getEvent(), pattern, output, verbose);

            }

        } else {
            result.addAll(rateRegression.getNonRegressions());
            result.addAll(rateRegression.getAllNewEvents().values());

            for (RegressionResult regressionResult : rateRegression.getAllRegressions().values()) {
                result.add(regressionResult.getEvent());
            }
        }

        return result;
    }

    private static List<EventResult> getSortedEventsByVolume(Collection<EventResult> events) {
        List<EventResult> result = new ArrayList<EventResult>(events);

        result.sort((o1, o2) -> {
            long v1;
            long v2;

            if (o1.stats != null) {
                v1 = o1.stats.hits;
            } else {
                v1 = 0;
            }

            if (o2.stats != null) {
                v2 = o2.stats.hits;
            } else {
                v2 = 0;
            }

            return (int) (v2 - v1);
        });

        return result;
    }

    private static ReportVisualizationModel createReportVisualizationModel(String pathToReportVisualizationModelJson) {
        ClassLoader cl = QualityReportHTMLGenerator.class.getClassLoader();
        URL[] urls = ((URLClassLoader)cl).getURLs();

        for(URL url: urls){
        	System.out.println(url.getFile());
        }

        InputStream stream = QualityReportHTMLGenerator.class.getClassLoader().getResourceAsStream(pathToReportVisualizationModelJson);
        Reader reader = new InputStreamReader(stream);
        int intValueOfChar;
        String json = "";
        try {
            while ((intValueOfChar = reader.read()) != -1) {
                json += (char) intValueOfChar;
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return gson.fromJson(json, ReportVisualizationModel.class);
    }


    private static ReportVisualizationModel createReportVisualizationModel(String endPoint, String apiKey, String sid, QualityReportParams reportParams) {
        PrintStream printStream = System.out;

        boolean runRegressions = convertToMinutes(reportParams.getBaselineTimespan()) > 0;

        validateInputs(endPoint, apiKey, sid, reportParams);

        RemoteApiClient apiClient = (RemoteApiClient) RemoteApiClient.newBuilder().setHostname(endPoint).setApiKey(apiKey).build();

        if (Objects.nonNull(printStream) && (reportParams.isDebug())) {
            apiClient.addObserver(new ApiClientObserver(printStream, reportParams.isDebug()));
        }

        SummarizedView allEventsView = ViewUtil.getServiceViewByName(apiClient, sid.toUpperCase(), "All Events");

        if (Objects.isNull(allEventsView)) {
            throw new IllegalStateException(
                    "Could not acquire ID for 'All Events'. Please check connection to " + endPoint);
        }

        RegressionInput input = new RegressionInput();
        input.serviceId = sid;
        input.viewId = allEventsView.id;
        input.applictations = parseArrayString(reportParams.getApplicationName(), printStream, "Application Name");
        input.deployments = parseArrayString(reportParams.getDeploymentName(), printStream, "Deployment Name");
        input.criticalExceptionTypes = parseArrayString(reportParams.getCriticalExceptionTypes(), printStream, "Critical Exception Types");

        if (runRegressions) {
            input.activeTimespan = convertToMinutes(reportParams.getActiveTimespan());
            input.baselineTime = reportParams.getBaselineTimespan();
            input.baselineTimespan = convertToMinutes(reportParams.getBaselineTimespan());
            input.minVolumeThreshold = reportParams.getMinVolumeThreshold();
            input.minErrorRateThreshold = reportParams.getMinErrorRateThreshold();
            input.regressionDelta = reportParams.getRegressionDelta();
            input.criticalRegressionDelta = reportParams.getCriticalRegressionDelta();
            input.applySeasonality = reportParams.isApplySeasonality();
            input.validate();
        }

        int maxEventVolume = reportParams.getMaxErrorVolume();
        int maxUniqueErrors = reportParams.getMaxUniqueErrors();
        boolean newEvents = reportParams.isNewEvents();
        boolean resurfacedEvents = reportParams.isResurfacedErrors();
        String regexFilter = reportParams.getRegexFilter();
        int topEventLimit = reportParams.getPrintTopIssues();
        PrintStream output = printStream;
        boolean verbose = reportParams.isDebug();

        //check if total or unique gates are being tested
        boolean countGate = false;
        if (maxEventVolume != 0 || maxUniqueErrors != 0) {
            countGate = true;
        }
        boolean checkMaxEventGate = maxEventVolume != 0;
        boolean checkUniqueEventGate = maxUniqueErrors != 0;

        //get the CICD quality report for all gates but Regressions
        //initialize the QualityGateReport so we don't get null pointers below
        QualityGateReport qualityGateReport = new QualityGateReport();
        if (countGate || newEvents || resurfacedEvents || regexFilter != null) {
            qualityGateReport = ProcessQualityGates.processCICDInputs(apiClient, input, newEvents, resurfacedEvents,
                    regexFilter, topEventLimit, countGate, output, verbose);
        }

        //run the regression gate
        RateRegression rateRegression = null;
        List<OOReportRegressedEvent> regressions = null;
        boolean hasRegressions = false;
        if (runRegressions) {
            rateRegression = RegressionUtil.calculateRateRegressions(apiClient, input, output, verbose);

            List<OOReportEvent> topEvents = new ArrayList<>();
            Collection<EventResult> filter = null;
    
            Pattern pattern;
    
            if ((regexFilter != null) && (regexFilter.length() > 0)) {
                pattern = Pattern.compile(regexFilter);
            } else {
                pattern = null;
            }
    
            Collection<EventResult> eventsSet = filterEvents(rateRegression, pattern, output, verbose);
            List<EventResult> events = getSortedEventsByVolume(eventsSet);
    
            if (pattern != null) {
                filter = eventsSet;
            }
    
            for (EventResult event : events) {
                if (event.stats != null) {
                    if (topEvents.size() < topEventLimit) {
                        String arcLink = ProcessQualityGates.getArcLink(apiClient, event.id, input, rateRegression.getActiveWndowStart());
                        topEvents.add(new OOReportEvent(event, arcLink));
                    }
                }
            }
    
            regressions = getAllRegressions(apiClient, input, rateRegression, filter);
            if (regressions != null && regressions.size() > 0) {
                hasRegressions = true;
                replaceSourceId(regressions);
            }
        }

        //max total error gate
        boolean maxVolumeExceeded = (maxEventVolume != 0) && (qualityGateReport.getTotalErrorCount() > maxEventVolume);

        //max unique error gate
        long uniqueEventCount;
        boolean maxUniqueErrorsExceeded;
        if (maxUniqueErrors != 0) {
            uniqueEventCount = qualityGateReport.getUniqueErrorCount();
            maxUniqueErrorsExceeded = uniqueEventCount > maxUniqueErrors;
        } else {
            uniqueEventCount = 0;
            maxUniqueErrorsExceeded = false;
        }

        //new error gate
        boolean newErrors = false;
        if (qualityGateReport.getNewErrors() != null && qualityGateReport.getNewErrors().size() > 0) {
            newErrors = true;
            replaceSourceId(qualityGateReport.getNewErrors());
        }

        //resurfaced error gate
        boolean resurfaced = false;
        if (qualityGateReport.getResurfacedErrors() != null && qualityGateReport.getResurfacedErrors().size() > 0) {
            resurfaced = true;
            replaceSourceId(qualityGateReport.getResurfacedErrors());
        }

        //critical error gate
        boolean critical = false;
        if (qualityGateReport.getCriticalErrors() != null && qualityGateReport.getCriticalErrors().size() > 0) {
            critical = true;
            replaceSourceId(qualityGateReport.getCriticalErrors());
        }

        //top errors
        if (qualityGateReport.getTopErrors() != null && qualityGateReport.getTopErrors().size() > 0) {
            replaceSourceId(qualityGateReport.getTopErrors());
        }

        boolean checkCritical = false;
        if (input.criticalExceptionTypes != null && input.criticalExceptionTypes.size() > 0) {
            checkCritical = true;
        }

        boolean unstable = (hasRegressions)
                || (maxVolumeExceeded)
                || (maxUniqueErrorsExceeded)
                || (newErrors)
                || (resurfaced)
                || (critical);

        ReportVisualizationModel visualizationModel = new ReportVisualizationModel(qualityGateReport, input, rateRegression, regressions, unstable, newEvents, resurfacedEvents, checkCritical, checkMaxEventGate, checkUniqueEventGate, runRegressions, maxEventVolume, maxUniqueErrors, reportParams.isMarkUnstable());

        injectWebAssets(visualizationModel);

        return visualizationModel;

    }

    private static Collection<String> parseArrayString(String value, PrintStream printStream, String name) {
        if (isEmptyString(value)) {
            return Collections.emptySet();
        }

        return Arrays.asList(value.trim().split(Pattern.quote(",")));
    }

    private static int convertToMinutes(String timeWindow) {
        if (isEmptyString(timeWindow)) {
            return 0;
        }

        if (timeWindow.toLowerCase().contains("d")) {
            int days = Integer.parseInt(timeWindow.substring(0, timeWindow.indexOf("d")));
            return days * 24 * 60;
        } else if (timeWindow.toLowerCase().contains("h")) {
            int hours = Integer.parseInt(timeWindow.substring(0, timeWindow.indexOf("h")));
            return hours * 60;
        } else if (timeWindow.toLowerCase().contains("m")) {
            return Integer.parseInt(timeWindow.substring(0, timeWindow.indexOf("m")));
        }

        return 0;
    }

    private static boolean isEmptyString(String aString) {
        return (aString == null) || (aString.trim().length() == 0);
    }

    private static void injectWebAssets(ReportVisualizationModel model) {
        WebAssets assets = model.getAssets();
        assets.setStyle(webResourceToString("/web/css/style.css"));
        assets.setOveropsLogo(webResourceToString("/web/img/overops-logo.svg"));
        assets.setIcnDanger(webResourceToString("/web/img/icn-danger.svg"));
        assets.setIcnQuestion(webResourceToString("/web/img/icn-question.svg"));
        assets.setIcnSuccess(webResourceToString("/web/img/icn-success.svg"));
        assets.setIcnTimes(webResourceToString("/web/img/icn-times.svg"));
        assets.setIcnWarning(webResourceToString("/web/img/icn-warning.svg"));
    }

    private static String webResourceToString(String location) {
        try {
            InputStream overopsLogo = null; //this.getServletContext().getClassLoader().getResourceAsStream(location);
            return IOUtils.toString(overopsLogo, Charset.defaultCharset());
        } catch (Exception e) {
            return "";
        }
    }

    private static void validateInputs(String endPoint, String apiKey, String sid, QualityReportParams reportParams) {

        if (isEmptyString(endPoint)) {
            throw new IllegalArgumentException("Missing host name");
        }

        if (isEmptyString(apiKey)) {
            throw new IllegalArgumentException("Missing api key");
        }

        if (reportParams.isCheckRegressionErrors()) {
            if (!"0".equalsIgnoreCase(reportParams.getActiveTimespan())) {
                if (convertToMinutes(reportParams.getActiveTimespan()) == 0) {
                    throw new IllegalArgumentException("For Increasing Error Gate, the active timewindow currently set to: " + reportParams.getActiveTimespan() + " is not properly formated. See help for format instructions.");
                }
            }
            if (!"0".equalsIgnoreCase(reportParams.getBaselineTimespan())) {
                if (convertToMinutes(reportParams.getBaselineTimespan()) == 0) {
                    throw new IllegalArgumentException("For Increasing Error Gate, the baseline timewindow currently set to: " + reportParams.getBaselineTimespan() + " cannot be zero or is improperly formated. See help for format instructions.");
                }
            }
        }

        if (isEmptyString(sid)) {
            throw new IllegalArgumentException("Missing environment Id");
        }

    }
}