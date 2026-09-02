package com.saarthi.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request and Response DTOs for Saarthi Voice AI Core with Emotional Intelligence.
 */
public class AiChatModels {

    public static class ChatRequest {

        @NotBlank(message = "Query cannot be blank")
        @Size(max = 2000, message = "Query cannot exceed 2000 characters")
        private String query;

        @Size(max = 64, message = "Model name cannot exceed 64 characters")
        private String model;

        @Size(max = 16, message = "Language tag cannot exceed 16 characters")
        private String language;

        public ChatRequest() {}

        public ChatRequest(String query, String model, String language) {
            this.query = query;
            this.model = model;
            this.language = language;
        }

        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
    }

    public static class ChatResponse {
        private String replyText;
        private ActuationCommand executedAction;
        private String modelUsed;
        private boolean success;
        private String errorMessage;
        private String emotion; // "helpful", "serious", "thinking", "happy"

        public ChatResponse() {
            this.emotion = "helpful";
        }

        public ChatResponse(String replyText, ActuationCommand executedAction, String modelUsed, boolean success) {
            this(replyText, executedAction, modelUsed, success, "helpful");
        }

        public ChatResponse(String replyText, ActuationCommand executedAction, String modelUsed, boolean success, String emotion) {
            this.replyText = replyText;
            this.executedAction = executedAction;
            this.modelUsed = modelUsed;
            this.success = success;
            this.emotion = (emotion != null && !emotion.isBlank()) ? emotion : "helpful";
        }

        public static ChatResponse error(String message) {
            ChatResponse resp = new ChatResponse();
            resp.setSuccess(false);
            resp.setErrorMessage(message);
            resp.setReplyText("I encountered an error processing your query: " + message);
            resp.setEmotion("serious");
            return resp;
        }

        public String getReplyText() { return replyText; }
        public void setReplyText(String replyText) { this.replyText = replyText; }

        public ActuationCommand getExecutedAction() { return executedAction; }
        public void setExecutedAction(ActuationCommand executedAction) { this.executedAction = executedAction; }

        public String getModelUsed() { return modelUsed; }
        public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public String getEmotion() { return emotion; }
        public void setEmotion(String emotion) { this.emotion = emotion; }
    }
}
