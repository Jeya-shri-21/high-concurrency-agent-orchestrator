package com.zaalima.orchestrator.resilience;

import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.resilience.client.ExternalAgentClient;
import com.zaalima.orchestrator.resilience.client.ExternalAgentRequest;
import com.zaalima.orchestrator.resilience.client.ExternalAgentResponse;
import com.zaalima.orchestrator.resilience.client.HttpExternalAgentClient;
import com.zaalima.orchestrator.resilience.config.ResilienceProperties;
import com.zaalima.orchestrator.resilience.exception.ExternalApiException;
import com.zaalima.orchestrator.resilience.exception.ExternalApiTimeoutException;
import com.zaalima.orchestrator.resilience.exception.ExternalApiValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("External Agent Client HTTP Boundary Tests")
class ExternalAgentClientTest {

    private HttpClient mockHttpClient;
    private ResilienceManager resilienceManager;
    private ResilientOperationExecutor executor;
    private ExternalAgentClient client;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockHttpClient = mock(HttpClient.class);
        ResilienceProperties properties = new ResilienceProperties();
        resilienceManager = new ResilienceManager(properties);
        executor = new ResilientOperationExecutor(resilienceManager);
        client = new HttpExternalAgentClient(resilienceManager, executor, mockHttpClient);
    }

    @Test
    @DisplayName("200 OK from external agent returns successful ExternalAgentResponse")
    @SuppressWarnings("unchecked")
    void shouldSendSuccessfulRequestWhenRemoteReturns200() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"status\":\"success\",\"result\":42}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        ExternalAgentRequest request = ExternalAgentRequest.of("agent-1", "http://localhost:8081/test", "{\"action\":\"compute\"}");
        ExternalAgentResponse response = client.sendRequest(request);

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("success"));
    }

    @Test
    @DisplayName("400 Bad Request throws ExternalApiValidationException")
    @SuppressWarnings("unchecked")
    void shouldThrowExternalApiValidationExceptionWhenRemoteReturns400() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(400);
        when(mockResponse.body()).thenReturn("{\"error\":\"Invalid payload\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        ExternalAgentRequest request = ExternalAgentRequest.of("agent-1", "http://localhost:8081/test", "bad-payload");

        ExternalApiValidationException ex = assertThrows(ExternalApiValidationException.class, () ->
                client.sendRequest(request)
        );

        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Client error"));
    }

    @Test
    @DisplayName("500 Internal Server Error throws ExternalApiException")
    @SuppressWarnings("unchecked")
    void shouldThrowExternalApiExceptionWhenRemoteReturns500() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(500);
        when(mockResponse.body()).thenReturn("{\"error\":\"Internal Crash\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        ExternalAgentRequest request = ExternalAgentRequest.of("agent-1", "http://localhost:8081/test", "payload");

        ExternalApiException ex = assertThrows(ExternalApiException.class, () ->
                client.sendRequest(request)
        );

        assertEquals(500, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Server error"));
    }

    @Test
    @DisplayName("HttpTimeoutException is translated to ExternalApiTimeoutException")
    @SuppressWarnings("unchecked")
    void shouldThrowExternalApiTimeoutExceptionWhenHttpClientTimesOut() throws Exception {
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("request timed out"));

        ExternalAgentRequest request = ExternalAgentRequest.of("agent-1", "http://localhost:8081/test", "payload");

        ExternalApiTimeoutException ex = assertThrows(ExternalApiTimeoutException.class, () ->
                client.sendRequest(request)
        );

        assertEquals(ExternalApiTimeoutException.TimeoutType.REQUEST, ex.getTimeoutType());
        assertTrue(ex.getMessage().contains("timed out"));
    }

    @Test
    @DisplayName("executeTask correctly routes AgentTask to agent endpoint")
    @SuppressWarnings("unchecked")
    void shouldExecuteAgentTaskUsingDefaultEndpoint() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"taskCompleted\":true}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        AgentTask task = new AgentTask("task-100", "agent-remote-9", "REMOTE_AGENT", "{\"query\":\"summarize\"}");
        ExternalAgentResponse response = client.executeTask("agent-remote-9", task);

        assertTrue(response.success());
        assertEquals(200, response.statusCode());
    }

    @Test
    @DisplayName("Two-parameter constructor initializes HttpExternalAgentClient with configured timeouts")
    void testTwoParameterConstructorInitialization() {
        HttpExternalAgentClient defaultClient = new HttpExternalAgentClient(resilienceManager, executor);
        assertNotNull(defaultClient);
    }

    @Test
    @DisplayName("Two-parameter constructor is annotated with @Autowired for Spring dependency injection")
    void testTwoParameterConstructorHasAutowiredAnnotation() throws NoSuchMethodException {
        var constructor = HttpExternalAgentClient.class.getConstructor(
                ResilienceManager.class,
                ResilientOperationExecutor.class
        );
        assertNotNull(constructor);
        assertTrue(constructor.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class),
                "Constructor must have @Autowired annotation for Spring dependency injection");
    }
}
