# **Table of Contents**

[**&lt;Connector name&gt;**](#connector-name)

[Overview](#overview)

[Connector Details](#connector-details)

[Connector Features](#connector-features)

[Metadata Mapping](#metadata-mapping)

[Set up a Connection](#set-up-a-connection)

[Prerequisites](#prerequisites)

[Whitelisting Ports](#whitelisting-ports)

[External Supporting Files](#external-supporting-files)

[Authentication Type](#authentication-type)

[Service Account User Permissions](#service-account-user-permissions)

[Connection Configuration Steps](#connection-configuration-steps)

[Manage Connector Operations](#manage-connector-operations)

[Crawl/Profile](#crawlprofile)

[Other Operations](#other-operations)

[Limitations](#limitations)

[Connectivity Troubleshooting](#connectivity-troubleshooting)

# Connector name

This article outlines the integration with **\<Connector name\>** connector, enabling streamlined metadata management through features such as \<Features\>.

This connector supports connectivity to **\<Connector name\>** environments deployed across **\<platforms\>** through **\<Connectivity\>**. It provides authentication options, including **\<Authentication Types\>**, ensuring secure access based on deployment requirements. 

| *Note: \<Additional informations\>* |
| :---- |


## Overview

### Connector Details

| Connector Category | RDBMS |
| :---- | :---- |
| OvalEdge Release Supported | Release6.3.4 and later |
| Connectivity *\[How the connection is established with Microsoft SQL Server\]* | \<Connectivity\> |
| Verified \<Connector name\> Version | \<Version\> |

| *Note: The \<Connector name\> connector has been validated with the mentioned "Verified \<Connector name\> Versions" and is expected to be compatible with other supported \<Connector name\> versions. If there are any issues with validation or metadata crawling, please submit a support ticket for investigation and feedback.* |
| :---- |

### Connector Features

| Feature | Availability |  |
| ----- | :---: | ----- |
| Crawling | ✅ |  |
| Delta Crawling | ❌ |  |
| Profiling | ✅ |  |
| Sample Profiling | ✅ |  |
| Query Sheet | ✅ |  |
| Data Preview | ✅ |  |
| Auto Lineage | ✅ |  |
| Manual Lineage | ✅ |  |
| Secure Authentication via Credential Manager | ✅ |  |
| Data Quality | ✅ |  |
| DAM (Data Access Management) | ❌ |  |
| Bridge | ✅ |  |

### Metadata Mapping

The following objects are crawled from **\<Connector name\>** and mapped to the corresponding UI assets.

Table: **\<Metadata Mapping\>**

## Set up a Connection

### Prerequisites

The following are the prerequisites to establish a connection:

#### Whitelisting Ports

Ensure the inbound port “**\<Port Number\>**” is whitelisted for OvalEdge to connect to the **\<Connector name\>** database.

|  *Important: The default port number for \<Connector name\> is \<Port Number\>. If a different port is used, ensure that the updated port number is specified during connection setup, the port is whitelisted, and communication between the system and \<Connector name\> is properly established.* |
| :---- |

#### External Supporting Files

| *Note: The required external JAR files are included as part of the OvalEdge installation artifacts. For driver installation and configuration details, refer to the [Connector Drivers Setup Guide](https://docs.ovaledge.com/connectors/additional-requirements/connector-drivers-setup-guide). Please contact the OvalEdge Team for assistance related to the driver files and configuration setup.* |
| :---- |

#### Authentication Type

The prerequisites vary depending on the authentication method selected for the **\<Connector name\>** connection. To ensure a smooth and successful configuration, the required setup steps and supporting components are listed separately for each authentication type.

**\<Authentication Types\>**

#### Service Account User Permissions

| *Important: It is recommended to use a separate service account to establish the connection to the data source, configured with the following minimum set of permissions.* |
| :---- |

#### 

| 👨‍💻 *Who can provide these permissions? These permissions are typically granted by the \<Connector name\>  administrator, as users may not have the required access to assign them independently.* |
| :---- |

**Table: \<Access Permissions\>**

### 

### Connection Configuration Steps

| *Important: Users are required to have the Connector Creator role in order to configure a new connection.* |
| :---- |

1. Log into OvalEdge, go to Administration \> Connectors, click **\+ (New Connector)**, search for **\<Connector name\>**, and complete the required parameters.

   ***Note:** Fields marked with an asterisk (**\***) are mandatory for establishing a connection.*

| Field Name | Description |
| ----- | ----- |
| Connector Type | By default, "\<Connector name\>" is displayed as the selected connector type. |
| **Connector Settings** |  |
| Authentication | The following types of authentication are supported  for \<Connector name\>: \<Authentication Types\> |
| Credential Manager**\*** | Select the desired credentials manager from the drop-down list. Relevant parameters will be displayed based on your selection. Supported Credential Managers: OE Credential Manager AWS Secrets Manager HashiCorp Vault Azure Key Vault For more details, click [here](https://docs.ovaledge.com/connectors/additional-requirements/credential-manager-configuration).  |
| License Add Ons |  **Auto Lineage** Supported **Data Quality** Supported **Data Access** Not Supported  Select the checkbox for **Auto Lineage Add-On** to build data lineage automatically. Select the checkbox for **Data Quality Add-On** to identify data quality issues using data quality rules and anomaly detection. For more details, click [here](https://docs.ovaledge.com/connectors/introduction-to-connectors/setup-and-connectivity/license-types-and-add-ons).  |
| Connector Name**\*** | Enter a unique name for the \<Connector name\>connection               |
| Connector Environment | Select the environment (Example: PROD, STG) configured for the connector. For more details, click [here](https://docs.ovaledge.com/connectors/introduction-to-connectors/setup-and-connectivity/prerequisites#connector-environment).  |
| Connector description | Enter a brief description of the connector. |
| Server**\*** | Enter the \<Connector name\> database server name or IP address (Example: xxxx-sqlserver.xxxx4ijtzasl.xx-south-1.rds.amazonaws.com or 1xx.xxx.1.x0). |
| Port**\*** | By default, the port number for the \<Connector name\>, "**\<Port Number\>**" is auto-populated. If required, the port number can be modified as per custom port number that is configured for \<Connector name\>. |
| Database**\*** | Enter the database name to which the service account user has access within the\<Connector name\>. |
| Domain | Enter the qualified \<Connector name\> domain name.**Note:** This field will appear, when the authentication type is selected as Windows authentication and the OvalEdge installed Environment is selected as linux/Unix. |
| Connection String | Configure the connection string for the SQL Server database: **Automatic Mode:** The system generates a connection string based on the provided credentials. **Manual Mode:** Enter a valid connection string manually. Replace placeholders with actual database details. {sid} refers to **Database Name.** |
| Plugin Server | Enter the server’s name when running as a plugin server. |
| Plugin Port | Enter the port number on which the plugin is running. |

| Default Governance Roles |  |
| ----- | :---- |
| Default Governance Roles**\*** | Select the appropriate users or teams for each governance role from the drop-down list. All users and teams configured in OvalEdge Security are displayed for selection. |
| **Admin Roles** |  |
| Admin Roles**\*** | Select one or more users from the dropdown list for Integration Admin and Security & Governance Admin. All users configured in OvalEdge Security are available for selection. |
| **No of Archive Objects** |  |
| No Of Archive Objects**\*** | This shows the number of recent metadata changes to a dataset at the source. By default, it is off. To enable it, toggle the **Archive** button and specify the number of objects to archive. **Example:** Setting it to 4 retrieves the last four changes, displayed in the 'Version' column of the 'Metadata Changes' module. |
| **Bridge** |  |
| Select Bridge**\*** | **If applicable,** select the bridge from the drop-down list. The drop-down list displays all active bridges configured in OvalEdge. These bridges enable communication between data sources and OvalEdge without altering firewall rules. |

   

2. After entering all connection details, the following actions can be performed:  
* Click **Validate** to verify the connection.  
* Click **Save** to store the connection for future use.  
* Click **Save & Configure** to apply additional settings before saving.  
3. The saved connection will appear on the Connectors home page.

## Manage Connector Operations

### Crawl/Profile

| *Important: To perform crawl and profile operations, users must be assigned the Integration Admin role.* |
| :---- |

The **Crawl/Profile** button allows users to select one or more schemas for crawling and profiling. 

1. Navigate to the Connectors page and click **Crawl/Profile.**  
2. Select the schemas to crawl.  
3. The **Crawl** option is selected by default. Click the **Crawl & Profile** radio button if you want both operations.  
4. Click **Run** to collect metadata from the connected source and load it into the OvalEdge Data Catalog.  
5. After a successful crawl, the information appears in the **Data Catalog** \> **Databases** tab.

The **Schedule** checkbox allows automated crawling and profiling at defined intervals, from a minute to a year.

1. Click the **Schedule** checkbox to enable the **Select Period** drop-down.

2. Select a time period for the operation from the drop-down menu.

3. Click **Schedule** to initiate metadata collection from the connected source.

4. The system will automatically execute the selected operation (**Crawl** or **Crawl & Profile**) at the scheduled time.

### Other Operations

The **Connectors page** in OvalEdge provides a centralized view of all configured connectors, including their health status.

**Managing connectors includes:**

* **Connectors Health**: Displays the current status of each connector using a green icon for active connections and a red icon for inactive connections, helping to monitor the connectivity with data sources.  
* **Viewing**: Click the **Eye** icon next to the connector name to view connector details, including databases, tables, columns, and codes.

**Nine Dots Menu Options**:

To view, edit, validate, build lineage, configure, or delete connectors, click on the Nine Dots menu.

* **Edit Connector**: Update and revalidate the data source.  
* **Validate Connector**: Check the connection's integrity.  
* **Settings**: Modify connector settings.  
  * **Crawler**: Configure data extraction.  
  * **Profiler**: Customize data profiling rules and methods.  
  * **Query Policies**: Define query execution rules based on roles.  
  * **Access Instructions**: Add notes on how data can be accessed.  
  * **Business Glossary Settings**: Manage term associations at the connector level.  
  * **Anomaly Detection Settings**: Configure anomaly detection preferences at the connector level.  
  * **Others**: Configure notification recipients for metadata changes.  
* **Build Lineage:** Automatically build data lineage using source code parsing.  
* **Delete Connector**: Remove a connector with confirmation.

## Limitations

| Feature | Description |
| ----- | ----- |
| **Crawling** |  |
| **Profiling** |  |
| **Lineage** |  |
| **Data Quality** |  |
| **Data Preview** |  |

### Connectivity Troubleshooting

If incorrect parameters are entered, error messages may appear. Ensure all inputs are accurate to resolve these issues. If issues persist, contact the assigned support team.

| S.No. | Error Message(s) | Error Description & Resolution |
| :---: | ----- | ----- |
|  |  |  |
|  |  |  |

\*\*\*End of the document\*\*\*