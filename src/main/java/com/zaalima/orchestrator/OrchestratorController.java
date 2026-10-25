
package com.zaalima.orchestrator;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zaalima.orchestrator.service.VirtualThreadService;
import com.zaalima.orchestrator.redis.RedisMessagePublisher;

@RestController
public class OrchestratorController {

    private final VirtualThreadService virtualThreadService;
    private final RedisMessagePublisher redisMessagePublisher;

    public OrchestratorController(
            VirtualThreadService virtualThreadService,
            RedisMessagePublisher redisMessagePublisher) {
        this.virtualThreadService = virtualThreadService;
        this.redisMessagePublisher = redisMessagePublisher;
    }

    @GetMapping("/")
    public String home() {
        return "Zaalima Agent Orchestrator is running!";
    }

    @GetMapping("/virtual-thread")
    public List<String> runVirtualThreads() throws Exception {
        return virtualThreadService.executeTasks();
    }

    @GetMapping("/redis/publish")
    public String publishRedisMessage(
            @RequestParam String message) {
        redisMessagePublisher.publish(message);
        return "Redis message sent: " + message;
    }
}

