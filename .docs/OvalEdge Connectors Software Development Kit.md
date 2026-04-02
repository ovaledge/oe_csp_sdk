# **Getting Started with the OvalEdge Connectors SDK for Java**

Build and run **OvalEdge Apps connectors** — modular Java components that connect OvalEdge to external data sources (Databases, APIs, ERPs, CRMs). This repository contains the SDK, a unified API to test connectors, reference connectors (e.g. MonetDB, AWS Console), and tooling to scaffold new connectors.

## Table of contents

[1\. Prerequisites](#1.-prerequisites)  
[2\. Set up your IDE](#2.-set-up-your-ide)  
[3\. Repository structure](#3.-repository-structure)  
[4\. What you will build](#4.-what-you-will-build)  
[5\. Connector contract](#5.-connector-contract)  
[6\. Create a new connector](#6.-create-a-new-connector)  
[7\. Build the SDK](#7.-build-the-sdk)  
[8\. Run and test](#8.-run-and-test)  
[9\. Register a new connector in the repo](#9.-register-a-new-connector-in-the-repo)  
[10\. Publishing the assembly JAR](#10.-publishing-the-assembly-jar)  
[11\. Troubleshooting](#11.-troubleshooting)

### 

### **1\. Prerequisites** {#1.-prerequisites}

| Requirement | Version/ Notes |
| ----- | ----- |
| Java | JDK 21 |
| Maven | 3.8+ |
| IDE | IntelliJ IDEA, Eclipse, or VS Code (with Java \+ Maven support). Optional but recommended. |
| OvalEdge dependencies | `csp-sdk-core` only (from the `oe_csp_sdk_core` repository). Make it available either by local install (`mvn clean install` in `oe_csp_sdk_core`) or by publishing to your organization’s Maven repository and adding that repository to your Maven settings/project. |

Fork this repository into your own GitHub or Bitbucket account, then clone your fork. Work from the repository root (oe\_csp\_sdk).

* Ensure JAVA\_HOME points to JDK 21 and mvn \-v works.

### 

### **2\. Set up your IDE**  {#2.-set-up-your-ide}

Import as a Maven Project

Open the repository root in your IDE and import (or auto-detect) the root pom.xml as a Maven project. This ensures all modules (csp-api, assembly, connectors) are loaded correctly.

Java 21

Set the project SDK and language level to **Java 21**, matching the version defined in the parent POM.

**IntelliJ IDEA**

* *File → Project Structure → Project* → Set **Project SDK** to Java 21  
* *Build, Execution, Deployment → Build Tools → Maven* → Configure Maven to use the same JDK (21)

**Eclipse**

* *Project → Properties → Java Build Path* → Set JRE to Java 21  
* Ensure *Maven → Java configuration* is also set to Java 21

**VS Code**

* Install **Extension Pack for Java** and **Maven for Java**  
* Open the repository folder  
* Configure java.configuration.runtimes to point to JDK 21 if required

Verify Setup

After importing the project, run the following command from the repository root to download dependencies and verify the build:

```
mvn clean install -DskipTests
```

### 

### **3\. Repository structure** {#3.-repository-structure}

* **Connector modules** (e.g., monetdb, awsconsole) implement the Apps connector SPI and can be run standalone or through csp-api.  
* **csp-api** is a Spring Boot app that discovers connectors via Java ServiceLoader and exposes a single REST API; requests are routed by serverType.  
* **assembly** depends on all connector modules and builds one shaded JAR for deployment next to OvalEdge/CSP runtime.

### 

### **4\. What you will build** {#4.-what-you-will-build}

A connector is a **Maven module** that:

1. Implements \*\*AppsConnector\*\* (connection validation, metadata, query, server type, connection attributes).  
2. Provides \*\*MetadataService\*\* (supported object types, containers, objects, fields).  
3. Provides \*\*QueryService\*\* (execute query / fetch data for an entity).  
4. Is registered in META-INF/services/com.ovaledge.csp.v3.core.apps.service.AppsConnector so the SDK and csp-api can discover it.

Optionally it can include a **quick** package: a small Spring Boot app to run and test that connector alone (e.g., health, connection validate, supported-objects).

### **5\. Connector contract** {#5.-connector-contract}

> For production implementation, treat **[CONNECTOR_INTERFACE_REFERENCE.md](CONNECTOR_INTERFACE_REFERENCE.md)** as the source of truth for:
> - required vs optional/default methods
> - method inputs/outputs/errors/invariants
> - lifecycle execution order
> - thread-safety and performance expectations

#### 5.1 AppsConnector (from csp-sdk-core)

Your connector class must implement:

| Method | Purpose |
| ----- | ----- |
| getServerType() | Unique string used in requests (e.g., "monetdb", "myapp"). Use lowercase. |
| validateConnection(ConnectionConfig config) | Validate credentials/settings; return success/failure. |
| getMetadataService() | Return your MetadataService implementation. |
| getQueryService() | Return your QueryService implementation. |
| getAttributes() | Connection form definition (host, port, username, password, etc.) for the OvalEdge UI. |
| exchangeAttributes(ConnInfo) / exchangeAttributes(Map) | Map between UI attributes and ConnInfo / config. |

Additional methods (`getExtendedAttributes`, `exchangeVaultAttributes`, `getVaultPath`, etc.) are used for secrets/vault and governance; you can delegate to `BaseAppConnector` for common behavior (see `monetdb`).

#### 5.2 MetadataService

| Method | Purpose |
| ----- | ----- |
| getSupportedObjects() | List supported entity types (e.g., entities, reports). |
| getContainers(ContainersRequest) | List top-level containers (e.g., schemas, companies, accounts). |
| getObjects(ObjectRequest) | List objects (e.g., tables, entities, reports) in a container for a type. |
| getFields(FieldsRequest) | List fields (columns) for a given object. |

#### 5.3 QueryService

| Method | Purpose |
| ----- | ----- |
| fetchData(QueryRequest) | Execute the query (filters, fields, limit, offset) and return rows. |

#### 5.4 Required vs optional (implementation checklist)

- **Required to implement:** `AppsConnector`, `MetadataService`, `QueryService` contracts used by runtime routing and query flow.  
- **Optional/default:** EDGI and SDK version methods when connector does not support those workflows.  
- **Strongly recommended:** annotate connector with `@SdkConnector(artifactId="...")` (release/version auditability is driven by the assembly-level `release-csp-sdk.properties` file).

### 

### **6\. Create a new connector** {#6.-create-a-new-connector}

#### Option A: Generate with Maven archetype (CLI)

You can scaffold a new connector from the command line without running `csp-api`:

1. **Prerequisite**: Build and install the archetype once from the repo root:

```
mvn clean install -pl connector-archetype -am
```

2. **Generate the connector:** From any directory (e.g. a temp folder), run in **non-interactive mode** (recommended) so all parameters are supplied and Maven does not prompt:

```
mvn archetype:generate -DarchetypeGroupId=com.ovaledge -DarchetypeArtifactId=oe-csp-connector-archetype -DgroupId=com.ovaledge -DarchetypeVersion=<version> -DartifactId=<artifactId> -Dversion=<version> -DclassPrefix=<classPrefix> -DsdkVersion=<version> -DinteractiveMode=false
```

Replace placeholders:
- `<version>` — **oe\_csp\_sdk** parent version (e.g. `8.0.0-SNAPSHOT`)
- `<artifactId>` — connector module name (e.g. `myconnector`, `lookerv2`)
- `<classPrefix>` — PascalCase prefix for generated classes (e.g. `MyConnector`, `LookerV2`)

Example:

```
mvn archetype:generate -DarchetypeGroupId=com.ovaledge -DarchetypeArtifactId=oe-csp-connector-archetype -DgroupId=com.ovaledge -DarchetypeVersion=8.0.0-SNAPSHOT -DartifactId=lookerv2 -Dversion=8.0.0-SNAPSHOT -DclassPrefix=LookerV2 -DsdkVersion=8.0.0-SNAPSHOT -DinteractiveMode=false
```

To run interactively (Maven will prompt for any missing properties), omit `-DinteractiveMode=false` and the `-DartifactId`, `-DclassPrefix`, etc. parameters you do not wish to preset.

3. **Conventions**:  
   * **artifactId**: Use a single lowercase word (e.g. `myconnector`) so the generated Java package `com.ovaledge.csp.apps.<artifactId>` is valid. Do not use hyphens.  
   * **classPrefix**: Use PascalCase (e.g. `MyConnector`). Generated classes will be named `<classPrefix>Connector`, `<classPrefix>MetadataService`, etc.  
4. **Next steps:** Copy the generated folder into the `oe_csp_sdk` directory, then follow the generated **INSTRUCTIONS.txt** to add the module to the parent pom, csp-api, and assembly (same steps as in section 9).

#### Option B: Generate from csp-api UI

You can either **copy and adapt the monetdb module** or **generate scaffolding from csp-api** (if you run it and use the “Create New Connector” flow).

![][image1]

**![][image2]**

After filling out and submitting the form, a .zip file, named after the connector you specified, will be downloaded.

Extract this .zip file and move the resulting folder into the `oe_csp_sdk` directory. From there, follow the provided instructions.

This process will prepare the new connector, allowing you to concentrate on developing the core connectivity logic.

#### 

#### 

#### Option C: Manual path. 

##### C.1 Create the module directory

From repo root:

```
mkdir -p myconnector/src/main/java/com/ovaledge/csp/apps/myconnector/{main,constants,quick}
mkdir -p myconnector/src/main/resources/{META-INF/services,static}
```

Use a **lowercase** artifact name (e.g., myconnector) and match it in package com.ovaledge.csp.apps.myconnector.

##### C.2 Add a pom.xml for the connector

Copy monetdb/pom.xml to myconnector/pom.xml. Then:

* Change \<artifactId\> to myconnector and \<name\> / \<description\> as needed.  
* Set **parent** to oe-csp-sdk (same as monetdb).  
* Keep dependencies: csp-sdk-core, spring-boot-starter, spring-boot-starter-web (for quick app), logging (e.g., slf4j, log4j). Add any driver or client your connector needs (e.g., JDBC driver).  
* In spring-boot-maven-plugin, set \<mainClass\> to your quick app, e.g., com.ovaledge.csp.apps.myconnector.quick.MyConnectorQuickApplication.

##### C.3 Implement the connector classes

* **constants** – e.g., MyConnectorConstants.java: SERVER\_TYPE \= "myconnector", keys/labels for connection attributes (host, port, database, username, password, etc.), and any default port or JDBC URL prefix.  
* **main** – Three classes:  
  * **MyConnectorConnector** – Implements AppsConnector (and usually extends BaseAppConnector). Returns server type, validates connection, returns metadata and query services, defines getAttributes() and exchange methods (see monetdb for pattern).  
  * **MyConnectorMetadataService** – Implements MetadataService: supported objects, containers, objects, fields (for JDBC you can use schema/table/column metadata).  
  * **MyConnectorQueryService** – Implements QueryService: fetchData(QueryRequest) (e.g., build SELECT with filters/limit/offset and return QueryResponse).  
* **quick** (optional) – **MyConnectorController** (e.g., /health, /connection/validate, /supported-objects) and **MyConnectorQuickApplication** (Spring Boot main class) for standalone testing.

For **JDBC** connectors, follow **monetdb**: use ConnectionPoolManager (from csp-sdk-core) with ResourceType.JDBC, and in the connector ensure ConnectionConfig has jdbcUrl and credentials (from attributes or additionalAttributes).

##### C.4 Register the connector (SPI)

Create:  
myconnector/src/main/resources/META-INF/services/com.ovaledge.csp.v3.core.apps.service.AppsConnector  
Content: one line, the fully qualified class name of your connector, e.g.:  
com.ovaledge.csp.apps.myconnector.main.MyConnectorConnector

##### C.5 – Icon (optional)

Place myconnector.png (or similar) under myconnector/src/main/resources/static/ for the connector icon in the UI.

### **7\. Build the SDK** {#7.-build-the-sdk}

From the **repository root**:

| Goal | Command | Result |
| ----- | ----- | ----- |
| Build one connector | mvn clean install \-pl myconnector \-am | Builds myconnector and its dependencies. |
| Build everything | mvn clean install | Builds all modules (connectors, csp-api, assembly). |
| Build assembly JAR only | mvn clean install \-pl assembly \-am | Builds all modules (connectors, csp-api, assembly) and produces assembly/target/csp-sdk-\*.jar |

### 

### **8\. Run and test** {#8.-run-and-test}

#### Option A – Unified API (all connectors)

1. Build: mvn clean install \-pl csp-api \-am (or mvn clean install).  
2. Run: java \-jar csp-api/target/csp-api-\*.jar (Default port is 8800; see csp-api/src/main/resources/application.properties.)  
3. Open the built-in UI (e.g., http://localhost:8800/) to select a connector, set connection attributes, and call connection validate, metadata, and query.  
4. Call REST endpoints:
  - `GET /v1/info` does **not** require a request body.
  - Metadata/query/validate endpoints require request body and (for some endpoints) query parameters.

Example:

```
curl -s http://localhost:8800/v1/info
```

#### Option B – Standalone connector (quick app)

For a connector that has a quick package (e.g., monetdb):

```
mvn -pl monetdb spring-boot:run
```

Or run the JAR of that module (main class configured in its pom.xml).

#### Unit tests for reference connectors

The **MonetDB** and **AWS Console** reference connectors include unit tests that serve as quality gates and as examples for partners. They cover:

* **getAttributes()** – required keys and required flags
* **getSupportedObjects()** – expected object types (e.g., Tables, Views, EC2 Instances, S3 Buckets)
* **validateConnection()** – null config and invalid config (e.g., missing host, database, or credentials) return a proper failure response
* **exchangeAttributes()** round-trip – setting connector-specific attribute values, converting to ConnInfo, then back to attributes preserves values

Run tests for a single connector from the repository root:

```
mvn test -pl monetdb -Dtest=MonetDBConnectorTest
mvn test -pl awsconsole -Dtest=AwsConsoleConnectorTest
```

Or run all tests for that module: `mvn test -pl monetdb` (or `awsconsole`). No running database or AWS credentials are required for these tests.

### **9\. Register a new connector in the repo** {#9.-register-a-new-connector-in-the-repo}

After adding a new connector module (e.g., myconnector), wire it into the build and discovery:

1. **Parent pom.xml** (repo root)  
   * In \<modules\>, add: \<module\>myconnector\</module\>.  
   * In \<dependencyManagement\>\<dependencies\>, add:

```
<dependency>
  <groupId>com.ovaledge</groupId>
  <artifactId>myconnector</artifactId>
  <version>${project.version}</version>
</dependency>
```

2. **csp-api/pom.xml**  
   * In \<dependencies\>, add:

```
<dependency>
  <groupId>com.ovaledge</groupId>
  <artifactId>myconnector</artifactId>
</dependency>
```

3. So the unified API can load and route to your connector.  
4. **assembly/pom.xml**  
   * In \<dependencies\>, add the same \<dependency\> (without version).  
     So the assembly JAR includes your connector.  
5. **SPI file**  
   * Already done in Step 6.4: META-INF/services/com.ovaledge.csp.v3.core.apps.service.AppsConnector with your connector’s FQCN.  
     The assembly uses ServicesResourceTransformer to merge all connector SPI files into one.

6. **Release properties**  
   * The assembly JAR includes a `release-csp-sdk.properties` file (with keys `csp-sdk.release.version`, `csp-sdk.git.commit.full`, `csp-sdk.git.branch`, `csp-sdk.git.buildtime`).  
   * Connector JARs no longer include connector-specific release metadata; release/version audit is driven by the assembly-level metadata.  
   * **Partners** building assembly artifacts outside this repo must ensure their build produces an equivalent `release-csp-sdk.properties`.

Then run:

```
mvn clean install -pl myconnector -am
mvn clean install -pl csp-api -am
# or
./build-csp-sdk.sh
```

to verify the new connector is discovered (e.g., GET /v1/info shows it) and included in the assembly JAR.

### **10\. Publishing the assembly JAR** {#10.-publishing-the-assembly-jar}

The **assembly** module produces a single JAR (csp-sdk-\<version\>.jar) that contains all connectors (and their dependencies, except those excluded in the shade plugin, e.g., Spring Boot and csp-sdk-core which are provided by the OvalEdge runtime). This JAR is intended to be placed in the classpath (or “jar path”) of the OvalEdge/CSP application so it can discover and use all connectors.

* Build: ./build-csp-sdk.sh or mvn clean install \-pl assembly \-am.  
* Output: assembly/target/csp-sdk-\<version\>.jar.

Publishing (e.g., to a private Maven repo or distribution channel) is done with your usual process; the repo does not define a publishing workflow.

### 

### **11\. Troubleshooting** {#11.-troubleshooting}

| Issue | What to check |
| ----- | ----- |
| Connector not in /v1/info | SPI file present under META-INF/services/ with the exact interface name and one fully qualified class name (FQCN) per line. Ensure the connector dependency is added to csp-api (and assembly). Rebuild csp-api and assembly. |
| csp-sdk-core not found | Build and install **oe\_csp\_sdk\_core** first (or publish it to the Maven repository your build uses). Ensure your Maven `settings.xml`/POM has access to the repository hosting `csp-sdk-core`. |
| JDBC connection fails in connector | Ensure ConnectionConfig includes jdbcUrl and username/password. These must be set in the connector from attributes before calling ConnectionPoolManager. See MonetDB’s ensureConnectionConfig. |
| Assembly build fails | Run mvn clean install from the root first so all connector modules are installed. Then run ./build-csp-sdk.sh or mvn clean install \-pl assembly \-am. |
| Port already in use | Change server.port in csp-api/src/main/resources/application.properties (default is 8800). |

### **12\. Deployment checklist (partner runtime)**

Use this checklist after connector development is complete and before handing off to operations.

1. **Build deployable artifact**  
  - Preferred: build `assembly/target/csp-sdk-<version>.jar` using `./build-csp-sdk.sh` or `mvn clean install -pl assembly -am`.

2. **Pick deployment mode**  
  - **Assembly mode (recommended):** one `csp-sdk-<version>.jar` containing all registered connectors.  
  - **Connector-only mode:** deploy `{connector}.jar` only if your runtime already provides required shared dependencies.

3. **Upload/copy JAR for OvalEdge pickup**  
  - Copy artifact(s) to the runtime-configured **jar path/classpath** used by OvalEdge/CSP for connector loading.  
  - The exact folder is environment-specific and configured in OvalEdge system settings (commonly shown as *jar path*).

4. **Ensure runtime prerequisites**  
  - Required shared dependencies must be available in runtime classpath (for example `csp-sdk-core` and platform-shared bundles used in your environment).  
  - The assembly JAR should include `release-csp-sdk.properties` metadata.

5. **Restart services**  
  - Restart OvalEdge/CSP services so new connectors are discovered.

6. **Verify deployment**  
  - Confirm connector appears in connector discovery endpoints (for SDK API runtime, `GET /v1/info` includes `serverType`).  
  - Run connection validation, metadata calls, and one sample query.  
  - Confirm release/version metadata is visible in connector audit/release history views.

7. **Rollback**  
  - Keep the previous known-good JAR; if validation fails, restore old JAR and restart services.

### 

### **Summary**

Before coding, confirm contract details in **[CONNECTOR_INTERFACE_REFERENCE.md](CONNECTOR_INTERFACE_REFERENCE.md)**.

* **Prerequisites**  
  1. JDK 21  
  2. Maven  
  3. **oe\_csp\_sdk\_core** built and installed, or available in your Maven repository (provides csp-sdk-core)  
* **Connector**  
  1. Single module implementing:  
     * AppsConnector  
     * MetadataService  
     * QueryService  
  2. SPI registration included  
  3. Optional **quick** app for standalone connector testing  
* **Build**  
  1. Run mvn clean install, or  
  2. Use ./build-csp-sdk.sh to produce the assembly JAR  
* **Run**  
  1. Use **csp-api** to run a single API and UI serving all connectors, or  
  2. Run a connector’s **quick** app to start only that connector  
* **Adding a New Connector**  
  1. Create a new module  
  2. Implement the required connector contracts  
  3. Add the SPI registration file  
  4. Add the module and dependencies to:  
     * Parent POM  
     * csp-api  
     * Assembly module  
  5. Build and test

[image1]: <data:image/png;base64,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AQBCAQQOABAkIHAAgFAAgQMABAkIHAAgFEDgAABBAgIHAAgFEDgAQJCAwAEAQgEEDgAQJCBwAIBQAIEDAAQJCBwAIBRA4AAAQQICBwAIBRA4AECQgMABAEIBBA4AECQgcACAUACBAwAECQgcACAUQOAAAEECAgcACAUQOABAkIDAAQBCAQQOABAkIHAAgFAAgQMABAkIHAAgFEDgAABBAgIHAAgFEDgAQJCAwAEAQgEEDgAQJCBwAIBQAIEDAAQJCBwAIBRA4AAAQQICBwAIBRA4AECQgMABAEIBBA4AECQgcACAUACBAwAECQgcACAUQOAAAEECAgcACAUQOABAkIDAAQBCAQQOABAkIHAAgFAAgQMABAkIHAAgFEDgAABBAgIHAAgFEDgAQJCAwAEAQgEEDgAQJCBwAIBQAIEDAAQJCBwAIBRA4AAAQQICBwAIBRA4AECQgMABAEIBBA4AECQgcACAUACBAwAECQgcACAUQOAAAEECAgcACAUQOABAkIDAAQBCAQQOABAkIHAAgFAAgQMABAkIHAAgFEDgAABBAgIHAAgFEDgAQJCAwAEAQgEEDgAQJCBwAIBQAIEDAAQJCBwAIBRA4AAAQQICBwAIBRA4AECQgMABAEIBBA4AECQgcACAUACBAwAECQgcACAUQOAAAEECAgcACAUQOABAkIDAAQBCAQQOABAkIHAAgFAAgQMABAkIHAAgFEDgAABBAgIHAAgFEDgAQJCAwAEAQgEEDgAQJCBwAIBQAIEDAAQJCBwAIBRA4AAAQQICBwAIBRA4AECQgMABAEIBBA4AECQgcACAUACBAwAECQgcACAUQOAAAEECAgcACAUQOABAkIDAAQBCAQQOABAkIHAAgFAAgQMABAkIHAAgFEDgAABBAgIHAAgFEDgAQJCAwAEAQgEEDgAQJCBwAIBQAIEDAAQJCBwAIBRA4AAAQQICBwAIBRA4AECQgMABAEIBBA4AECQgcACAUACBAwAECQgcACAUQOAAAEECAgcACAUQOABAkIDAAQBCAQQOABAkIHAAgFAAgQMABAkIHAAgFEDgAABBAgIHAAgFEDgAQJCAwAEAQgEEDgAQJCBwAIBQAIEDAAQJCBwAIBRA4AAAQQICBwAIBRA4AECQgMABAEIBBA4AECQgcACAUACBAwAECQgcACAUQOAAAEECAgcACAUQOABA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>

[image2]: <data:image/png;base64,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OYvWi3EcN950myj/9tjj9Otbb7fGyu2kzloiyjfffItOnjwl8tffOEDHjh2nP/3lIUuwfvrza80FQzFyUqaVb91ZRadOnbLW+fg/nqSDhw7R6dOn6cIvf120PfPs82GyNnLybFHKZXjfZf87x4/TXuMxc7z88qt0+51/oH89vY9efuVV0fbInr/RgYMHRX5o+TI6/cjDIkckT0DgAHAHBA4AD4iFwOUbEiSDZYrj3j8+IKQmZ9tuevyJfwkhYuF5663DdOc994v8a9/+AV1x9XU0bup8scw77xynxavzRD584ixR8rjJ0xeZKw/FzAVrRLsUuPsNQRyfnkXvvnuSfnrltZQSaucxF33122I7UrY4spZtsnIWOI477rpXlDfefBut2FAg8h/97Bei/M3td4Utz8H7uWTNVrru178R9dU5RaLct+8Z2ry9TORfvOSbojx69Jh4LlgUv/qNy+jL//0dOmnkZMgeIvkCAgeAOyBwAHhALAROyg2LmJyJKq1uFuWuCh89+c+nrVmuw28fMQTuPmuZv/39CSu/708PCCniGDVljigXr8pzFLijx46J5Y4de4euvOYGmpC+QAjctdffYokd91/1yxtE/t/f+H+iPHXqNP32dvMULAcLHO/zaGN7t9z2O5Hn7agUfV/66rfooUcepedeeDFM4MZOnSdKlszXXz8g8knTF4qSpW9G1iqRf++HPxXl/X96kJ597gWxr6+8+jodCkkuIjkDAgeAOyBwAHhALASOne0X190sZtk4+JQhCxbH20eOCnnjOHjoTSFILFo89uE95nfRON/z6N9FzoL16muvi/yxx58QpS48b751OKydT0+++ZaZnzhxwpoF5O1xPPDgHrFejrt+f78oZfAYXo8UzyeefMrY93dEzqdjOZ56er+1Lhm/vuX/rMf78J5HrVO4r73+hpBUDp5R5Dhw8JCYeeR45dXXrP1DJGdA4ABwBwQOAA+IhcAhEEMpIHAAuAMCB4AHQOAQCHcBgQPAHRA4ADwgFgL31LPm6UMEIpmDz4jffHfvPyyBwAHgDggcAB7QX4F79Ml39SYEImnj1Kner+0HgQPAHRA4ADygvwL31ycgcIihFRA4ANwBgQPAA2ItcDf/9vdWft1Nv1N6eg8ef8Mtd9G7J09a9RtvvTt8EAIxwAGBA8AdEDgAPCDWAveTq8w7Kqj5iy+/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>