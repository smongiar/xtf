package cz.xtf.core.bm;


import cz.xtf.core.openshift.OpenShift;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigSpecBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

import static org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM;

/**
 * Binary build that expect a file on path that shall be uploaded as {@code ROOT.(suffix)}. {@code suffix} is war, jar,...
 * It is extracted from filename.
 */
@Slf4j
public class BinaryBuildFromFile extends BinaryBuild {

	public BinaryBuildFromFile(String builderImage, Path path, Map<String, String> envProperties, String id) {
		super(builderImage, path, envProperties, id);
	}

	protected void configureBuildStrategy(BuildConfigSpecBuilder builder, String builderImage, List<EnvVar> env) {
		builder.withNewStrategy().withType("Source").withNewSourceStrategy().withEnv(env).withForcePull(true).withNewFrom().withKind("DockerImage").withName(builderImage).endFrom().endSourceStrategy().endStrategy();
	}

	protected String getImage(BuildConfig bc) {
		return bc.getSpec().getStrategy().getSourceStrategy().getFrom().getName();
	}

	protected List<EnvVar> getEnv(BuildConfig buildConfig) {
		return buildConfig.getSpec().getStrategy().getSourceStrategy().getEnv();
	}

	@Override
	public void build(OpenShift openShift) {
		openShift.imageStreams().create(is);
		openShift.buildConfigs().create(bc);
		String fileName = getPath().getFileName().toString();
		if (fileName.matches(".*(\\.\\w+)$")) {
			fileName = "ROOT" + fileName.replaceFirst(".*(\\.\\w+)$", "$1");
		}
		openShift.buildConfigs().withName(bc.getMetadata().getName())
				.instantiateBinary()
				.asFile(fileName)
				.fromFile(getPath().toFile());

	}

	protected String getContentHash() {
		if (!isCached() || contentHash == null) {
			try (InputStream i = Files.newInputStream(getPath())) {
				MessageDigest md = MessageDigest.getInstance("SHA-256");
				DigestOutputStream dos = new DigestOutputStream(NULL_OUTPUT_STREAM, md);
				IOUtils.copy(i, dos);

				// kubernetes annotation value must not be longer than 63 chars
				contentHash = Hex.encodeHexString(dos.getMessageDigest().digest()).substring(0, 63);
			} catch (NoSuchAlgorithmException | IOException e) {
				throw new RuntimeException(e);
			}
		}

		return contentHash;
	}
}