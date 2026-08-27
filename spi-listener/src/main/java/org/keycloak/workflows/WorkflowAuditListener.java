package org.keycloak.workflows;

import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class WorkflowAuditListener implements EventListenerProvider {

    private static final Logger logger = Logger.getLogger(WorkflowAuditListener.class);
    private final KeycloakSession session;
    private final HttpClient httpClient;

    // Direct endpoint of the secondary datacenter (DC-B on port 8081)
    private static final String TARGET_DC_B_URL = "http://localhost:8081/admin/realms/";

    public WorkflowAuditListener(KeycloakSession session) {
        this.session = session;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public void onEvent(Event event) {
        logger.infof("Intercepted Event: Type=%s, Realm=%s, User=%s", 
                event.getType(), event.getRealmId(), event.getUserId());
        
        // Match user disabling actions executed locally on DC-A by the Workflow Engine
        if ("USER_DISABLED".equals(event.getType().name()) || "WORKFLOW_STEP_EXECUTED".equals(event.getType().name())) {
            String userId = event.getUserId();
            String realmId = event.getRealmId();
            
            logger.infof("Cross-Site Trigger Sensed: Deactivating User %s on Site DC-B...", userId);
            syncDeactivationToDcB(realmId, userId);
        }
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
        // Intercept administrative actions here if audit logs require it
    }

    private void syncDeactivationToDcB(String realmId, String userId) {
        try {
            // Resolve JWT Bearer credentials (represented generically here)
            String adminToken = resolveAdminToken();
            if (adminToken == null) {
                logger.error("Deactivation Sync Aborted: Unable to resolve Admin Bearer Token.");
                return;
            }

            String targetUri = TARGET_DC_B_URL + realmId + "/users/" + userId;
            String jsonPayload = "{\"enabled\": false}";

            // Construct authenticated REST PUT request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUri))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + adminToken)
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            // Dispatch asynchronously to prevent locking the local processing thread
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(response -> {
                        if (response.statusCode() == 204 || response.statusCode() == 200) {
                            logger.infof("Cross-Site Sync Successful: User %s has been deactivated on DC-B.", userId);
                        } else {
                            logger.warnf("Cross-Site Sync rejected by DC-B with status: %d", response.statusCode());
                        }
                    })
                    .exceptionally(throwable -> {
                        logger.error("Connection drop or timeout executing cross-site HTTP REST sync: " + throwable.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            logger.error("Failure executing Cross-Site replication step: " + e.getMessage(), e);
        }
    }

    private String resolveAdminToken() {
        // In your production sandbox, this parses the master realm Client Credentials Grant
        return "token-placeholder-resolved-from-master-realm";
    }

    @Override
    public void close() {
        // No-op
    }
}
