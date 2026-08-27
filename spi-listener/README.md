This document provides focused build, packaging, and hot-swapping instructions for engineers developing the custom Java SPI event listener.
spi-listener/README.md
# Keycloak Workflows SPI Listener

This module contains the custom **`workflows-audit-listener`** Event Listener SPI. It is designed to compile as a standalone JAR, deploy to Keycloak's `/providers` path, and intercept internal state changes to coordinate cross-DC synchronization tasks.

## Prerequisites
- Java 17 SDK (or higher)
- Apache Maven 3.8+

## Compilation & Deployment Lifecycle

1. **Compile & Package**:
   Build the optimized SPI JAR file by running Maven from this directory:
   ```cd spi-listener && mvn clean package```

    This generates the output asset: `target/workflows-audit-listener-1.0.0-SNAPSHOT.jar`.
    Hot-Swap in Local Compose Sandbox: Our local `docker-compose.yml` mounts the `target/ directory` directly to `/opt/keycloak/ providers` inside the containers. To load code modifications:

2. **Recompile the JAR**
    ```mvn package```

    **Restart the target container to trigger Quarkus build and ClassLoader refresh**
    ```docker-compose restart keycloak-dc-a```

3. **Validation:** 
Verify that Keycloak has successfully detected and registered the provider on startup:
```docker logs keycloak-dc-a | grep -i "workflows-audit-listener"```

---

# Keycloak SPI compilation and hot-reload instructions
1. Compile the Java SPI:
```cd spi-listener && mvn clean package```
2. Start the Local Environment:
```cd ../docker && docker-compose up -d```
3. Sync the Workflows:
```cd ../playbooks && ansible-playbook sync-workflows.yml```

---

2026