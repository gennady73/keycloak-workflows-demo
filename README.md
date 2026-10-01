# Keycloak Workflows demo

```
keycloak-workflows-sandbox/
├── .github/
│   └── workflows/
│       └── validate-workflow-schema.yml  # YAML linter and dry-run API validation
├── docker/                                # Local playground simulating our multi-DC topology
│   ├── docker-compose.yml                 # Spins up Keycloak v26.6 DC-A and DC-B locally
│   ├── keycloak-dc-a.conf                 # Local dev configuration for Site A
│   └── keycloak-dc-b.conf                 # Local dev configuration for Site B
├── spi-listener/                          # Maven project for our custom Java Listener
│   ├── src/
│   │   └── main/
│   │       └── java/
│   │           └── org/
│   │               └── keycloak/
│   │                   └── workflows/
│   │                       ├── WorkflowAuditListener.java        # Core listener intercepting events
│   │                       └── WorkflowAuditListenerFactory.java # Standard Keycloak SPI factory
│   ├── pom.xml                            # Targeted dependencies (keycloak-server-spi, Quarkus)
│   └── README.md                          # SPI compilation and hot-reload instructions
├── workflows/                             # Declarative Workflow templates
│   ├── inactive-user-disable.yml          # User deactivation with cross-DC webhook triggers
│   ├── rotational-password.yml            # Password rotation policy loop
│   └── partner-guest-offboarding.yml      # External IDP federation cleanup workflow
├── tests/                                 # Verification & Mock Event Harness
│   ├── mock-webhook-receiver/             # Simple server to catch outbound webhook alerts
│   │   ├── package.json
│   │   └── server.js                      # Node.js/Express server that prints caught events
│   └── simulate-inactivity.sh             # Script to override database timestamps for test runs
├── playbooks/                             # Developer sync playbooks
│   ├── sync-workflows.yml                 # Pushes YAML templates directly to local Docker nodes
│   └── tasks/
│       └── sync-single-workflow.yml
└── README.md                              # Local setup, run, and debugging guide
```

# Keycloak Workflows Demo Sandbox (`keycloak-workflows-demo`)

This repository serves as an isolated developer playground to design, debug, and test the new **Workflows Engine** introduced in **RHBK v26.6**.

It includes a local multi-instance environment representing decoupled sites (DC-A and DC-B), a custom Java Event Listener SPI for cross-DC state replication (without active-active deployment), test automation scripts, and GitOps Ansible sync playbooks.

## Project Structure

- `docker/`: Configures local Postgres and Keycloak v26.6 instances (Ports `8080` & `8081`).
- `spi-listener/`: Java project for the custom Event Listener SPI.
- `workflows/`: Declarative YAML workflow configurations.
- `playbooks/`: Ansible scripts to register workflows programmatically.
- `tests/`: Mock webhook receiver and database timestamp aging scripts.

---

## Quick-Start Testing Loop

### 1. Compile the Event Listener SPI
First, build the custom Event Listener JAR that intercepts local workflow deactivations and synchronizes them with the secondary site:
```bash
cd spi-listener
mvn clean package
cd ..
```

### 2. Boot the Multi-Site Playground

Start the local database and Keycloak instances. The compiled JAR is automatically mounted into Keycloak's providers directory:
```bash
cd docker
docker-compose up -d
cd ..
```

Wait ~30 seconds for both sites to fully initialize.
Site DC-A: `http://localhost:8080`
Site DC-B: `http://localhost:8081`

### 3. Deploy the Declarative Workflows
Push your local YAML workflow templates directly to both running sites using the GitOps Ansible playbooks:
```bash
cd playbooks
ansible-playbook sync-workflows.yml
cd ..
```

### 4. Simulate and Test Inactivity Deactivation

To test the "Symmetrical Inactive User Disabling Workflow":
* Create a test user (e.g., jdoe) in the Admin Console of both DC-A and DC-B.
* Run the database aging helper script to simulate 250 days of inactivity:

  ```bash
  ./tests/simulate-inactivity.sh
  ```

* Enter name("jdoe") when prompted
* Boot the local Webhook Receiver to catch notifications:

  ```bash
  cd tests/mock-webhook-receiver
  npm install && npm start
  ```

Trigger the background Workflows task in `DC-A`. Once deactivated locally on `DC-A`, your custom SPI will intercept the event and replicate the deactivation REST call directly to `DC-B`!


### ⚠️ Operational Note: Workflows Background Step-Runner Tuning    

When executing time-delayed workflow steps (e.g., `after: 180d` or `after: 1m`), tasks initially enter a **`Pending`** state. They remain in `Pending` until Keycloak's  background **Step Runner Task** (`ScheduleWorkflowTask`) polls the database and evaluates them, while **default is 12 hours**.

Without proper tuning, workflows may sit silently in `Pending` without generating errors or log entries, leading to false-positive troubleshooting.

#### Environment Configuration Guidelines (`keycloak.conf`)

* **Local Sandbox / Development (`docker/keycloak-dc-*.conf`)**:
  Set a short polling interval to force immediate execution sweeps during testing:
  ```properties
  # Poll database for due pending steps every 5 seconds
  spi-workflows-step-runner-task-interval=5
  ```

* **Production StatefulSets**: Set a 24-hour interval aligned to off-peak hours to avoid database lock contention during active user login hours:

  ```
  # Run the background evaluation sweep once every 24 hours starting at 02:00 AM
  spi-workflows-step-runner-task-interval=86400
  spi-workflows-step-runner-task-start-time=02:00
  ```

> **Quick Check:** If a test workflow remains in `Pending` despite database timestamps being aged, verify that `spi-workflows-step-runner-task-interval=5` is active in your dev container configuration and restart the Keycloak instance.

---
2026