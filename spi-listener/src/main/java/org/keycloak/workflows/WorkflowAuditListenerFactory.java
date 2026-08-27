package org.keycloak.workflows;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class WorkflowAuditListenerFactory implements EventListenerProviderFactory {

    public static final String PROVIDER_ID = "workflows-audit-listener";

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new WorkflowAuditListener(session);
    }

    @Override
    public void init(Config.Scope config) {
        // Initialization tasks if custom spi configs are defined in keycloak.conf
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Post-initialization lifecycle hooks
    }

    @Override
    public void close() {
        // Server-shutdown resource cleanup
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
