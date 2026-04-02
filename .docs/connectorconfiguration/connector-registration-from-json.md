# Connector registration from JSON

Connectors are auto-registered at startup from JSON descriptors. The app loads all `configs/*.json` on the classpath and, for each connector whose DTO is provided by loaded JARs but not yet in the DB, inserts into `connector_master`, `conn_crawler_setting_config`, and `connector_crawler_option_mapping`.

## Template for a new connector

- **Base template:** [examples/connectors/oe_connector.json](examples/connectors/oe_connector.json) — placeholder structure with possible values and field descriptions (`_fieldDescriptions`). Copy and replace placeholders.
- **Example:** [examples/connectors/mysql.json](examples/connectors/mysql.json) — filled-in MySQL connector for reference.

1. Copy **oe_connector.json** to your module as `configs/<your_connector>.json` (e.g. `src/main/resources/configs/myconnector.json`). Remove the `_template`, `_description`, and `_fieldDescriptions` keys if you prefer.
2. Replace placeholders: `your_connector_key`, `Your Connector`, `com.ovaledge.csp.dto.YourDto`, icon (`icons/your-connector.png`), etc. Use **mysql.json** as a concrete example.
3. **Set `connectorMaster.dtoRegisterName`** to the fully qualified class name of your connector DTO (must match the class in the connector JAR).
4. Keep **crawlerSettings** as in the template or copy from mysql.json.
