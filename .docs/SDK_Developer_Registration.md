# OvalEdge SDK Developer Registration & Enablement Guide

---

## Overview

The OvalEdge Connector SDK ecosystem supports three classes of developers, each with a distinct legal relationship, IP ownership structure, and security compliance obligation. This guide covers the end-to-end onboarding process, the applicable agreement for each developer type, mandatory security and ethical development standards, access provisioning, ongoing compliance monitoring, and offboarding procedures.

| Developer Type | Legal Instrument | IP Ownership | Compensation |
|---|---|---|---|
| Partners / Customers | SDK License Agreement | Partner-owned | N/A |
| Contractors / Service Providers | Contractor & Work-for-Hire Agreement | OvalEdge-owned | Paid |
| Open Source Contributors | Contributor License Agreement (CLA) | OvalEdge-owned (licensed) | Unpaid — certificate issued |

---

## Part I: Developer Registration

### 1.1 Registration Process

All developers must register before receiving SDK access. Registration is initiated by emailing the following information to **developer@ovaledge.com**:

**Required Information:**

- Organization Name and/or Individual Name
- Developer Type (select one: Partner / Customer, Contractor / Service Provider, Open Source Contributor)
- Primary Contact: Full Name, Email Address, Phone Number
- Technical Contact (if different from Primary): Full Name, Email Address
- Intended Connector Use Case — describe the integration you plan to build
- Target Data Source(s) *(e.g., Salesforce, SAP, Snowflake, REST API, JDBC-compliant database)*
- For Contractors: name of the OvalEdge contact who engaged you
- For Open Source Contributors: GitHub username

### 1.2 Verification and Due Diligence

The OvalEdge team performs verification appropriate to the developer type before issuing agreements or credentials:

**Partners / Customers:**
- Verify business legitimacy and operational relevance to OvalEdge's connector ecosystem.
- Confirm the target integration is within the permitted scope of the SDK License Agreement.
- Review any existing OvalEdge customer or reseller agreement for consistency.

**Contractors / Service Providers:**
- Verify that a valid Statement of Work (SOW) or Master Services Agreement (MSA) is in place or being executed concurrently.
- Validate identity and business registration where appropriate.
- Confirm the contractor has or will sign the OvalEdge Contractor & Work-for-Hire Agreement prior to access.
- OvalEdge **prefers** to engage contractors from companies holding recognized security certifications (SOC 2 Type II, ISO 27001, or equivalent). Certification status should be disclosed at registration; OvalEdge reserves the right to request evidence.

**Open Source Contributors:**
- Verify GitHub account existence and basic activity history.
- No formal background check required; the CLA governs the legal relationship.

### 1.3 Registration Outcome and Timeline

Upon successful verification:
- OvalEdge issues the applicable agreement (see Part II) to the primary contact within **3–5 business days**.
- The developer reviews, signs, and returns the agreement.
- Access credentials are provisioned within **2 business days** of signed agreement receipt.

If verification cannot be completed (e.g., insufficient information, business legitimacy concerns), OvalEdge will notify the applicant within **5 business days** with the reason for the hold.

---

## Part II: Legal Agreements

### 2.1 SDK License Agreement (Partners / Customers)

**Effective upon signature by both parties.** Signed by the Developer's authorized representative and by OvalEdge's Head of Connectors.

**2.1.1 Grant of License**

OvalEdge grants the Developer a limited, non-exclusive, non-transferable, non-sublicensable, revocable license to access and use the OvalEdge Connector SDK — including its frameworks, libraries, sample code, documentation, and associated artifacts — solely for the purpose of building, testing, and deploying connector integrations with the OvalEdge Data Governance Platform for the Developer's own internal use or for the Developer's customers.

**2.1.2 Scope Limitations**

The license does not permit the Developer to:

(a) Use the SDK to build connectors for platforms that compete with OvalEdge;
(b) Sublicense, resell, or otherwise transfer SDK access or derived SDK components to any third party not covered by this Agreement;
(c) Reverse engineer, decompile, or disassemble the SDK core libraries or OvalEdge platform components;
(d) Access or attempt to access other developers' repositories, credentials, or SDK assets;
(e) Use the SDK to build any connector that introduces unauthorized data collection, exfiltration, or surveillance capabilities;
(f) Remove, alter, or obscure any OvalEdge copyright, trademark, or proprietary notices.

**2.1.3 Intellectual Property**

- The SDK, its frameworks, core libraries, and all OvalEdge platform components remain the exclusive intellectual property of OvalEdge, Inc.
- Connectors developed by the Developer using the SDK are owned by the Developer, subject to the restrictions and license grant in this Agreement.
- If the Developer elects to submit a connector to the OvalEdge public catalog, the Developer grants OvalEdge a perpetual, worldwide, royalty-free, non-exclusive license to distribute, host, display, and sublicense that connector as part of OvalEdge's connector catalog. This license does not transfer ownership of the connector to OvalEdge.

**2.1.4 Confidentiality**

The Developer agrees to treat as confidential all non-public OvalEdge materials received under this Agreement, including SDK internals, connector framework architecture, pre-release documentation, and JFrog credentials. The Developer shall use the same degree of care as it uses to protect its own confidential information, but no less than reasonable care. This obligation survives termination for a period of three (3) years.

**2.1.5 Security and Compliance Obligations**

The Developer agrees to comply with all security and ethical development standards set forth in Part III of this Guide. Breach of these standards constitutes a material breach of this Agreement.

**2.1.6 Representations and Warranties**

The Developer represents and warrants that:

(a) It has the legal authority to enter into this Agreement;
(b) The connectors it develops will not infringe any third party's intellectual property rights;
(c) The connectors will not contain malicious code, undisclosed data collection mechanisms, or backdoors;
(d) It will comply with all applicable laws and regulations in connection with its use of the SDK, including data protection laws applicable to any data sources integrated via the connector.

**2.1.7 Indemnification**

The Developer shall defend, indemnify, and hold harmless OvalEdge, its officers, employees, and agents from and against any third-party claims, damages, liabilities, and expenses (including reasonable attorneys' fees) arising out of or related to: (a) the Developer's use of the SDK in breach of this Agreement; (b) any connector developed by the Developer, including claims that the connector infringes a third party's intellectual property; or (c) the Developer's violation of applicable law.

**2.1.8 Disclaimer of Warranties**

THE SDK IS PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED. OVALEDGE DISCLAIMS ALL WARRANTIES INCLUDING, WITHOUT LIMITATION, WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NON-INFRINGEMENT. OVALEDGE DOES NOT WARRANT THAT THE SDK WILL BE ERROR-FREE OR UNINTERRUPTED.

**2.1.9 Limitation of Liability**

IN NO EVENT SHALL OVALEDGE BE LIABLE FOR ANY INDIRECT, INCIDENTAL, SPECIAL, CONSEQUENTIAL, OR PUNITIVE DAMAGES ARISING OUT OF OR RELATED TO THIS AGREEMENT OR THE USE OF THE SDK, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGES. OVALEDGE'S TOTAL AGGREGATE LIABILITY UNDER THIS AGREEMENT SHALL NOT EXCEED ONE HUNDRED DOLLARS (US $100).

**2.1.10 Term and Termination**

This Agreement is effective from the date of last signature and continues until terminated. Either party may terminate this Agreement with thirty (30) days' written notice. OvalEdge may terminate immediately upon written notice if the Developer materially breaches this Agreement and fails to cure such breach within fifteen (15) days of receiving notice of the breach.

Upon termination: (a) the Developer's license to use the SDK is immediately revoked; (b) the Developer must cease use of the SDK and destroy or return all OvalEdge confidential materials; (c) GitHub and JFrog access is revoked within 24 hours of the effective termination date.

**2.1.11 Governing Law and Dispute Resolution**

This Agreement is governed by and construed in accordance with the laws of the State of Georgia, USA, without regard to its conflict of law principles. Any dispute arising under this Agreement shall be subject to the exclusive jurisdiction of the state and federal courts located in the State of Georgia. In any action to enforce this Agreement, the prevailing party is entitled to recover reasonable attorneys' fees and costs.

**2.1.12 General Provisions**

- *Entire Agreement:* This Agreement constitutes the entire agreement between the parties with respect to its subject matter and supersedes all prior negotiations, representations, or agreements.
- *Amendment:* OvalEdge may update the terms of this Agreement upon sixty (60) days' written notice. Continued use of the SDK after the effective date of an update constitutes acceptance. If the Developer does not accept, it must terminate per Section 2.1.10.
- *Severability:* If any provision is held invalid or unenforceable, the remaining provisions continue in full force.
- *Waiver:* Failure to enforce any right under this Agreement does not constitute a waiver of that right.
- *Assignment:* The Developer may not assign this Agreement or any rights hereunder without OvalEdge's prior written consent. OvalEdge may assign this Agreement in connection with a merger, acquisition, or sale of substantially all its assets.

---

### 2.2 Contractor and Work-for-Hire Agreement (Contractors / Service Providers)

**Effective upon signature by both parties.** Signed by the Contractor's authorized representative and by OvalEdge's Head of Connectors or designee. This agreement is typically executed in conjunction with an MSA or SOW.

**2.2.1 Engagement Scope**

This Agreement governs the Contractor's use of the OvalEdge Connector SDK in the performance of connector development services as described in the applicable Statement of Work (SOW). The SOW is incorporated herein by reference.

**2.2.2 Work for Hire / IP Assignment**

All work product created by the Contractor under this Agreement — including connector source code, configuration files, tests, documentation, and any other deliverables — constitutes work made for hire under the United States Copyright Act (17 U.S.C. § 101). To the extent any work product does not qualify as work made for hire, the Contractor irrevocably assigns to OvalEdge all right, title, and interest in and to such work product, including all intellectual property rights therein. The Contractor retains no rights in any work product delivered under this Agreement.

**2.2.3 Pre-existing IP**

If the Contractor incorporates any pre-existing tools, libraries, or IP owned by the Contractor into the deliverables, the Contractor must disclose such components to OvalEdge in writing prior to incorporation. The Contractor grants OvalEdge a perpetual, worldwide, royalty-free license to use such pre-existing IP solely as incorporated in the deliverables.

**2.2.4 Confidentiality and Non-Disclosure**

The Contractor agrees to maintain the confidentiality of all OvalEdge Confidential Information (including SDK internals, business information, customer data, and OvalEdge system access credentials) for the duration of this engagement and for a period of five (5) years thereafter. The Contractor shall not disclose OvalEdge Confidential Information to any third party without OvalEdge's prior written consent.

**2.2.5 Non-Solicitation**

During the term of this Agreement and for twelve (12) months thereafter, the Contractor agrees not to directly solicit for employment or engagement any OvalEdge employee or contractor who was involved in the connector development engagement.

**2.2.6 Security Requirements**

The Contractor is subject to all security obligations in Part III of this Guide. In addition:

(a) Personnel assigned to this engagement must not use personal devices for OvalEdge-related development without prior written approval.
(b) All deliverables must pass OvalEdge's automated security scanning (SAST, dependency scan, secrets detection) before acceptance.
(c) The Contractor must promptly notify OvalEdge of any security incident or suspected breach involving OvalEdge materials, credentials, or data.

**2.2.7 Representations and Warranties**

The Contractor represents and warrants that:

(a) It has the authority to enter into this Agreement and the deliverables will not infringe any third-party rights;
(b) The deliverables will be free of material defects and conform to the specifications in the SOW;
(c) The deliverables will not contain malicious code, undisclosed data collection mechanisms, or backdoors;
(d) The Contractor will comply with all applicable laws, including export control and data protection laws.

**2.2.8 Indemnification, Disclaimer, Limitation of Liability, Governing Law**

The indemnification, disclaimer of warranties, limitation of liability, and governing law provisions in Sections 2.1.7 through 2.1.11 apply to this Agreement with "Developer" replaced by "Contractor," except that: (a) the indemnification obligation is mutual; and (b) OvalEdge's limitation of liability cap under this Agreement is the total fees paid to the Contractor under the applicable SOW in the twelve (12) months preceding the claim.

**2.2.9 Term and Termination**

This Agreement is effective from the date of last signature and expires upon completion of the deliverables under the applicable SOW unless earlier terminated. OvalEdge may terminate for convenience with fourteen (14) days' notice. Either party may terminate for cause upon written notice if a material breach is not cured within ten (10) business days. Upon termination: all SDK and repository access is revoked immediately; all OvalEdge confidential materials must be returned or destroyed within five (5) business days.

---

### 2.3 OvalEdge Contributor License Agreement — CLA (Open Source Contributors)

**Effective upon the Contributor electronically executing this CLA via the CLA-assistant bot on their first Pull Request.** This CLA is based on the Apache Software Foundation Individual CLA, adapted for OvalEdge's connector ecosystem.

**2.3.1 Definitions**

"Contribution" means any original work of authorship submitted by the Contributor to OvalEdge for inclusion in the OvalEdge Connector SDK or connector catalog, including source code, tests, documentation, and configuration files. "Contributor" means the individual who electronically accepts this CLA.

**2.3.2 Copyright License Grant**

The Contributor grants to OvalEdge, Inc. a perpetual, worldwide, non-exclusive, no-charge, royalty-free, irrevocable copyright license to reproduce, prepare derivative works of, publicly display, publicly perform, sublicense, and distribute the Contribution and any derivative works thereof.

**2.3.3 Patent License Grant**

The Contributor grants to OvalEdge, Inc. a perpetual, worldwide, non-exclusive, no-charge, royalty-free, irrevocable patent license to make, have made, use, offer to sell, sell, import, and otherwise transfer the Contribution, where such license applies only to patent claims licensable by the Contributor that are necessarily infringed by the Contribution alone or in combination with the SDK.

**2.3.4 Ownership Representation**

The Contributor represents that: (a) the Contribution is the Contributor's original creation; (b) the Contributor has the right to grant the licenses above; (c) the Contribution does not violate any third-party intellectual property rights; and (d) if the Contributor's employer has rights to the Contributor's work, the Contributor has obtained permission to make the Contribution on behalf of that employer or the employer has waived such rights.

**2.3.5 No Compensation; Certificate**

The Contributor acknowledges that this Contribution is voluntary and that no compensation will be paid. Upon successful merge of the Contribution, OvalEdge will issue the Contributor an "OvalEdge Certified Connector Developer" digital certificate at the email address registered at the time of contribution. The certificate does not create any employment, agency, or partnership relationship between the Contributor and OvalEdge.

**2.3.6 Disclaimer and Limitation**

The Contribution is provided "AS IS." The Contributor makes no warranties, express or implied, including warranties of merchantability, fitness for a particular purpose, or non-infringement. OvalEdge's acceptance and use of the Contribution is at OvalEdge's sole discretion.

**2.3.7 Governing Law**

This CLA is governed by the laws of the State of Georgia, USA.

---

## Part III: Mandatory Security and Ethical Development Standards

All developers — regardless of type — must comply with the following standards as a condition of SDK access. Failure to comply constitutes a material breach of the applicable agreement.

### 3.1 Secure Development Lifecycle (SDL) Requirements

**3.1.1 OWASP Compliance**

Connectors must be developed in accordance with the [OWASP Top 10](https://owasp.org/www-project-top-ten/) risk categories. The following are of particular relevance to connector development:

| OWASP Risk | Connector Requirement |
|---|---|
| A01 — Broken Access Control | Validate all connection credentials server-side. Never allow unauthenticated access to connector endpoints. |
| A02 — Cryptographic Failures | Use TLS 1.2 or higher for all network connections. Do not transmit credentials in plaintext. |
| A03 — Injection | Sanitize and validate all inputs passed to queries, API calls, or shell commands. |
| A05 — Security Misconfiguration | Ship connectors with secure defaults. Do not include debug flags, test credentials, or permissive CORS settings in production artifacts. |
| A06 — Vulnerable and Outdated Components | All third-party dependencies must be current and free of known critical CVEs at the time of PR submission. |
| A09 — Security Logging and Monitoring Failures | Log connection lifecycle events (success, failure) at INFO level. Never log credentials or PII. |

**3.1.2 Hardcoded Credentials Prohibition**

Under no circumstances may credentials, API keys, tokens, passwords, or connection strings be hardcoded in connector source code, configuration files, test files, scripts, or documentation. All secrets must be passed via `ConnectionConfig` at runtime. Violations detected by automated scanning will result in immediate PR rejection.

**3.1.3 Dependency Management**

- All third-party libraries used in connectors must be declared in `pom.xml` with explicit version pins.
- Developers must run OWASP Dependency-Check or equivalent before PR submission.
- Transitive dependencies that introduce known high- or critical-severity CVEs must be excluded or upgraded.

**3.1.4 Static Analysis**

Developers are strongly encouraged to run a SAST tool (e.g., SpotBugs, SonarLint, Semgrep) locally before PR submission. OvalEdge's automated pipeline also performs SAST; high-severity findings will block merge.

**3.1.5 Secrets Detection**

Before committing, run a secrets detection scan (e.g., `git-secrets`, `truffleHog`, or GitHub's built-in secret scanning). Do not commit `.env` files, credential files, or keystore files. Add these to `.gitignore` immediately.

### 3.2 Privacy and Data Handling

(a) **Data Minimization:** Connectors must fetch only the metadata and data fields required to fulfill the OvalEdge cataloging function. Do not extract, store, or transmit data beyond the scope of the connector's documented purpose.

(b) **PII Handling:** If the target data source contains personally identifiable information (PII), the connector must handle it in compliance with applicable data protection regulations (including GDPR, CCPA, or other laws applicable to the developer's jurisdiction and the data subject's location). PII must never be logged at any log level.

(c) **No Unauthorized Data Exfiltration:** Connectors must not transmit data to any destination other than the OvalEdge platform instance initiating the request. Third-party telemetry, analytics, or call-home features are strictly prohibited.

(d) **Privacy by Design:** Consider data minimization and access control at the design stage, not as an afterthought. Implement the principle of least privilege when requesting access scopes from target data sources.

### 3.3 Ethical Development Standards

(a) **No Malicious Code:** Connectors must not contain malware, ransomware, spyware, keyloggers, backdoors, or any code designed to harm, deceive, or surveil users or systems.

(b) **No Deceptive Behavior:** Connector behavior must be transparent and consistent with its documented purpose. Connectors must not behave differently in test vs. production environments to circumvent review.

(c) **Respect for Third-Party Terms:** Developers must ensure that building a connector for a given data source complies with that data source's API terms of service and licensing restrictions.

(d) **Responsible Disclosure:** If a developer discovers a security vulnerability in the OvalEdge SDK, a connector, or the OvalEdge platform, they must disclose it privately to **security@ovaledge.com** within 48 hours of discovery. Developers must not publicly disclose vulnerabilities without OvalEdge's consent and must not exploit discovered vulnerabilities.

(e) **No Discrimination or Bias:** Connectors must not implement logic that discriminates against users or data based on protected characteristics.

(f) **Accessibility Awareness:** While connectors are not UI components, any configuration UI contributed alongside a connector should be accessible in accordance with WCAG 2.1 AA guidelines.

### 3.4 Access and Credential Security

(a) JFrog artifact repository credentials are personal and non-transferable. They must not be shared with colleagues, contractors, or any third party.

(b) GitHub access tokens used for SDK repository access must be stored in a secrets manager or credential vault — never in code, shell scripts, or `.env` files.

(c) Developers must use MFA (multi-factor authentication) on their GitHub accounts.

(d) If credentials are compromised, the developer must notify **security@ovaledge.com** within 24 hours. OvalEdge will immediately revoke and reissue credentials.

(e) Upon termination or completion of engagement, developers must revoke their own personal access tokens and confirm credential retirement.

### 3.5 Code of Conduct

All developers participating in the OvalEdge connector ecosystem — including when interacting on GitHub issues, pull requests, and community forums — are expected to conduct themselves professionally and respectfully. Harassment, abusive language, or bad-faith engagement will result in immediate access revocation. OvalEdge's Code of Conduct is available at [https://docs.ovaledge.com/community/code-of-conduct] *(to be published)*.

---

## Part IV: Access Provisioning

### 4.1 GitHub Access

Upon agreement execution:

- **Partners:** Directed to fork the public SDK repository (`https://github.com/ovaledge/oe_csp_sdk`). No private repository access is granted by default.
- **Contractors / Service Providers:** Added to the OvalEdge-managed per-engagement repository with appropriate role (Developer). Access is scoped to that repository only.
- **Open Source Contributors:** Fork the public SDK independently. The CLA-assistant bot is triggered automatically on first PR submission.

### 4.2 Artifact Repository Access (JFrog)

All registered developers receive:

- **Read-only access** to the OvalEdge artifact repository for:
  - `csp-sdk-core` and its dependencies
  - Connector framework libraries
  - SDK BOM (Bill of Materials)

No publish or deploy permissions are granted to external developers. OvalEdge internal teams manage publishing.

**Maven Configuration:**

Add the following to your Maven `~/.m2/settings.xml`:

```xml
<servers>
  <server>
    <id>ovaledge-repo</id>
    <username>${oe.jfrog.username}</username>
    <password>${oe.jfrog.token}</password>
  </server>
</servers>
```

Never hardcode them in `pom.xml`.

### 4.3 Credential Issuance

Provisioned credentials include:

- JFrog artifact repository username and API token (issued via secure email)
- GitHub access invitation (sent to registered GitHub email)

Credentials are valid for the duration of the agreement. Annual credential rotation is required for long-running Partner engagements; OvalEdge will notify 30 days in advance.

---

## Part V: Developer Enablement

This repository is provided with:

- **Getting Started Guide**
- **Sample Connectors** — `monetdb` (JDBC) and `awsconsole` (REST/SDK)
- **Connector Interfaces Reference** — full API contract documentation
- **Security Checklist** — pre-PR submission security verification checklist
- **SDK Version Compatibility Matrix**
- **Support Channel** — `developer@ovaledge.com` for SDK questions and onboarding assistance

OvalEdge does not guarantee a specific response SLA for Partner support queries unless a paid support tier is contracted. Contractors and Service Providers support is handled through the assigned OvalEdge technical member.

---

## Part VI: Compliance Monitoring and Audit

### 6.1 Audit Rights

OvalEdge reserves the right to audit any connector deployed in a production environment by a contractor or any connector submitted for public catalog inclusion. Audit scope includes source code review, dependency analysis, and runtime behavior inspection. Partners deploying connectors in their own environments are not subject to production audit unless the connector is submitted for public catalog inclusion.

### 6.2 Periodic Compliance Review

For active Contractors / Service Providers engagements exceeding six (6) months, OvalEdge will conduct a security compliance review at least annually, covering:

- Dependency vulnerability status
- Adherence to secure coding standards
- Credential hygiene
- Incident history

### 6.3 Violation Response

| Severity | Example Violations | Response |
|---|---|---|
| **Critical** | Hardcoded credentials in production, malicious code, data exfiltration | Immediate access revocation, escalation to OvalEdge legal and security teams |
| **High** | Unpatched critical CVE in production dependency, unauthorized data access | 48-hour cure period; access revocation if not remediated |
| **Medium** | Logging PII, missing SPI registration in submitted connector, missing test coverage | 10-business-day cure period with OvalEdge review |
| **Low** | Missing documentation, non-standard branching | Flagged in code review; must be resolved before merge |

---

## Part VII: Offboarding

### 7.1 Planned Offboarding

For planned engagement ends (contract completion, partner transition):

- OvalEdge notifies the developer 5 business days before access revocation.
- Developer confirms deletion of any locally cached OvalEdge credentials and confidential materials.
- OvalEdge revokes GitHub and JFrog access on the agreed effective date.
- For contractors, OvalEdge confirms final deliverable acceptance before access revocation.

### 7.2 Immediate Offboarding

In cases of agreement breach, security incident, or termination for cause:

- GitHub and JFrog access is revoked within 24 hours.
- Developer is notified via the registered primary contact email.
- Developer must return or certifiably destroy all OvalEdge confidential materials within 5 business days and provide written confirmation.

### 7.3 Surviving Obligations

The following obligations survive termination of any agreement:

- Confidentiality obligations (duration per applicable agreement)
- IP assignment (perpetual)
- Indemnification for pre-termination acts
- Governing law and dispute resolution provisions

---

## Appendix A: Developer Registration Checklist

Use this checklist to confirm all steps are complete before SDK access is granted:

**Registration:**
- [ ] Developer registration email received at developer@ovaledge.com
- [ ] Developer type identified and verification performed
- [ ] Applicable agreement issued and signed by both parties
- [ ] Agreement stored in OvalEdge contract management system

**Access Provisioning:**
- [ ] GitHub access granted (repository per developer type)
- [ ] JFrog credentials issued via secure channel
- [ ] Maven `settings.xml` configuration confirmed by developer
- [ ] Initial build verification (`mvn clean install -DskipTests`) confirmed

**Enablement:**
- [ ] Developer provided links to Getting Started Guide and Connector Interfaces Reference
- [ ] Developer introduced to OvalEdge reviewer (for Contractors / Service Providers)
- [ ] Support channel (developer@ovaledge.com) communicated

---

## Appendix B: Agreement Quick Reference

| Developer Type | Agreement | Signed By (OvalEdge) | Signed By (Developer) | Agreement Delivery |
|---|---|---|---|---|
| Partner / Customer | SDK License Agreement | Head of Connectors | Authorized Representative | Email — PDF for signature |
| Contractor | Contractor & Work-for-Hire Agreement | Head of Connectors | Authorized Representative | Email — PDF for signature |
| Open Source Contributor | Contributor License Agreement (CLA) | Automated (CLA-assistant) | Contributor (electronic) | GitHub PR bot |

---

## Appendix C: Key Contacts

| Function | Contact |
|---|---|
| Developer Registration & Onboarding | developer@ovaledge.com |
| Security Incidents & Vulnerability Disclosure | security@ovaledge.com |
| Legal Questions (Agreements) | Routed via developer@ovaledge.com to OvalEdge legal |
| SDK Technical Support | developer@ovaledge.com |

---

*This document is maintained by the OvalEdge team. Last reviewed: April 2026. Governing law: State of Georgia, USA.*
