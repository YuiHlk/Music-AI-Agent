package com.musicai.agent.agent;

import com.musicai.agent.application.CreationConstraints;

public interface RequirementParser {
    CreationConstraints parse(String userMessage);
}
