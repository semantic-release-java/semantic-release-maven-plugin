package gg.nils.semanticrelease.maven.plugin;

import gg.nils.semanticrelease.config.DefaultSemanticReleaseConfig;
import gg.nils.semanticrelease.config.SemanticReleaseConfig;
import gg.nils.semanticrelease.maven.plugin.session.SessionHolder;
import gg.nils.semanticrelease.maven.plugin.session.impl.SessionImpl;
import gg.nils.semanticrelease.maven.plugin.util.MavenPluginProperties;
import gg.nils.semanticrelease.maven.plugin.util.Utils;
import gg.nils.semanticrelease.versioncontrol.VersionControlProvider;
import gg.nils.semanticrelease.versioncontrol.git.GitVersionControlProvider;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

@Component(role = AbstractMavenLifecycleParticipant.class, hint = "semantic-release")
public class SemanticReleaseExtension extends AbstractMavenLifecycleParticipant {

    @Requirement
    private Logger logger;

    @Requirement
    private PlexusContainer container;

    @Requirement
    private ModelProcessor modelProcessor;

    @Requirement
    private SessionHolder sessionHolder;

    @Override
    public void afterSessionStart(MavenSession mavenSession) throws MavenExecutionException {
        if (Utils.shouldSkip(mavenSession)) {
            logger.info("  semantic-release execution has been skipped by request of the user");
            sessionHolder.setSession(null);
            return;
        }

        final File rootDirectory = mavenSession.getRequest().getMultiModuleProjectDirectory();

        logger.debug("using " + Utils.EXTENSION_PREFIX + " on directory: " + rootDirectory);

        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder().findGitDir(rootDirectory);

        if (repositoryBuilder == null || repositoryBuilder.getGitDir() == null) {
            logger.warn("Not a git repository");
            return;
        }

        try (Repository repository = repositoryBuilder.build()) {
            logger.info(
                    String.format(
                            "Using semantic-release-maven-plugin [%s] (sha1: %s)",
                            MavenPluginProperties.getVersion(), MavenPluginProperties.getSHA1()));
            long start = System.currentTimeMillis();

            Git git = new Git(repository);

            // TODO: 21.06.2021 Make configurable via .mvn/semantic-release.config.json
            SemanticReleaseConfig config = new DefaultSemanticReleaseConfig();

            VersionControlProvider versionControlProvider = new GitVersionControlProvider(config, git);

            String computedVersion = versionControlProvider.getFullVersionWithoutDirty();

            long duration = System.currentTimeMillis() - start;

            logger.info(String.format("    version '%s' computed in %d ms", computedVersion, duration));
            logger.info("");

            Utils.fillPropertiesFromMetadatas(mavenSession.getUserProperties(), versionControlProvider, logger);

            SessionImpl session = new SessionImpl(computedVersion, rootDirectory);
            sessionHolder.setSession(session);
        } catch (IOException e) {
            //throw new MavenExecutionException("Could not generate version...", e);
            logger.warn("Could not generate version for project: " + rootDirectory, e);
        }
    }

    @Override
    public void afterSessionEnd(MavenSession session) throws MavenExecutionException {
        sessionHolder.setSession(null);
    }

    @Override
    public void afterProjectsRead(MavenSession mavenSession) throws MavenExecutionException {
        if (!Utils.shouldSkip(mavenSession)) {
            File projectBaseDir = mavenSession.getCurrentProject().getBasedir();
            if (projectBaseDir != null) {
                final Consumer<? super CharSequence> c = cs -> logger.warn(cs.toString());

                if (SemanticReleaseModelProcessor.class.isAssignableFrom(modelProcessor.getClass())) {
                    if (!mavenSession.getUserProperties().containsKey(Utils.SESSION_MAVEN_PROPERTIES_KEY)) {
                        Utils.failAsOldMechanism(c);
                    }
                } else {
                    Utils.failAsOldMechanism(c);
                }

                sessionHolder
                        .session()
                        .ifPresent(
                                session -> {
                                    logger.info("semantic-release-maven-plugin is about to change project(s) version(s)");

                                    session
                                            .getProjects()
                                            .forEach(
                                                    gav ->
                                                            logger.info(
                                                                    "    "
                                                                            + gav.toString()
                                                                            + " -> "
                                                                            + session.getVersion()));
                                });
            }
        }
    }
}
