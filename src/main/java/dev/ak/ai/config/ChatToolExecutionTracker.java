package dev.ak.ai.config;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class ChatToolExecutionTracker {

    private boolean subscriptionChanged;
    private boolean autopilotChanged;

    public void markSubscriptionChanged() {
        this.subscriptionChanged = true;
    }

    public void markAutopilotChanged() {
        this.autopilotChanged = true;
    }

    public boolean isSubscriptionChanged() {
        return subscriptionChanged;
    }

    public boolean isAutopilotChanged() {
        return autopilotChanged;
    }
}