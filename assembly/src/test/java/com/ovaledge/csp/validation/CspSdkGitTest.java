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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.stream.Stream;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.Test;

/**
 * @author maheshnagineni
 *
 */
public class CspSdkGitTest {

	/**
	 * @throws Exception
	 */
	@Test
	public void test() throws Exception {
		try {

			System.out.println("Updating release-csp-sdk.properties");

			MavenXpp3Reader reader = new MavenXpp3Reader();
      Model model = reader.read(new FileReader("../pom.xml"));
      Properties mprops = model.getProperties();

			String fileContent = "";
			String branch = "";
			String pom_version = model.getVersion();
			String git_commit = "";
			String git_commit_code = "";
			String build_user = "";
			String build_url = "";
			String job_release_info = "";
			String cspSdkCoreVersion = mprops.getProperty("csp-sdk-core.version");
			String cspCoreVersion = mprops.getProperty("csp-sdk-core.version");

			File branchinfo = new File("./../buildinfo.properties");
			if( branchinfo.exists() ) {
				System.out.println("Found buildinfo.properties, getting git details");
				Properties props = readProperties(branchinfo);

				branch = props.getProperty("GIT_BRANCH").replaceAll("origin/", "");
				git_commit = props.getProperty("GIT_COMMIT");
				build_user = props.getProperty("BUILD_TRIGGED_BY");
				build_url = props.getProperty("BUILD_URL");
				try {
					URL url = new URL(build_url);
					build_url = url.getFile();
				} catch (MalformedURLException e) {
					System.err.println("Invalid build_url : "+build_url);
				}
				job_release_info = props.getProperty("RELEASE_NUMBER", "");
			} else {
				System.out.println("Reading git details from .git");
				Repository repository = new FileRepositoryBuilder().setGitDir(new File("./../.git")).build();

				RevWalk walk = new RevWalk(repository);
				ObjectId head = repository.resolve(Constants.HEAD);
				RevCommit commit = walk.parseCommit(head);

				branch = repository.getBranch();
				git_commit = commit.getName();

				walk.close();
			}
			if( git_commit != null && git_commit.length() >= 7 ) {
				git_commit_code = git_commit.substring(0, 7);
			}

			File releasefile = new File("./src/main/resources/release-csp-sdk.properties");
			File releasefileTarget = new File("./target/classes/release-csp-sdk.properties");

			Properties props = readProperties(releasefile);

			if(job_release_info.trim().length() == 0) {
				job_release_info = props.getProperty("csp-sdk.release.info", branch).replace("Release", "");
			}
			String releaseTypeArray[] = job_release_info.split("\\.");

			String releaseInfo = "Release".concat(job_release_info);
			String releaseNumber = job_release_info.replace(".", "");
			while (releaseNumber.length() < 4) {
				releaseNumber += "0";
			}
			String releaseType = "main";
			int lastNum = Integer.parseInt(releaseTypeArray[releaseTypeArray.length - 1]);
			if (releaseTypeArray.length == 3) {
				releaseType = (lastNum == 0) ? "main" : "service";
			} else if (releaseTypeArray.length > 3) {
				releaseType = (lastNum == 0) ? "service" : "hotfix";
			}

			String releaseVersion = pom_version != "" ? pom_version : props.getProperty("csp-sdk.release.version", branch);
			String releaseMain = props.getProperty("csp-sdk.release.main", branch);
			String releaseService = props.getProperty("csp-sdk.release.service", branch);
			String tagname = branch.startsWith("tag") ? branch : "";

			fileContent = fileContent + "csp-sdk.release.info="+releaseInfo+"\n";
			fileContent = fileContent + "csp-sdk.release.number="+releaseNumber+"\n";
			fileContent = fileContent + "csp-sdk.release.version="+releaseVersion+"\n";
			fileContent = fileContent + "csp-sdk.release.main="+releaseMain+"\n";
			fileContent = fileContent + "csp-sdk.release.service="+releaseService+"\n";
			fileContent = fileContent + "csp-sdk.release.type="+releaseType+"\n";
			fileContent = fileContent + "csp-sdk.git.branch="+branch+"\n";
			fileContent = fileContent + "csp-sdk.git.tag="+tagname+"\n";
			fileContent = fileContent + "csp-sdk.git.commit="+git_commit_code+"\n";
			fileContent = fileContent + "csp-sdk.git.commit.full="+git_commit+"\n";
			fileContent = fileContent + "csp-sdk.git.buildtime="+ formatAsString("yyyy-MM-dd HH:mm:ss")+"\n";
			fileContent = fileContent + "csp-sdk.csp-sdk-core.version="+cspSdkCoreVersion+"\n";
			fileContent = fileContent + "csp-sdk.csp-sdk-core.version="+cspCoreVersion+"\n";
			fileContent = fileContent + "csp-sdk.build.user="+ build_user +"\n";
			fileContent = fileContent + "csp-sdk.build.url="+ build_url;

			FileOutputStream fos = new FileOutputStream(releasefile);
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			bos.write(fileContent.getBytes());
			bos.flush();
			bos.close();
			fos.close();

			FileOutputStream fosTarget = new FileOutputStream(releasefileTarget);
			BufferedOutputStream bosTarget = new BufferedOutputStream(fosTarget);
			bosTarget.write(fileContent.getBytes());
			bosTarget.flush();
			bosTarget.close();
			fosTarget.close();

			System.out.println("-------------------------------- Maven Information: csp-sdk -------------------------");
			System.out.println("csp-sdk Id : "+model.getId());

			try (Stream<Path> paths = Files.walk(Paths.get("./target/classes/"))) {
				paths.filter(Files::isRegularFile)
								.filter( path -> path.getFileName().toString().startsWith("release-") &&
												path.getFileName().toString().endsWith(".properties") )
								.forEach(CspSdkGitTest::printFileContents);
				System.out.println("-----------------------------------------------------------------------");
			} catch (IOException e) {
				System.err.println("Error reading directory: " + e.getMessage());
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void printFileContents(Path path) {
		System.out.println("------------------------------" + path.getFileName().toString() + "---------------------");
		try {
			Files.lines(path).forEach(System.out::println);
		} catch (IOException e) {
			System.err.println("Error reading file: " + path.getFileName() + " - " + e.getMessage());
		}
	}

	/**
	 * @return Properties
	 */
	public static Properties readProperties(File file) {
		FileInputStream fis = null;
		Properties prop = new Properties();
		try {
			fis = new FileInputStream(file);
			prop = new Properties();
			prop.load(fis);
		} catch(FileNotFoundException fnfe) {
			fnfe.printStackTrace();
		} catch(IOException ioe) {
			ioe.printStackTrace();
		} finally {
			try {
				if (fis != null) {
					fis.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return prop;
	}

	/**
	 * @param format
	 * @return String
	 */
	public static String formatAsString(String format) {
		ZonedDateTime date = ZonedDateTime.now();
		if (date != null) {
			DateTimeFormatter dtf = DateTimeFormatter.ofPattern(format);
			return dtf.format(date);
		}
		return "";
	}

}
