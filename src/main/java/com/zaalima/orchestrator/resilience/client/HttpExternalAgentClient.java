package com.zaalima.orchestrator.resilience.client;

import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.resilience.ResilienceManager;
import com.zaalima.orchestrator.resilience.ResilientOperationExecutor;
import com.zaalima.orchestrator.resilience.exception.ExternalApiException;
import com.zaalima.orchestrator.resilience.exception.ExternalApiValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP implementation of ExternalAgentClient using Java 21 java.net.http.HttpClient.
 * Configured with finite connection and request timeouts, executed via ResilientOperationExecutor.
 */
@Component
public class HttpExternalAgentClient implements ExternalAgentClient {

    private static final Logger log = LoggerFactory.getLogger(HttpExternalAgentClient.class);

    private final HttpClient httpClient;
    private final ResilienceManager resilienceManager;
    private final ResilientOperationExecutor resilientExecutor;

    public HttpExternalAgentClient(
            ResilienceManager resilienceManager,
            ResilientOperationExecutor resilientExecutor,
            HttpClient httpClient) {
        this.resilienceManager = Objects.requireNonNull(resilienceManager, "ResilienceManager must not be null");
        this.resilientExecutor = Objects.requireNonNull(resilientExecutor, "ResilientOperationExecutor must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "HttpClient must not be null");
    }

    public HttpExternalAgentClient(
            ResilienceManager resilienceManager,
            ResilientOperationExecutor resilientExecutor) {
        this(
                resilienceManager,
                resilientExecutor,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(
                                resilienceManager.getProperties().getTimeout().getConnectTimeoutMs()))
                        .build()
        );
    }

    @Override
    public ExternalAgentResponse sendRequest(ExternalAgentRequest request) {
        Objects.requireNonNull(request, "ExternalAgentRequest must not be null");

        try {
            return resilientExecutor.execute(ResilienceManager.DEFAULT_INSTANCE, () -> {
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(request.endpointUrl()))
                        .timeout(Duration.ofMillis(
                                resilienceManager.getProperties().getTimeout().getRequestTimeoutMs()));

                for (Map.Entry<String, String> header : request.headers().entrySet()) {
                    reqBuilder.header(header.getKey(), header.getValue());
                }

                String payload = request.payload() != null ? request.payload() : "";
                if ("GET".equalsIgnoreCase(request.method())) {
                    reqBuilder.GET();
                } else if ("PUT".equalsIgnoreCase(request.method())) {
                    reqBuilder.PUT(HttpRequest.BodyPublishers.ofString(payload));
                } else if ("DELETE".equalsIgnoreCase(request.method())) {
                    reqBuilder.DELETE();
                } else {
                    reqBuilder.POST(HttpRequest.BodyPublishers.ofString(payload));
                    if (!request.headers().containsKey("Content-Type")) {
                        reqBuilder.header("Content-Type", "application/json");
                    }
                }

                HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status >= 200 && status < 300) {
                    return ExternalAgentResponse.success(status, response.body());
                } else if (status >= 400 && status < 500) {
                    throw new ExternalApiValidationException(status,
                            "Client error from external agent (status " + status + "): " + response.body());
                } else {
                    throw new ExternalApiException(status,
                            "Server error from external agent (status " + status + "): " + response.body());
                }
            });
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new ExternalApiException(500, "Failed to execute external agent request: " + e.getMessage(), e);
        }
    }

    @Override
    public ExternalAgentResponse executeTask(String agentId, AgentTask task) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(task, "task must not be null");

        String endpoint = "http://localhost:8081/agents/" + agentId + "/tasks";
        ExternalAgentRequest request = ExternalAgentRequest.of(agentId, endpoint, task.getPayload());
        return sendRequest(request);
    }
}
