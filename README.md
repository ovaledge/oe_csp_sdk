# OvalEdge Connectors SDK

Build and run **OvalEdge connectors** — modular Java components that connect the OvalEdge Data Governance Platform to external data sources (Databases, APIs, ERPs, CRMs, file systems, and more).

This repository contains the Connector SDK template, a unified REST API for testing connectors, reference connector implementations (MonetDB, AWS Console), and Maven tooling to scaffold new connectors.

---

## Documentation

All SDK documentation is located in the [`.docs/`](.docs/) folder:

| Document                                                                                  | Description |
|-------------------------------------------------------------------------------------------|---|
| [Getting Started with the SDK](.docs/OvalEdge_Connectors_Software_Development_Kit.md) | Step-by-step guide: prerequisites, IDE setup, connector development, building, testing, and deployment |
| [Connector Interface Reference](.docs/CONNECTOR_INTERFACE_REFERENCE.md)                   | Full API contract for `AppsConnector`, `MetadataService`, and `QueryService` |
| [SDK Developer Registration & Legal](.docs/SDK_Developer_Registration.md)                 | Developer onboarding process, legal agreements by persona, security and ethical development standards, access provisioning, and compliance requirements |

---

## Quick Start

**1. Register as an OvalEdge SDK Developer**

Send your details to **developer@ovaledge.com** — see the [Registration Guide](.docs/SDK_Developer_Registration.md) for required information. Access credentials are issued after your SDK agreement is signed.

**2. Fork this repository**

```bash
git fork https://github.com/ovaledge/oe_csp_sdk
```

**3. Set up prerequisites**

- JDK 21 (`JAVA_HOME` must point to JDK 21)
- Maven 3.8+
- Git

**4. Build and verify**

```bash
mvn clean install -DskipTests
```

**5. Generate a new connector**

```bash
mvn archetype:generate \
  -DarchetypeGroupId=com.ovaledge \
  -DarchetypeArtifactId=oe-csp-connector-archetype \
  -DgroupId=com.ovaledge \
  -DarchetypeVersion=1.0.0-SNAPSHOT \
  -DartifactId=myconnector \
  -Dversion=1.0.0-SNAPSHOT \
  -DclassPrefix=MyConnector \
  -DsdkVersion=1.0.0-SNAPSHOT \
  -DinteractiveMode=false
```

See the full [Getting Started guide](.docs/OvalEdge_Connectors_Software_Development_Kit.md) for all steps.

---

## Repository Structure

| Module / File | Purpose |
|---|---|
| `assembly/` | Builds the single shaded JAR containing all connectors for deployment |
| `connector-archetype/` | Maven archetype to scaffold a new connector module |
| `csp-api/` | Spring Boot REST gateway for testing all connectors locally |
| `monetdb/` | Reference JDBC connector (MonetDB) |
| `awsconsole/` | Reference REST/SDK connector (AWS Console) |
| `.docs/` | SDK documentation |
| `build-csp-sdk.sh` | Convenience script to build the assembly JAR |
| `pom.xml` | Root Maven POM — modules, dependency management, shared build config |

---

## License

This repository is governed by the [OvalEdge Connector SDK Source License](LICENSE).

Use of this Software is restricted to building connector integrations with the OvalEdge Data Governance Platform. **Production use requires a signed OvalEdge SDK License Agreement.**

To register and obtain a signed agreement, contact **developer@ovaledge.com**.

> This is a **source available** repository, not an open source project. Forking and contributing are welcome under the terms of the License; use for competing products or platforms is not permitted.

---

## Contributing

Contributions from registered developers are welcome. Before raising a Pull Request, review the [Quality Guidelines](.docs/sdk/7.Development_And_Testing.md#73-code-review-standards-and-quality-gates)

---

## Support

| Topic | Contact                                       |
|---|-----------------------------------------------|
| Developer registration & onboarding | developer@ovaledge.com                     |
| Security vulnerabilities & responsible disclosure | security@ovaledge.com |
| OvalEdge platform documentation | https://docs.ovaledge.com                     |
