package com.infinity.ai.ai.prompts

import com.infinity.ai.model.ChatMessage

/**
 * PromptFormatter
 *
 * Qwen 2.5 uses the ChatML prompt format:
 *
 *   <|im_start|>system
 *   You are a helpful assistant.<|im_end|>
 *   <|im_start|>user
 *   Hello!<|im_end|>
 *   <|im_start|>assistant
 *   Hi there!<|im_end|>
 *   <|im_start|>assistant
 *   ← generation starts here
 *
 * If the format is wrong, the model will produce garbage output.
 * This formatter ensures the exact format Qwen 2.5 expects.
 */
object PromptFormatter {

    private const val SYSTEM_PROMPT = "You are G-ONE, a helpful, concise, and intelligent Healthcare AI assistant running entirely offline on the user's edge device. " +
        "You are part of an edge-based health monitoring application. " +
        "For general questions, be direct and helpful. " +
        "For health-related explanations, use simple language, never diagnose, never invent readings, and never claim certainty. " +
        "Do not mention that you are an AI language model unless asked."

    /**
     * Build the full prompt string from chat history.
     *
     * @param history  list of previous messages (user + assistant)
     * @param newInput the new user message to respond to
     * @return formatted prompt string ready for llama.cpp
     */
    fun buildPrompt(history: List<ChatMessage>, newInput: String): String {
        val sb = StringBuilder()

        // System message — sets the AI's personality
        sb.append("<|im_start|>system\n")
        sb.append(SYSTEM_PROMPT)
        sb.append("<|im_end|>\n")

        // Chat history — last N messages to fit in context window
        // We keep the last 10 exchanges to avoid exceeding 2048 tokens
        val recentHistory = history.takeLast(20)
        for (msg in recentHistory) {
            val role = if (msg.isUser) "user" else "assistant"
            sb.append("<|im_start|>$role\n")
            sb.append(msg.text)
            sb.append("<|im_end|>\n")
        }

        // New user message
        sb.append("<|im_start|>user\n")
        sb.append(newInput)
        sb.append("<|im_end|>\n")

        // Assistant turn start — model generates from here
        sb.append("<|im_start|>assistant\n")

        return sb.toString()
    }

    /**
     * Build a health anomaly explanation prompt.
     * The anomaly engine has already determined the event — Qwen only explains it.
     */
    fun buildSessionReportPrompt(sessionJson: String): String {
        val sb = StringBuilder()
        sb.append("<|im_start|>system\n")
        sb.append(
            "You are G-ONE, a health monitoring AI assistant running fully offline. " +
            "You have been given aggregated vitals statistics from a completed monitoring session. " +
            "Write a clear, structured body health report. " +
            "Rules: 1. Do NOT diagnose. 2. Use the actual values. 3. Comment on each vital (HR, SpO2, Temperature). " +
            "4. Note any fall events. 5. Give an overall health status (Normal / Needs Attention / Concerning). " +
            "6. Keep it under 150 words. Be calm, professional, and clear."
        )
        sb.append("<|im_end|>\n")
        sb.append("<|im_start|>user\n")
        sb.append("Here is the session summary data:\n$sessionJson\nPlease generate a full body health report for this session.")
        sb.append("<|im_end|>\n")
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    fun buildHealthExplanationPrompt(anomalyJson: String): String {
        val sb = StringBuilder()
        sb.append("<|im_start|>system\n")
        sb.append(
            "You are G-ONE, a health monitoring AI assistant running fully offline. " +
            "You have been given a confirmed health anomaly detected by the device's sensor analysis engine. " +
            "Your job is ONLY to explain this anomaly in simple, calm language. " +
            "Rules you MUST follow: " +
            "1. Do NOT diagnose any medical condition. " +
            "2. Do NOT invent or change any sensor values. " +
            "3. Do NOT change the severity determined by the detection engine. " +
            "4. Do NOT claim certainty about what is wrong. " +
            "5. Use the actual values provided. " +
            "6. Suggest appropriate next steps (e.g. rest, check again, seek medical attention if severe). " +
            "7. Keep the response under 80 words. Be calm and clear."
        )
        sb.append("<|im_end|>\n")
        sb.append("<|im_start|>user\n")
        sb.append("A health anomaly was detected. Here is the structured data:\n$anomalyJson\nPlease explain this in simple language for the patient or caregiver.")
        sb.append("<|im_end|>\n")
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }
}
