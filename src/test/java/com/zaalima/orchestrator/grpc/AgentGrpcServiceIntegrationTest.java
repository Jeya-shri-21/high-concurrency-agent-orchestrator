package com.zaalima.orchestrator.grpc;

import com.zaalima.orchestrator.VirtualThreadTaskHandlingApplication;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.devh.boot.grpc.server.serverfactory.GrpcServerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = VirtualThreadTaskHandlingApplication.class,
        properties = {
                "grpc.server.port=0"
        }
)
@DirtiesContext
class AgentGrpcServiceIntegrationTest {

    @Autowired
    private GrpcServerFactory grpcServerFactory;

    private ManagedChannel channel;
    private AgentServiceGrpc.AgentServiceBlockingStub blockingStub;

    @BeforeEach
    void setUp() {
        int port = grpcServerFactory.getPort();
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .build();
        blockingStub = AgentServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("End-to-end: Register agent and verify client receives successful response")
    void testRegisterAgent_OverGrpcChannel_Success() {
        RegisterAgentRequest request = RegisterAgentRequest.newBuilder()
                .setAgentId("agent-live-01")
                .setAgentType("calculator")
                .build();

        RegisterAgentResponse response = blockingStub.registerAgent(request);

        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertTrue(response.getMessage().contains("Agent registered successfully: agent-live-01"));
    }

    @Test
    @DisplayName("End-to-end: Send task for registered agent and verify acceptance response")
    void testSendTask_ForRegisteredAgent_Success() {
        // Register agent first
        RegisterAgentRequest regRequest = RegisterAgentRequest.newBuilder()
                .setAgentId("agent-live-02")
                .setAgentType("data-processor")
                .build();
        RegisterAgentResponse regResponse = blockingStub.registerAgent(regRequest);
        assertTrue(regResponse.getSuccess());

        // Send task
        SendTaskRequest taskRequest = SendTaskRequest.newBuilder()
                .setAgentId("agent-live-02")
                .setTaskId("live-task-100")
                .setTaskPayload("{\"operation\":\"compute\",\"value\":42}")
                .build();

        SendTaskResponse taskResponse = blockingStub.sendTask(taskRequest);

        assertNotNull(taskResponse);
        assertTrue(taskResponse.getSuccess());
        assertTrue(taskResponse.getMessage().contains("Task accepted for submission: live-task-100"));
    }

    @Test
    @DisplayName("End-to-end: Send task for unregistered agent is rejected")
    void testSendTask_ForUnregisteredAgent_Rejected() {
        SendTaskRequest taskRequest = SendTaskRequest.newBuilder()
                .setAgentId("unregistered-live-agent")
                .setTaskId("live-task-200")
                .setTaskPayload("some payload")
                .build();

        SendTaskResponse taskResponse = blockingStub.sendTask(taskRequest);

        assertNotNull(taskResponse);
        assertFalse(taskResponse.getSuccess());
        assertTrue(taskResponse.getMessage().contains("Agent not registered: unregistered-live-agent"));
    }
}
