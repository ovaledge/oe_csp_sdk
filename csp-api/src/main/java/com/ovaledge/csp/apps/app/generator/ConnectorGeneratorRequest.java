package com.ovaledge.csp.apps.app.generator;

import java.util.List;

public class ConnectorGeneratorRequest {

    private String connectorName;
    private String repoRoot;
    private Boolean overwriteExistingModule;
    private List<String> objectKinds;
    private ManifestInput manifest;
    private List<ReferenceInput> references;

    public ConnectorGeneratorRequest() {
    }

    public String getConnectorName() {
        return connectorName;
    }

    public void setConnectorName(String connectorName) {
        this.connectorName = connectorName;
    }

    public String getRepoRoot() {
        return repoRoot;
    }

    public void setRepoRoot(String repoRoot) {
        this.repoRoot = repoRoot;
    }

    public Boolean getOverwriteExistingModule() {
        return overwriteExistingModule;
    }

    public void setOverwriteExistingModule(Boolean overwriteExistingModule) {
        this.overwriteExistingModule = overwriteExistingModule;
    }

    public List<String> getObjectKinds() {
        return objectKinds;
    }

    public void setObjectKinds(List<String> objectKinds) {
        this.objectKinds = objectKinds;
    }

    public ManifestInput getManifest() {
        return manifest;
    }

    public void setManifest(ManifestInput manifest) {
        this.manifest = manifest;
    }

    public List<ReferenceInput> getReferences() {
        return references;
    }

    public void setReferences(List<ReferenceInput> references) {
        this.references = references;
    }

    public static class ManifestInput {
        private ConnectorMasterInput connectorMaster;
        private CrawlerSettingsInput crawlerSettings;
        private List<CrawlerOptionInput> crawlerOptions;

        public ConnectorMasterInput getConnectorMaster() {
            return connectorMaster;
        }

        public void setConnectorMaster(ConnectorMasterInput connectorMaster) {
            this.connectorMaster = connectorMaster;
        }

        public CrawlerSettingsInput getCrawlerSettings() {
            return crawlerSettings;
        }

        public void setCrawlerSettings(CrawlerSettingsInput crawlerSettings) {
            this.crawlerSettings = crawlerSettings;
        }

        public List<CrawlerOptionInput> getCrawlerOptions() {
            return crawlerOptions;
        }

        public void setCrawlerOptions(List<CrawlerOptionInput> crawlerOptions) {
            this.crawlerOptions = crawlerOptions;
        }
    }

    public static class ConnectorMasterInput {
        private String oeDocs;
        private String shortDescription;
        private String protocol;
        private String oeConnCategory;
        private String srcConnCategory;
        private String usageCostModel;
        private Boolean active;
        private Boolean crawling;
        private Boolean querySheet;
        private Boolean dataAccess;
        private Boolean autoLineage;
        private Boolean profiling;
        private Boolean dataQuality;
        private List<String> authenticationTypes;
        private List<String> credentialManagers;

        public String getOeDocs() { return oeDocs; }
        public void setOeDocs(String oeDocs) { this.oeDocs = oeDocs; }
        public String getShortDescription() { return shortDescription; }
        public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }
        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }
        public String getOeConnCategory() { return oeConnCategory; }
        public void setOeConnCategory(String oeConnCategory) { this.oeConnCategory = oeConnCategory; }
        public String getSrcConnCategory() { return srcConnCategory; }
        public void setSrcConnCategory(String srcConnCategory) { this.srcConnCategory = srcConnCategory; }
        public String getUsageCostModel() { return usageCostModel; }
        public void setUsageCostModel(String usageCostModel) { this.usageCostModel = usageCostModel; }
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
        public Boolean getCrawling() { return crawling; }
        public void setCrawling(Boolean crawling) { this.crawling = crawling; }
        public Boolean getQuerySheet() { return querySheet; }
        public void setQuerySheet(Boolean querySheet) { this.querySheet = querySheet; }
        public Boolean getDataAccess() { return dataAccess; }
        public void setDataAccess(Boolean dataAccess) { this.dataAccess = dataAccess; }
        public Boolean getAutoLineage() { return autoLineage; }
        public void setAutoLineage(Boolean autoLineage) { this.autoLineage = autoLineage; }
        public Boolean getProfiling() { return profiling; }
        public void setProfiling(Boolean profiling) { this.profiling = profiling; }
        public Boolean getDataQuality() { return dataQuality; }
        public void setDataQuality(Boolean dataQuality) { this.dataQuality = dataQuality; }
        public List<String> getAuthenticationTypes() { return authenticationTypes; }
        public void setAuthenticationTypes(List<String> authenticationTypes) { this.authenticationTypes = authenticationTypes; }
        public List<String> getCredentialManagers() { return credentialManagers; }
        public void setCredentialManagers(List<String> credentialManagers) { this.credentialManagers = credentialManagers; }
    }

    public static class CrawlerSettingsInput {
        private Boolean relationship;
        private Boolean procnfunc;
        private Boolean reports;
        private Boolean reportcolumns;
        private Boolean indexes;
        private Boolean settings;
        private Boolean fullcrawl;
        private Boolean incrementalcrawl;

        public Boolean getRelationship() { return relationship; }
        public void setRelationship(Boolean relationship) { this.relationship = relationship; }
        public Boolean getProcnfunc() { return procnfunc; }
        public void setProcnfunc(Boolean procnfunc) { this.procnfunc = procnfunc; }
        public Boolean getReports() { return reports; }
        public void setReports(Boolean reports) { this.reports = reports; }
        public Boolean getReportcolumns() { return reportcolumns; }
        public void setReportcolumns(Boolean reportcolumns) { this.reportcolumns = reportcolumns; }
        public Boolean getIndexes() { return indexes; }
        public void setIndexes(Boolean indexes) { this.indexes = indexes; }
        public Boolean getSettings() { return settings; }
        public void setSettings(Boolean settings) { this.settings = settings; }
        public Boolean getFullcrawl() { return fullcrawl; }
        public void setFullcrawl(Boolean fullcrawl) { this.fullcrawl = fullcrawl; }
        public Boolean getIncrementalcrawl() { return incrementalcrawl; }
        public void setIncrementalcrawl(Boolean incrementalcrawl) { this.incrementalcrawl = incrementalcrawl; }
    }

    public static class CrawlerOptionInput {
        private String optionType;
        private String optionKey;

        public String getOptionType() { return optionType; }
        public void setOptionType(String optionType) { this.optionType = optionType; }
        public String getOptionKey() { return optionKey; }
        public void setOptionKey(String optionKey) { this.optionKey = optionKey; }
    }

    public static class ReferenceInput {
        private String type;
        private String title;
        private String url;
        private String text;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }
}
