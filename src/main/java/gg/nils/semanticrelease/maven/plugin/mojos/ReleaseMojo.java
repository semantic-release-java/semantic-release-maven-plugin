package gg.nils.semanticrelease.maven.plugin.mojos;

import gg.nils.semanticrelease.config.DefaultSemanticReleaseConfig;
import gg.nils.semanticrelease.config.SemanticReleaseConfig;
import gg.nils.semanticrelease.versioncontrol.VersionControlProvider;
import gg.nils.semanticrelease.versioncontrol.git.GitVersionControlProvider;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.IOException;

@Mojo(name = ReleaseMojo.GOAL_RELEASE,
        instantiationStrategy = InstantiationStrategy.SINGLETON,
        threadSafe = true)
public class ReleaseMojo extends AbstractMojo {

    public static final String GOAL_RELEASE = "release";

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession mavenSession;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoFailureException {
        Log logger = this.getLog();

        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder()
                .findGitDir(this.project.getBasedir());

        if (repositoryBuilder.getGitDir() == null) {
            logger.error(".git dir not found!");
            return;
        }

        try (Repository repository = repositoryBuilder.build()) {
            Git git = new Git(repository);

            SemanticReleaseConfig config = new DefaultSemanticReleaseConfig();

            VersionControlProvider provider = new GitVersionControlProvider(config, git);

            if (!provider.getCurrentBranch().getName().equals("master")
                    && !provider.getCurrentBranch().getName().equals("main")) {
                throw new MojoFailureException("Only master and main branches can be released!");
            }

            if (provider.hasUncommittedChanges()) {
                throw new MojoFailureException("This branch has uncommitted changes, cannot release!");
            }

            if (provider.getLatestVersion().equals(provider.getNextVersion()) && provider.getLatestTag() != null) {
                throw new MojoFailureException("There were no changes that would require a release!");
            }

            Ref ref = git.tag()
                    .setName(provider.getNextVersion().toString())
                    .setAnnotated(false)
                    .call();

            logger.info("New version: " + ref.getName());

            git.push().setPushTags().call();
        } catch (IOException | GitAPIException e) {
            e.printStackTrace();
        }
    }
}
