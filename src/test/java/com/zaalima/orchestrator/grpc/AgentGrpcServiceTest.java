package com.zaalima.orchestrator.grpc;

import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.service.TaskExecutionService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentGrpcServiceTest {

    @Mock
    private TaskExecutionService taskExecutionService;

    @Mock
    private StreamObserver<RegisterAgentResponse> registerResponseObserver;

    @Mock
    private StreamObserver<SendTaskResponse> sendTaskResponseObserver;

    private AgentGrpcService agentGrpcService;

    @BeforeEach
    void setUp() {
        agentGrpcService = new AgentGrpcService(taskExecutionService);
    }

    @Test
    @DisplayName("Valid agent registration returns success and stores agent in registry")
    void testRegisterAgent_ValidRequest_ReturnsSuccess() {
        RegisterAgentRequest request = RegisterAgentRequest.newBuilder()
                .setAgentId("agent-001")
                .setAgentType("worker")
                .build();

        agentGrpcService.registerAgent(request, registerResponseObserver);

        ArgumentCaptor<RegisterAgentResponse> captor = ArgumentCaptor.forClass(RegisterAgentResponse.class);
        verify(registerResponseObserver, times(1)).onNext(captor.capture());
        verify(registerResponseObserver, times(1)).onCompleted();
        verify(registerResponseObserver, never()).onError(any());

        RegisterAgentResponse response = captor.getValue();
        assertTrue(response.getSuccess());
        assertTrue(response.getMessage().contains("Agent registered successfully: agent-001"));
        assertTrue(agentGrpcService.isAgentRegistered("agent-001"));
    }

    @Test
    @DisplayName("Blank agent ID is rejected with failure response")
    void testRegisterAgent_BlankAgentId_Rejected() {
        RegisterAgentRequest request = RegisterAgentRequest.newBuilder()
                .setAgentId("   ")
                .setAgentType("worker")
                .build();

        agentGrpcService.registerAgent(request, registerResponseObserver);

        ArgumentCaptor<RegisterAgentResponse> captor = ArgumentCaptor.forClass(RegisterAgentResponse.class);
        verify(registerResponseObserver, times(1)).onNext(captor.capture());
        verify(registerResponseObserver, times(1)).onCompleted();

        RegisterAgentResponse response = captor.getValue();
        assertFalse(response.getSuccess());
        assertTrue(response.getMessage().contains("agent_id and agent_type must not be empty"));
        assertFalse(agentGrpcService.isAgentRegistered("   "));
    }

    @Test
    @DisplayName("Blank agent type is rejected with failure response")
    void testRegisterAgent_BlankAgentType_Rejected() {
        RegisterAgentRequest request = RegisterAgentRequest.newBuilder()
                .setAgentId("agent-002")
                .setAgentType("")
                .build();

        agentGrpcService.registerAgent(request, registerResponseObserver);

        ArgumentCaptor<RegisterAgentResponse> captor = ArgumentCaptor.forClass(RegisterAgentResponse.class);
        verify(registerResponseObserver, times(1)).onNext(captor.capture());
        verify(registerResponseObserver, times(1)).onCompleted();

        RegisterAgentResponse response = captor.getValue();
        assertFalse(response.getSuccess());
        assertTrue(response.getMessage().contains("agent_id and agent_type must not be empty"));
        assertFalse(agentGrpcService.isAgentRegistered("agent-002"));
    }

    @Test
    @DisplayName("Task submitted for registered agent succeeds and calls TaskExecutionService")
    void testSendTask_RegisteredAgent_Success() {
        // First register the agent
        RegisterAgentRequest regRequest = RegisterAgentRequest.newBuilder()
                .setAgentId("agent-003")
                .setAgentType("data-processor")
                .build();
        agentGrpcService.registerAgent(regRequest, registerResponseObserver);

        // Submit task
        SendTaskRequest taskRequest = SendTaskRequest.newBuilder()
                .setAgentId("agent-003")
                .setTaskId("task-301")
                .setTaskPayload("{\"payload\":\"test data\"}")
                .build();

        agentGrpcService.sendTask(taskRequest, sendTaskResponseObserver);

        // Verify task execution service received the task with expected values
        ArgumentCaptor<AgentTask> taskCaptor = ArgumentCaptor.forClass(AgentTask.class);
        verify(taskExecutionService, times(1)).submitTask(taskCaptor.capture());

        AgentTask submittedTask = taskCaptor.getValue();
        assertEquals("task-301", submittedTask.getTaskId());
        assertEquals("agent-003", submittedTask.getAgentId());
        assertEquals("GRPC_TASK", submittedTask.getTaskType());
        assertEquals("{\"payload\":\"test data\"}", submittedTask.getPayload());

        // Verify response sent to observer
        ArgumentCaptor<SendTaskResponse> responseCaptor = ArgumentCaptor.forClass(SendTaskResponse.class);
        verify(sendTaskResponseObserver, times(1)).onNext(responseCaptor.capture());
        verify(sendTaskResponseObserver, times(1)).onCompleted();

        SendTaskResponse response = responseCaptor.getValue();
        assertTrue(response.getSuccess());
        assertTrue(response.getMessage().contains("Task accepted for submission: task-301"));
    }

    @Test
    @DisplayName("Task for unregistered agent is rejected and not submitted")
    void testSendTask_UnregisteredAgent_Rejected() {
        SendTaskRequest taskRequest = SendTaskRequest.newBuilder()
                .setAgentId("unregistered-agent")
                .setTaskId("task-401")
                .setTaskPayload("{\"action\":\"run\"}")
                .build();

        agentGrpcService.sendTask(taskRequest, sendTaskResponseObserver);

        // Verify TaskExecutionService was NOT called
        verify(taskExecutionService, never()).submitTask(any());

        ArgumentCaptor<SendTaskResponse> captor = ArgumentCaptor.forClass(SendTaskResponse.class);
        verify(sendTaskResponseObserver, times(1)).onNext(captor.capture());
        verify(sendTaskResponseObserver, times(1)).onCompleted();

        SendTaskResponse response = captor.getValue();
        assertFalse(response.getSuccess());
        assertTrue(response.getMessage().contains("Agent not registered: unregistered-agent"));
    }

    @Test
    @DisplayName("Task with blank task ID is rejected")
    void testSendTask_BlankTaskId_Rejected() {
        SendTaskRequest taskRequest = SendTaskRequest.newBuilder()
                .setAgentId("agent-001")
                .setTaskId("   ")
                .setTaskPayload("payload")
                .build();

        agentGrpcService.sendTask(taskRequest, sendTaskResponseObserver);

        verify(taskExecutionService, never()).submitTask(any());

        ArgumentCaptor<SendTaskResponse> captor = ArgumentCaptor.forClass(SendTaskResponse.class);
        verify(sendTaskResponseObserver, times(1)).onNext(captor.capture());
        verify(sendTaskResponseObserver, times(1)).onCompleted();

        SendTaskResponse response = captor.getValue();
        assertFalse(response.getSuccess());
        assertTrue(response.getMessage().contains("agent_id, task_id, and task_payload must not be empty"));
    }

    @Test
    @DisplayName("Task with blank agent ID is rejected")
    void testSendTask_BlankAgentId_Rejected() {
        SendTaskRequest taskRequest = SendTaskRequest.newBuilder()
                .setAgentId("")
                .setTaskId("task-501")
                .setTaskPayload("payload")
                .build();

        agentGrpcService.sendTask(taskRequest, sendTaskResponseObserver);

        verify(taskExecutionService, never()).submitTask(any());

        ArgumentCaptor<SendTaskResponse> captor = ArgumentCaptor.forClass(SendTaskResponse.class);
        verify(sendTaskResponseObserver, times(1)).onNext(captor.capture());
        verify(sendTaskResponseObserver, times(1)).onCompleted();

        SendTaskResponse response = captor.getValue();
        assertFalse(response.getSuccess());
        assertTrue(response.getMessage().contains("agent_id, task_id, and task_payload must not be empty"));
    }

    @Test
    @DisplayName("Task with blank payload is rejected")
    void testSendTask_BlankPayload_Rejected() {
        SendTaskRequest taskRequest = SendTaskRequest.newBuilder()
                .setAgentId("agent-001")
                .setTaskId("task-601")
                .setTaskPayload("  \t\n  ")
                .build();

        agentGrpcService.sendTask(taskRequest, sendTaskResponseObserver);

        verify(taskExecutionService, never()).submitTask(any());

        ArgumentCaptor<SendTaskResponse> captor = ArgumentCaptor.forClass(SendTaskResponse.class);
        verify(sendTaskResponseObserver, times(1)).onNext(captor.capture());
        verify(sendTaskResponseObserver, times(1)).onCompleted();

        SendTaskResponse response = captor.getValue();
        assertFalse(response.getSuccess());
        assertTrue(response.getMessage().contains("agent_id, task_id, and task_payload must not be empty"));
    }

    @Test
    @DisplayName("Exception from TaskExecutionService.submitTask() produces failure response")
    void testSendTask_ExecutionServiceThrowsException_ReturnsFailure() {
        // Register agent first
        RegisterAgentRequest regRequest = RegisterAgentRequest.newBuilder()
                .setAgentId("agent-error-test")
                .setAgentType("tester")
                .build();
        agentGrpcService.registerAgent(regRequest, registerResponseObserver);

        // Configure mock to throw an exception
        doThrow(new RuntimeException("Virtual thread pool failure"))
                .when(taskExecutionService).submitTask(any(AgentTask.class));

        SendTaskRequest taskRequest = SendTaskRequest.newBuilder()
                .setAgentId("agent-error-test")
                .setTaskId("task-fail-001")
                .setTaskPayload("payload")
                .build();

        agentGrpcService.sendTask(taskRequest, sendTaskResponseObserver);

        ArgumentCaptor<SendTaskResponse> captor = ArgumentCaptor.forClass(SendTaskResponse.class);
        verify(sendTaskResponseObserver, times(1)).onNext(captor.capture());
        verify(sendTaskResponseObserver, times(1)).onCompleted();
        verify(sendTaskResponseObserver, never()).onError(any());

        SendTaskResponse response = captor.getValue();
        assertFalse(response.getSuccess());
        assertTrue(response.getMessage().contains("Error submitting task: Virtual thread pool failure"));
    }
}
