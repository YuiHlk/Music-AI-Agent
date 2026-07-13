package com.musicai.agent.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

interface CreationConstraintsAiService {

    @SystemMessage(fromResource = "prompts/music-requirements-system.txt")
    CreationConstraintsResponse parse(@UserMessage String message);
}
