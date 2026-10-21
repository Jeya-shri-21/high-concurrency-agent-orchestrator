package com.zaalima.orchestrator.grpc;

import com.zaalima.orchestrator.model.AgentTask;
import com.zaalima.orchestrator.service.TaskExecutionService;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@GrpcService
public class AgentGrpcService extends AgentServiceGrpc.AgentServiceImplBase {

    private final TaskExecutionService taskExecutionService;
    private final Map<String, String> registeredAgents = new ConcurrentHashMap<>();

    @Autowired
    public AgentGrpcService(TaskExecutionService taskExecutionService) {
        this.taskExecutionService = taskExecutionService;
    }

    @Override
    public void registerAgent(RegisterAgentRequest request,
                              StreamObserver<RegisterAgentResponse> responseObserver) {
        boolean responseSent = false;
        try {
            String agentId = request.getAgentId();
            String agentType = request.getAgentType();

            if (agentId == null || agentId.isBlank() || agentType == null || agentType.isBlank()) {
                RegisterAgentResponse response = RegisterAgentResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Invalid request: agent_id and agent_type must not be empty")
                        .build();
                responseSent = true;
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }

            String trimmedAgentId = agentId.trim();
            String trimmedAgentType = agentType.trim();

            registeredAgents.put(trimmedAgentId, trimmedAgentType);

            RegisterAgentResponse response = RegisterAgentResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Agent registered successfully: " + trimmedAgentId)
                    .build();
            responseSent = true;
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception ex) {
            if (!responseSent) {
                RegisterAgentResponse errorResponse = RegisterAgentResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Error registering agent: " + ex.getMessage())
                        .build();
                responseObserver.onNext(errorResponse);
                responseObserver.onCompleted();
            }
        }
    }

    @Override
    public void sendTask(SendTaskRequest request,
                         StreamObserver<SendTaskResponse> responseObserver) {
        boolean responseSent = false;
        try {
            String agentId = request.getAgentId();
            String taskId = request.getTaskId();
            String taskPayload = request.getTaskPayload();

            if (agentId == null || agentId.isBlank()
                    || taskId == null || taskId.isBlank()
                    || taskPayload == null || taskPayload.isBlank()) {
                SendTaskResponse response = SendTaskResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Invalid request: agent_id, task_id, and task_payload must not be empty")
                        .build();
                responseSent = true;
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }

            String trimmedAgentId = agentId.trim();
            String trimmedTaskId = taskId.trim();

            if (!registeredAgents.containsKey(trimmedAgentId)) {
                SendTaskResponse response = SendTaskResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Agent not registered: " + trimmedAgentId)
                        .build();
                responseSent = true;
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }

            AgentTask task = new AgentTask(
                    trimmedTaskId,
                    trimmedAgentId,
                    "GRPC_TASK",
                    taskPayload.trim()
            );

            taskExecutionService.submitTask(task);

            SendTaskResponse response = SendTaskResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Task accepted for submission: " + trimmedTaskId)
                    .build();
            responseSent = true;
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception ex) {
            if (!responseSent) {
                SendTaskResponse errorResponse = SendTaskResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Error submitting task: " + ex.getMessage())
                        .build();
                responseObserver.onNext(errorResponse);
                responseObserver.onCompleted();
            }
        }
    }

    public boolean isAgentRegistered(String agentId) {
        return agentId != null && registeredAgents.containsKey(agentId.trim());
    }

    public Map<String, String> getRegisteredAgents() {
        return Collections.unmodifiableMap(registeredAgents);
    }
}
