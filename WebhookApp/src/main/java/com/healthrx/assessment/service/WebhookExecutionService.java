package com.healthrx.assessment.service;

import com.healthrx.assessment.model.SubmissionRequest;
import com.healthrx.assessment.model.WebhookRequest;
import com.healthrx.assessment.model.WebhookResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class WebhookExecutionService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(WebhookExecutionService.class);
    private final RestTemplate restTemplate;

    public WebhookExecutionService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private static final String GENERATE_WEBHOOK_URL = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";

    
    private static final String STUDENT_REG_NO = "250850120068";
    private static final String STUDENT_NAME = "E.Sampath Kumar";
    private static final String STUDENT_EMAIL = "e.sampathkumar.24@gmail.com";


   


    private static final String QUERY_2 = """
            WITH EmpSalaries AS (
                SELECT
                    p.EMP_ID,
                    SUM(p.AMOUNT) AS TotalSal
                FROM PAYMENTS p
                GROUP BY p.EMP_ID
                HAVING SUM(p.AMOUNT) > 70000
            ),
            HighEarners AS (
                SELECT
                    e.EMP_ID,
                    e.DEPARTMENT,
                    e.FIRST_NAME,
                    e.LAST_NAME,
                    DATEDIFF(YEAR, e.DOB, GETDATE()) AS Age
                FROM EMPLOYEE e
                JOIN EmpSalaries es ON e.EMP_ID = es.EMP_ID
            ),
            DeptStats AS (
                SELECT
                    DEPARTMENT,
                    AVG(Age) AS AVERAGE_AGE
                FROM HighEarners
                GROUP BY DEPARTMENT
            ),
            Top10Names AS (
                SELECT
                    DEPARTMENT,
                    FIRST_NAME,
                    LAST_NAME,
                    ROW_NUMBER() OVER (PARTITION BY DEPARTMENT ORDER BY FIRST_NAME, LAST_NAME) AS rn
                FROM HighEarners
            )
            SELECT
                d.DEPARTMENT_NAME,
                ds.AVERAGE_AGE,
                STRING_AGG(t.FIRST_NAME + ' ' + t.LAST_NAME, ', ') WITHIN GROUP (ORDER BY t.FIRST_NAME, t.LAST_NAME) AS EMPLOYEE_LIST
            FROM DEPARTMENT d
            JOIN DeptStats ds ON d.DEPARTMENT_ID = ds.DEPARTMENT
            LEFT JOIN Top10Names t ON d.DEPARTMENT_ID = t.DEPARTMENT AND t.rn <= 10
            GROUP BY d.DEPARTMENT_ID, d.DEPARTMENT_NAME, ds.AVERAGE_AGE
            ORDER BY d.DEPARTMENT_ID DESC;
            """;

    @Override
    public void run(String... args) {
        try {
            log.info("Starting Webhook Execution Flow...");

          
            log.info("Generating Webhook for RegNo: {}", STUDENT_REG_NO);
            WebhookRequest webhookRequest = new WebhookRequest(STUDENT_NAME, STUDENT_REG_NO, STUDENT_EMAIL);
            WebhookResponse response = generateWebhook(webhookRequest);

            if (response == null || response.getWebhookUrl() == null) {
                log.error("Failed to retrieve Webhook URL. Exiting.");
                return;
            }

            String webhookUrl = response.getWebhookUrl();
            String accessToken = response.getAccessToken();
            log.info("Received Webhook URL: {}", webhookUrl);
            log.info("Received Access Token: [HIDDEN]");

          
           
            char lastChar = STUDENT_REG_NO.charAt(STUDENT_REG_NO.length() - 1);
            int lastDigit = Character.getNumericValue(lastChar);

            String finalQuery;
               
                log.info("RegNo ends in Even digit ({}). Selecting Query 2.", lastDigit);
                finalQuery = QUERY_2;
           
            submitSolution(webhookUrl, accessToken, finalQuery);

        } catch (Exception e) {
            log.error("An error occurred during execution: ", e);
        }
    }

    private WebhookResponse generateWebhook(WebhookRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<WebhookRequest> entity = new HttpEntity<>(request, headers);

        try {
           
            ResponseEntity<String> responseRaw = restTemplate.postForEntity(GENERATE_WEBHOOK_URL, entity, String.class);
            log.info("RAW RESPONSE FROM GENERATE WEBHOOK: {}", responseRaw.getBody());

            if (responseRaw.getBody() == null) {
                return null;
            }

            
            String body = responseRaw.getBody();
            WebhookResponse response = new WebhookResponse();

            
            response.setWebhookUrl(extractJsonValue(body, "webhook"));
            response.setAccessToken(extractJsonValue(body, "accessToken"));

            return response;
        } catch (Exception e) {
            log.error("Error calling generateWebhook API: {}", e.getMessage());
            throw e;
        }
    }

    private String extractJsonValue(String json, String key) {
        try {
            String search = "\"" + key + "\":";
            int start = json.indexOf(search);
            if (start == -1)
                return null;

            start += search.length();

           
            int valStart = json.indexOf("\"", start);
            if (valStart == -1)
                return null;
            valStart++; 

            int valEnd = json.indexOf("\"", valStart);
            if (valEnd == -1)
                return null;

            return json.substring(valStart, valEnd);
        } catch (Exception e) {
            return null;
        }
    }

    private void submitSolution(String url, String token, String query) {
        SubmissionRequest request = new SubmissionRequest(query);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", token);
        headers.set("User-Agent", "Java/Spring-Boot-Client");

        HttpEntity<SubmissionRequest> entity = new HttpEntity<>(request, headers);

        try {
            log.info("Submitting solution to: {}", url);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            log.info("Submission Response Code: {}", response.getStatusCode());
            log.info("Submission Response Body: {}", response.getBody());
        } catch (Exception e) {
            log.error("Error submitting solution: {}", e.getMessage());
        }
    }
}
