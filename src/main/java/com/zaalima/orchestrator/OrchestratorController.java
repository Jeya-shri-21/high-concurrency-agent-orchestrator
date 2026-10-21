
package com.zaalima.orchestrator;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zaalima.orchestrator.service.VirtualThreadService;

@RestController
public class OrchestratorController {

    private final VirtualThreadService virtualThreadService;

    public OrchestratorController(
            VirtualThreadService virtualThreadService) {
        this.virtualThreadService = virtualThreadService;
    }

    @GetMapping("/")
    public String home() {
        return "Zaalima Agent Orchestrator is running!";
    }

    @GetMapping("/virtual-thread")
    public List<String> runVirtualThreads() throws Exception {
        return virtualThreadService.executeTasks();
    }
}
