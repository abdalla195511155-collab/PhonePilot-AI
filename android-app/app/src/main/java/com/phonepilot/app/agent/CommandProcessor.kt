package com.phonepilot.app.agent

/** Anything that can process a user command (the orchestrator in the app, a fake in tests). */
interface CommandProcessor {
    suspend fun processCommand(command: UserCommand): AgentCommandResult
}
