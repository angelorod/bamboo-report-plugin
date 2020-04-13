package com.overops.bamboo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class ReportGeneratorServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private Gson gson = new GsonBuilder().create();

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        String endPoint = null;
        String apiKey = null; 
        String sid = null;
        // String query = "{applicationName: application1, deploymentName: deployment1, debug: true, newEvents: true, resurfacedErrors: true}";

        String query = request.getParameter("query");
        QualityReportParams reportParams = gson.fromJson(query, QualityReportParams.class);
        reportParams.setServiceId(sid);

        String output;
        try {
            output = QualityReportHTMLGenerator.generateHTML(endPoint, apiKey, sid, reportParams);
        } catch (Exception e) {
            output = e.getMessage();
        }

        PrintWriter out = response.getWriter();
        out.println(output);
        out.close();
    }
}
