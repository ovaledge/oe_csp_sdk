package com.ovaledge.csp.validation;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Properties;
import java.util.stream.Stream;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/**
 * Maven Mojo to generate release-csp-sdk.properties with Git information.
 * Replaces CspSdkGitTest.java.
 */
@Mojo(name = "generate-csp-sdk-git-info", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = false)
public class CspSdkGitInfoGenerator extends AbstractMojo {

    private static final String RELEASE_FILE = "release-csp-sdk.properties";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    private File basedir;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private File outputDirectory;

    @Parameter(property = "skip.csp.sdk.git.info", defaultValue = "false")
    private boolean skip;

    public static void main(String[] args) {
        try {
            CspSdkGitInfoGenerator generator = new CspSdkGitInfoGenerator();
            String basedirPath = System.getProperty("project.basedir", ".");
            String buildDir = System.getProperty("project.build.directory", basedirPath + "/target");
            generator.basedir = new File(basedirPath);
            generator.outputDirectory = new File(buildDir);
            generator.setLog(new SimpleSystemLog());
            generator.execute();
        } catch (Exception e) {
            System.err.println("Error executing CspSdkGitInfoGenerator: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static class SimpleSystemLog implements org.apache.maven.plugin.logging.Log {
        @Override
        public boolean isDebugEnabled() { return false; }
        @Override
        public void debug(CharSequence content) { }
        @Override
        public void debug(CharSequence content, Throwable error) { }
        @Override
        public void debug(Throwable error) { }
        @Override
        public boolean isInfoEnabled() { return true; }
        @Override
        public void info(CharSequence content) { System.out.println(content); }
        @Override
        public void info(CharSequence content, Throwable error) { System.out.println(content); }
        @Override
        public void info(Throwable error) { error.printStackTrace(); }
        @Override
        public boolean isWarnEnabled() { return true; }
        @Override
        public void warn(CharSequence content) { System.err.println("[WARN] " + content); }
        @Override
        public void warn(CharSequence content, Throwable error) { System.err.println("[WARN] " + content); }
        @Override
        public void warn(Throwable error) { error.printStackTrace(); }
        @Override
        public boolean isErrorEnabled() { return true; }
        @Override
        public void error(CharSequence content) { System.err.println("[ERROR] " + content); }
        @Override
        public void error(CharSequence content, Throwable error) { System.err.println("[ERROR] " + content); }
        @Override
        public void error(Throwable error) { error.printStackTrace(); }
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping csp-sdk git info generation");
            return;
        }

        try {
            getLog().info("Updating " + RELEASE_FILE);

            MavenXpp3Reader reader = new MavenXpp3Reader();
            File parentPom = new File(basedir, "../pom.xml");
            if (!parentPom.exists()) {
                throw new MojoExecutionException("Cannot find parent pom.xml at " + parentPom.getAbsolutePath());
            }

            Model model;
            try {
                model = reader.read(new FileReader(parentPom));
            } catch (org.codehaus.plexus.util.xml.pull.XmlPullParserException e) {
                throw new MojoExecutionException("Error parsing parent pom.xml", e);
            }

            Properties mprops = model.getProperties();
            String pomVersion = model.getVersion();
            String cspSdkCoreVersion = mprops.getProperty("csp-sdk-core.version", "");

            String branch = "";
            String gitCommit = "";
            String gitCommitCode = "";
            String buildUser = "";
            String buildUrl = "";
            String jobReleaseInfo = "";

            File branchinfo = new File(basedir, "../branchinfo.properties");
            File buildinfo = new File(basedir, "../buildinfo.properties");

            if (branchinfo.exists()) {
                getLog().info("Found branchinfo.properties, getting git details");
                Properties ci = readProperties(branchinfo);
                branch = ci.getProperty("GIT_BRANCH", "").replaceAll("origin/", "");
                gitCommit = ci.getProperty("GIT_COMMIT", "");
                buildUser = ci.getProperty("BUILD_TRIGGED_BY", "");
                buildUrl = normalizeBuildUrl(ci.getProperty("BUILD_URL", ""));
                jobReleaseInfo = ci.getProperty("RELEASE_NUMBER", "");
            } else if (buildinfo.exists()) {
                getLog().info("Found buildinfo.properties, getting git details");
                Properties ci = readProperties(buildinfo);
                branch = ci.getProperty("GIT_BRANCH", "").replaceAll("origin/", "");
                gitCommit = ci.getProperty("GIT_COMMIT", "");
                buildUser = ci.getProperty("BUILD_TRIGGED_BY", "");
                buildUrl = normalizeBuildUrl(ci.getProperty("BUILD_URL", ""));
                jobReleaseInfo = ci.getProperty("RELEASE_NUMBER", "");
            } else {
                getLog().info("Reading git details from parent .git");
                try {
                    FileRepositoryBuilder builder = new FileRepositoryBuilder();
                    Repository repository = builder
                            .readEnvironment()
                            .findGitDir(new File(basedir, ".."))
                            .build();
                    RevWalk walk = new RevWalk(repository);
                    ObjectId head = repository.resolve(Constants.HEAD);
                    if (head != null) {
                        RevCommit commit = walk.parseCommit(head);
                        branch = repository.getBranch();
                        gitCommit = commit.getName();
                        walk.close();
                    }
                    repository.close();
                } catch (Exception e) {
                    getLog().warn("Error reading git details: " + e.getMessage());
                }
            }

            if (gitCommit != null && gitCommit.length() >= 7) {
                gitCommitCode = gitCommit.substring(0, 7);
            }

            File releasefile = new File(basedir, "src/main/resources/" + RELEASE_FILE);
            File releasefileTarget = new File(outputDirectory, "classes/" + RELEASE_FILE);
            releasefileTarget.getParentFile().mkdirs();

            Properties props = readProperties(releasefile);

            if (jobReleaseInfo == null || jobReleaseInfo.trim().isEmpty()) {
                jobReleaseInfo = props.getProperty("csp-sdk.release.info", branch != null ? branch : "").replace("Release", "");
            }

            String[] releaseTypeArray = jobReleaseInfo.split("\\.");
            String releaseInfo = "Release".concat(jobReleaseInfo);
            String releaseNumber = jobReleaseInfo.replace(".", "");
            while (releaseNumber.length() < 4) {
                releaseNumber += "0";
            }

            String releaseType = "main";
            if (releaseTypeArray.length > 0) {
                try {
                    int lastNum = Integer.parseInt(releaseTypeArray[releaseTypeArray.length - 1]);
                    if (releaseTypeArray.length == 3) {
                        releaseType = (lastNum == 0) ? "main" : "service";
                    } else if (releaseTypeArray.length > 3) {
                        releaseType = (lastNum == 0) ? "service" : "hotfix";
                    }
                } catch (NumberFormatException e) {
                    getLog().warn("Error parsing release number: " + e.getMessage());
                }
            }

            String releaseVersion = pomVersion != null && !pomVersion.isEmpty()
                    ? pomVersion
                    : props.getProperty("csp-sdk.release.version", branch != null ? branch : "");
            String releaseMain = props.getProperty("csp-sdk.release.main", branch != null ? branch : "");
            String releaseService = props.getProperty("csp-sdk.release.service", branch != null ? branch : "");
            String tagname = (branch != null && branch.startsWith("tag")) ? branch : "";

            String fileContent = ""
                    + "csp-sdk.release.info=" + releaseInfo + "\n"
                    + "csp-sdk.release.number=" + releaseNumber + "\n"
                    + "csp-sdk.release.version=" + releaseVersion + "\n"
                    + "csp-sdk.release.main=" + releaseMain + "\n"
                    + "csp-sdk.release.service=" + releaseService + "\n"
                    + "csp-sdk.release.type=" + releaseType + "\n"
                    + "csp-sdk.git.branch=" + (branch != null ? branch : "") + "\n"
                    + "csp-sdk.git.tag=" + tagname + "\n"
                    + "csp-sdk.git.commit=" + gitCommitCode + "\n"
                    + "csp-sdk.git.commit.full=" + (gitCommit != null ? gitCommit : "") + "\n"
                    + "csp-sdk.git.buildtime=" + formatAsString("yyyy-MM-dd HH:mm:ss") + "\n"
                    + "csp-sdk.csp-sdk-core.version=" + cspSdkCoreVersion + "\n"
                    + "csp-sdk.build.user=" + buildUser + "\n"
                    + "csp-sdk.build.url=" + buildUrl;

            getLog().info("-------------------------------- Maven Information: csp-sdk -------------------------");
            getLog().info("oe-dependencies Id : " + (model.getParent() != null ? model.getParent().getId() : "N/A"));
            getLog().info("csp-sdk Id : " + model.getId());
            getLog().info(fileContent);

            writeFile(releasefile, fileContent);
            writeFile(releasefileTarget, fileContent);

            File classesDir = new File(outputDirectory, "classes");
            if (classesDir.exists()) {
                try (Stream<Path> paths = Files.walk(Paths.get(classesDir.getAbsolutePath()))) {
                    long otherReleaseProps = paths.filter(Files::isRegularFile)
                            .map(p -> p.getFileName().toString())
                            .filter(name -> name.startsWith("release-") && name.endsWith(".properties")
                                    && !name.equals(RELEASE_FILE))
                            .count();
                    if (otherReleaseProps > 0) {
                        getLog().warn("Unexpected connector-level release metadata under assembly target/classes");
                    }
                }
            }

        } catch (IOException e) {
            getLog().error("Error generating csp-sdk git info", e);
            throw new MojoExecutionException("Failed to generate csp-sdk git info", e);
        }
    }

    private String normalizeBuildUrl(String buildUrl) {
        if (buildUrl == null || buildUrl.isEmpty()) {
            return "";
        }
        try {
            return new URL(buildUrl).getFile();
        } catch (MalformedURLException e) {
            getLog().warn("Invalid build_url : " + buildUrl);
            return buildUrl;
        }
    }

    private void writeFile(File file, String content) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        BufferedOutputStream bos = new BufferedOutputStream(fos);
        bos.write(content.getBytes());
        bos.flush();
        bos.close();
        fos.close();
    }

    private Properties readProperties(File file) {
        Properties prop = new Properties();
        FileInputStream fis = null;
        try {
            if (file.exists()) {
                fis = new FileInputStream(file);
                prop.load(fis);
            }
        } catch (FileNotFoundException fnfe) {
            getLog().debug("Properties file not found: " + file.getAbsolutePath());
        } catch (IOException e) {
            getLog().warn("Error reading properties file: " + file.getAbsolutePath(), e);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    getLog().debug("Error closing file: " + e.getMessage());
                }
            }
        }
        return prop;
    }

    private String formatAsString(String format) {
        return new SimpleDateFormat(format).format(Calendar.getInstance().getTime());
    }
}
