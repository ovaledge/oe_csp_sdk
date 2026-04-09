# Getting Started with the OvalEdge Connectors SDK for Java

---

## What is a Connector?

Connectors serve as interfaces that integrate external data sources with the application. Metadata is fetched, cataloged, and displayed from source systems such as databases, reporting systems, ETL tools, and file systems. These connections use supported protocols such as REST APIs, JDBC, SDKs, and others.

> **![High-level architecture diagram showing OvalEdge platform at center connected to external data sources (databases, APIs, ERPs, CRMs, file systems) via Connector modules. Arrows indicate bidirectional metadata and query flow between the platform and connectors.](assets/ovaledge_arch.png)**

[Introduction to Connectors](https://docs.ovaledge.com/connectors/introduction-to-connectors)

You can connect to data sources using [existing connectors](https://docs.ovaledge.com/connectors/connector-repositories). If you don't find a connector that suits your requirements, use the Connector SDK to build your own.

---

## How will the SDK help in building new connectors?

The OvalEdge Connectors Software Development Kit (SDK) will help build new connectors that are not yet supported. Build and run **OvalEdge connectors** — modular Java components that connect OvalEdge to external data sources (Databases, APIs, ERPs, CRMs, etc.). This repository contains the SDK template with a unified API to test connectors, reference connector source code (e.g., MonetDB, AWS Console), and tooling to scaffold new connectors.

You can either build a connector for your internal use (**PRIVATE**) or make it available to others (**PUBLIC**).

- **Public connectors** can be used by all OvalEdge customers regardless of who built them.
- **Private connectors** can be built by Partners and Customers and deployed internally.

> **Note on Private → Public promotion:** If you develop a PRIVATE connector and later wish to contribute it to the OvalEdge public catalog, you may submit it for review through the standard Pull Request process. OvalEdge runs both automated checks (via GitHub Actions) and a manual review before acceptance. See [Section 9](sdk/9.Publishing_the_assembly_JAR.md) for details.

---

## Who can build the OvalEdge Connector?

To build an OvalEdge connector, you should have:
- Strong knowledge of **Java / J2EE** technologies
- Solid understanding of data source functionality and technical integration methods (APIs, JDBC, SDKs, and similar interfaces)
- Access to the data source environment for building and testing the connector

---

## Table of Contents

1. [Prerequisites](sdk/1.Prerequisites.md)
2. [OvalEdge Connectors Public Repository](sdk/2.OvalEdge_Connectors_Public_Repository.md)
3. [Set up your IDE](sdk/3.Set_up_your_IDE.md)
4. [Repository Structure](sdk/4.Repository_Structure.md)
5. [What you will develop](sdk/5.What_you_will_develop.md)
6. [Generate the new Connector Source](sdk/6.Generate_the_new_Connector_Source.md)
7. [Compile, Build and Run](sdk/7.Compile_Build_Run.md)
8. [Assemble the new connector](sdk/8.Assemble_the_new_connector.md)
9. [Publishing the assembly JAR](sdk/9.Publishing_the_assembly_JAR.md)
10. [Summary](sdk/10.Summary.md)

---
