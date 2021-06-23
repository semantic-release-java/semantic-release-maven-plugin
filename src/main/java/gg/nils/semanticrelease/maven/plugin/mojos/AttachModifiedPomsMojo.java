package gg.nils.semanticrelease.maven.plugin.mojos;

import gg.nils.semanticrelease.maven.plugin.session.Session;
import gg.nils.semanticrelease.maven.plugin.session.impl.SessionImpl;
import gg.nils.semanticrelease.maven.plugin.util.Utils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.logging.console.ConsoleLogger;

import java.util.Objects;

@Mojo(name = AttachModifiedPomsMojo.GOAL_ATTACH_MODIFIED_POMS,
        instantiationStrategy = InstantiationStrategy.SINGLETON,
        threadSafe = true)
public class AttachModifiedPomsMojo extends AbstractMojo {

    public static final String GOAL_ATTACH_MODIFIED_POMS = "attach-modified-poms";

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession mavenSession;

    @Parameter(property = "semantic-release.resolve-project-version", defaultValue = "false")
    private Boolean resolveProjectVersion;

    @Override
    public void execute() throws MojoExecutionException {
        if (Objects.isNull(mavenSession.getUserProperties().get(Utils.SESSION_MAVEN_PROPERTIES_KEY))) {
            getLog().warn(GOAL_ATTACH_MODIFIED_POMS
                    + "shouldn't be executed alone. The Mojo "
                    + "is a part of the plugin and executed automatically.");
            return;
        }

        String content = mavenSession.getUserProperties().getProperty((Utils.SESSION_MAVEN_PROPERTIES_KEY));
        if ("-".equalsIgnoreCase(content)) {
            // We don't need to attach modified poms anymore.
            return;
        }

        try {
            Session session = SessionImpl.serializeFrom(content);

            Utils.attachModifiedPomFilesToTheProject(
                    mavenSession.getAllProjects(),
                    session.getProjects(),
                    session.getVersion(),
                    resolveProjectVersion,
                    new ConsoleLogger());

            mavenSession.getUserProperties().setProperty(Utils.SESSION_MAVEN_PROPERTIES_KEY, "-");
        } catch (Exception ex) {
            throw new MojoExecutionException("Unable to execute goal: " + AttachModifiedPomsMojo.GOAL_ATTACH_MODIFIED_POMS, ex);
        }
    }
}
