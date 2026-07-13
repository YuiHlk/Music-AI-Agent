package com.musicai.agent.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface MusicCreatorAgent {

    @SystemMessage(fromResource = "prompts/music-agent-system.txt")
    @UserMessage("Current project ID: {{projectId}}\nUser request: {{message}}")
    String chat(@MemoryId @V("projectId") String projectId, @V("message") String message);
}
