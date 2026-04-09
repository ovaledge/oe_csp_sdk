# OvalEdge Connector SDK Development Model

> **INTERNAL USE ONLY — Do not publish on docs.ovaledge.com**
> This document defines the internal operating model for how OvalEdge's Connectors team works with each class of external developer. Sections marked [Internal] contain operational guidance for the OvalEdge team.

---

This document defines the different user operating models for developing connectors using the OvalEdge Connectors SDK, covering repository strategy, ownership, development workflows, quality gates, and governance across all contributor types.

---

## Master SDK

- A **public SDK [repository](https://github.com/ovaledge/oe_csp_sdk)** is hosted on GitHub.
- All downstream repositories are **forked from this SDK**.
- Updates to the SDK are consumed by downstream forks via **upstream synchronization** (see [Section 5 — Upstream Sync Process](#5-upstream-sync-process)).

---

## Developer Persona Summary

| Attribute | Partners / Customers | Sister Companies | Contractors / Service Providers | Open Source Contributors |
|---|---|---|---|---|
| Relationship | Independent | Affiliated | Contracted (paid) | Community (unpaid) |
| Repository owned by | Partner | OvalEdge | OvalEdge | Developer (fork) |
| Connectors owned by | Partner | OvalEdge | OvalEdge | OvalEdge (licensed) |
| Contributes back to OvalEdge? | Optional | Mandatory | Mandatory | Mandatory (via PR) |
| OvalEdge review required? | For public catalog only | Yes | Yes | Yes |
| Agreement type | SDK License Agreement | Internal Collaboration Framework | Contractor / Work-for-Hire Agreement | Contributor License Agreement (CLA) |
| Certification | Not applicable | Not applicable | Not applicable | OvalEdge Certified Connector Developer |

---

## 1. Partners / Customers (Independent Delivery Model)

### 1.1 Ownership & Responsibility

- Partners/Customers independently develop, deploy, and maintain connectors for their own environments.
- Connectors **do not flow back** into the OvalEdge codebase unless the partner elects to submit them for public catalog inclusion.
- Partners are fully responsible for the security, correctness, and maintenance of their connectors in production.

### 1.2 Repository Strategy

- Partner/Customer forks the public SDK into their own GitHub organization.
- The fork is configured to sync with the upstream SDK (see [Section 5](#5-upstream-sync-process)).
- Repository ownership and access control remain with the **Partner/Customer**.

### 1.3 Branching & Development Workflow

- Feature development occurs in **feature branches** named `feature/<connector-name>`.
- Pull Requests are raised against the **`dev` branch** in the Partner/Customer's own repository.
- Partners manage their own code review and merge process internally.
- Deployment is performed from the **`dev` branch** (or a release branch — Partner's discretion).

### 1.4 Public Catalog Submission (Optional)

Partners who wish to contribute a connector to the OvalEdge public catalog must:

1. Raise a Pull Request from their fork against the `dev` branch of the upstream `ovaledge/oe_csp_sdk` repository.
2. Pass all automated GitHub Actions validation checks (connector contract, security scan, build verification).
3. Complete OvalEdge manual code review (see [Section 4 — Code Review Standards](#4-code-review-standards-and-quality-gates)).
4. Sign any supplementary public contribution addendum to their SDK License Agreement if not already in place.

IP ownership of the connector remains with the Partner post-publication; OvalEdge receives a distribution license.

### 1.5 [Internal] OvalEdge Responsibilities

- Provision the SDK License Agreement and JFrog credentials upon partner registration.
- Respond to public catalog PR submissions within **10 business days**.
- Notify partners of SDK breaking changes at least **30 days in advance** via the registered developer email.

---

## 2. OvalEdge Sister Companies (Shared Delivery Model)

### 2.1 Ownership & Responsibility

- Sister companies build connectors **on behalf of OvalEdge**.
- All connectors are **contributed back to OvalEdge**'s codebase and become OvalEdge intellectual property.
- OvalEdge provides direction, requirements, and quality standards.

### 2.2 Repository Strategy

- OvalEdge creates and owns a **central shared repository** forked from the public SDK.
- All sister companies working on connectors share this repository.
- Access is granted by OvalEdge's Connectors team lead.

### 2.3 Branching & Governance

- Sister companies develop in **feature branches** named `feature/<connector-name>`.
- PRs are raised to the **`dev` branch**.
- OvalEdge performs **code review, validation, and merge approval** (see [Section 4](#4-code-review-standards-and-quality-gates)).
- OvalEdge Connectors team lead has final merge authority.

### 2.4 [Internal] OvalEdge Responsibilities

- Provide sprint assignments, requirements, and technical guidance.
- Complete code review within **5 business days** of PR submission.
- Provide access to OvalEdge development/staging environments for integration testing.

---

## 3. Service Companies / Contractors (Controlled Delivery Model)

### 3.1 Ownership & Responsibility

- External developers work **under OvalEdge's direction** per a signed Contractor Agreement.
- All deliverables are **work-for-hire and owned by OvalEdge**.
- Contractors must adhere to OvalEdge's security, quality, and coding standards.

### 3.2 Repository Strategy

- OvalEdge creates and owns a **separate repository per vendor/contractor engagement**.
- Each repository is scoped to the engagement and access expires upon contract termination.
- Access is granted by OvalEdge's Connectors team — access requests go to developer@ovaledge.com.

### 3.3 Branching & Governance

- Development occurs in **feature branches** named `feature/<connector-name>`.
- PRs are raised to the **`dev` branch**.
- OvalEdge performs code review and merge (see [Section 4](#4-code-review-standards-and-quality-gates)).
- Contractors may not merge their own PRs.

### 3.4 [Internal] OvalEdge Responsibilities

- Issue and execute the Contractor / Work-for-Hire Agreement before granting repository access.
- Complete code review within **5 business days** of PR submission.
- Conduct access revocation within **24 hours** of contract end date.

---

## 4. Open Source Contributors (Community Model)

### 4.1 Ownership & Responsibility

- Open Source Contributors are **unpaid community members** who build connectors on a voluntary basis.
- All contributions are licensed to OvalEdge under the **OvalEdge Contributor License Agreement (CLA)** — OvalEdge owns and distributes the resulting connector.
- Contributors receive an **"OvalEdge Certified Connector Developer" certificate** upon successful merge of their contribution.
- Contributors have no ongoing maintenance obligation, though continued engagement is welcomed.

### 4.2 Repository Strategy

- Contributors **fork the public SDK** into their own GitHub account.
- Development occurs in the contributor's fork.

### 4.3 Branching & Governance

- Development occurs in **feature branches** named `feature/<connector-name>`.
- PRs are raised from the contributor's fork to the **`dev` branch** of the upstream `ovaledge/oe_csp_sdk`.
- The contributor must sign the OvalEdge CLA (via the automated CLA-assistant bot on the PR) before the PR will be reviewed.
- OvalEdge handles review and merge (see [Section 4](#4-code-review-standards-and-quality-gates)).

### 4.4 Contributor Recognition

Upon successful merge:

- OvalEdge issues the **"OvalEdge Certified Connector Developer" digital certificate** to the contributor's registered email.
- The contributor's name (or GitHub handle, at their discretion) is listed in the connector's attribution metadata.
- OvalEdge may (with permission) feature the contributor in community communications.

### 4.5 [Internal] OvalEdge Responsibilities

- Maintain CLA-assistant bot on the public repository.
- Respond to open source PRs within **15 business days**.
- Issue certificates within **5 business days** of merge.

---

## 4. Code Review Standards and Quality Gates

All PRs — regardless of developer persona — must pass the following gates before merge:

### 4.1 Automated Checks (GitHub Actions)

The following checks run automatically on every PR:

| Check | Description |
|---|---|
| **Build verification** | `mvn clean install` passes across all modules. |
| **Unit test pass** | All connector unit tests pass with no failures. |
| **SAST scan** | Static Application Security Testing via the configured scanner (e.g., CodeQL or Semgrep). Zero high-severity findings. |
| **Dependency vulnerability scan** | OWASP Dependency-Check or equivalent. No known critical CVEs in connector dependencies. |
| **Connector contract validation** | Automated check that `AppsConnector`, `MetadataService`, and `QueryService` are fully implemented with required methods present. |
| **SPI registration check** | `META-INF/services/` file is present and references a valid FQCN. |
| **No hardcoded secrets** | Secrets detection scan (e.g., git-secrets or truffleHog). PR fails if credentials, tokens, or API keys are detected in code. |

PRs with failing automated checks will **not proceed to manual review** until all issues are resolved.

### 4.2 Manual Review Criteria

OvalEdge reviewers assess the following:

| Criterion | Standard |
|---|---|
| **Connector contract compliance** | All required interface methods implemented correctly per the [Connector Interfaces Reference]. No silent exception swallowing. |
| **Error handling** | Errors surfaced with meaningful messages per the conventions in Tab 1, Section 5.4. |
| **Logging hygiene** | No credentials, tokens, or PII logged at any level. Logging follows Tab 1 Section 5.5 conventions. |
| **Security practices** | No hardcoded credentials. Input validation on all connection config parameters. Safe handling of external data. Follows OWASP Top 10 guidelines. |
| **Code quality** | Readable, maintainable Java. No unnecessary complexity. Classes and methods are appropriately sized. |
| **Test coverage** | Unit tests cover happy path, error scenarios, and edge cases for all public methods. |
| **Documentation** | `README.md` or `INSTRUCTIONS.txt` present. `@SdkConnector` annotation populated. Release properties file included. |

### 4.3 Code Review SLA

| Developer Persona | Review SLA |
|---|---|
| Sister Companies / Contractors | 5 business days from PR submission |
| Partners (public catalog submission) | 10 business days from PR submission |
| Open Source Contributors | 15 business days from PR submission |

If a review will exceed the SLA due to backlog, the OvalEdge reviewer will post a comment on the PR with an updated estimated timeline.

### 4.4 Testing Requirements Before Raising a PR

Before raising a PR, developers must confirm:

- [ ] `mvn clean install` passes locally with no errors.
- [ ] The connector appears in `GET /v1/info` when running `csp-api` locally.
- [ ] `validateConnection()` returns a successful response with valid test credentials.
- [ ] `getSupportedObjects()`, `getContainers()`, `getObjects()`, and `getFields()` return correct results.
- [ ] `fetchData()` returns correct results for at least one supported entity type.
- [ ] No credentials, tokens, or PII are present in code or logs.
- [ ] The PR description includes: connector name, target data source, test results (paste console output, a log snippet, or a screenshot — any format is acceptable), and any known limitations.

---

## 5. Upstream Sync Process

As OvalEdge publishes updates to the master SDK, all downstream forks must synchronize to stay current. Developers are responsible for syncing their forks before beginning new work.

### 5.1 Configuring Upstream Remote

```bash
git remote add upstream https://github.com/ovaledge/oe_csp_sdk.git
git remote -v   # Verify both origin and upstream are listed
```

### 5.2 Syncing Changes

```bash
git fetch upstream
git checkout dev           # Or your working branch
git rebase upstream/main   # Or upstream/dev, depending on target
```

Resolve any merge conflicts before continuing development.

### 5.3 Breaking Change Notifications

- OvalEdge will publish SDK release notes in the repository `CHANGELOG.md` and notify registered developers via **developer@ovaledge.com** at least **30 calendar days** before a major version release containing breaking changes.
- Major version changes may require connector updates. OvalEdge will provide a migration guide with each major release.
- Minor and patch releases are backward-compatible and do not require connector changes.

### 5.4 Version Compatibility

- Connectors must declare their target `csp-sdk-core` version in their `pom.xml`.
- A version compatibility matrix (SDK version ↔ OvalEdge platform version) will be published at `https://docs.ovaledge.com/connectors/sdk-compatibility`.
- Developers should verify compatibility before starting work on a new connector.

---

## 6. Release Process for Public Connectors

When a connector built by a Sister Company, Contractor, or Open Source Contributor is ready for inclusion in the OvalEdge public catalog, the following steps apply:

1. **Feature-complete PR** is merged to `dev` branch after passing all quality gates.
2. **Integration testing** is performed by OvalEdge in the staging environment against a live instance of the target data source.
3. **Release candidate** is cut to a `release/<version>` branch.
4. **Final security review** is conducted (automated + manual).
5. **Merge to `main`** upon passing all checks.
6. **Release published** — connector is packaged in the next OvalEdge SDK release and published to the connector catalog.
7. **Notification** — registered developers are notified via email of the new connector's availability.

---

## 7. Conflict Resolution

If disputes arise over code ownership, contribution scope, or merge decisions:

1. The developer raises the concern directly with the assigned OvalEdge Connectors team reviewer.
2. If unresolved, the matter is escalated to the **Head of Connectors** within 5 business days.
3. For Partner disputes involving SDK License Agreement interpretation, the matter is handled per the dispute resolution clause in the SDK License Agreement (governed by Georgia law).
4. OvalEdge's decision on merge approval and public catalog inclusion is final.

---

> *For questions about this operating model, contact the OvalEdge Connectors team at developer@ovaledge.com*
