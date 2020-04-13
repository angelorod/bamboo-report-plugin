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


    public static String generateHTML(String pathToReportVisualizationModelJson) {
        ReportService reportService = new ReportService();
        ReportVisualizationModel visualizationModel = reportService.createReportVisualizationModel(pathToReportVisualizationModelJson);
        return renderReport(visualizationModel);
    }

    public static String generateHTML(String endPoint, String apiKey, String sid, QualityReportParams reportParams) {
        ReportService reportService = new ReportService();
        ReportVisualizationModel visualizationModel = reportService.createReportVisualizationModel(endPoint, apiKey, sid, reportParams);
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

}