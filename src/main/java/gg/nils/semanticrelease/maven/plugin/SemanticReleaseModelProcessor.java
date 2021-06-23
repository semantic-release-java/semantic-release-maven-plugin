package gg.nils.semanticrelease.maven.plugin;

import gg.nils.semanticrelease.maven.plugin.mojos.AttachModifiedPomsMojo;
import gg.nils.semanticrelease.maven.plugin.session.Session;
import gg.nils.semanticrelease.maven.plugin.session.SessionHolder;
import gg.nils.semanticrelease.maven.plugin.session.impl.SessionImpl;
import gg.nils.semanticrelease.maven.plugin.util.GAV;
import gg.nils.semanticrelease.maven.plugin.util.Utils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.building.Source;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.*;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.plugin.LegacySupport;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.*;
import java.util.function.Predicate;

;

/**
 * Replacement ModelProcessor using semantic-version while loading POMs in order to adapt versions.
 */
@Component(role = ModelProcessor.class)
public class SemanticReleaseModelProcessor extends DefaultModelProcessor {

    public static final String FLATTEN_MAVEN_PLUGIN = "flatten-maven-plugin";
    public static final String ORG_CODEHAUS_MOJO = "org.codehaus.mojo";

    @Requirement
    private Logger logger;

    @Requirement
    private LegacySupport legacySupport;

    @Requirement
    private SessionHolder sessionHolder;

    public SemanticReleaseModelProcessor() {
        super();
    }

    @Override
    public Model read(File input, Map<String, ?> options) throws IOException {
        return provisionModel(super.read(input, options), options);
    }

    @Override
    public Model read(Reader input, Map<String, ?> options) throws IOException {
        return provisionModel(super.read(input, options), options);
    }

    @Override
    public Model read(InputStream input, Map<String, ?> options) throws IOException {
        return provisionModel(super.read(input, options), options);
    }

    private Model provisionModel(Model model, Map<String, ?> options) throws IOException {
        MavenSession mavenSession = legacySupport.getSession();

        Optional<Session> optSession = sessionHolder.session();

        if (!optSession.isPresent()) {
            // don't do anything in case no semantic-version is there (execution could have been skipped)
            return model;
        }

        Object sourceOptionRaw = options.get(ModelProcessor.SOURCE);

        if (!(sourceOptionRaw instanceof Source)) {
            return model;
        }

        Source source = (Source) sourceOptionRaw;

        if (source == null) {
            return model;
        }

        File location = new File(source.getLocation());
        if (!location.isFile()) {
            // their JavaDoc says Source.getLocation "could be a local file path, a URI or just an empty
            // string."
            // if it doesn't resolve to a file then calling .getParentFile will throw an exception,
            // but if it doesn't resolve to a file then it isn't under getMultiModuleProjectDirectory,
            return model; // therefore the model shouldn't be modified.
        }

        Session session = optSession.get();
        File relativePath = location.getParentFile().getCanonicalFile();
        File multiModuleDirectory = session.getMultiModuleDirectory();
        String calculatedVersion = session.getVersion();

        if (StringUtils.containsIgnoreCase(
                relativePath.getCanonicalPath(), multiModuleDirectory.getCanonicalPath())) {
            logger.debug("handling version of project Model from " + location);

            session.addProject(GAV.from(model.clone()));

            if (Objects.nonNull(model.getVersion())) {
                // TODO evaluate how to set the version only when it was originally set in the pom file
                model.setVersion(calculatedVersion);
            }

            if (Objects.nonNull(model.getParent())) {
                // if the parent is part of the multi module project, let's update the parent version
                String modelParentRelativePath = model.getParent().getRelativePath();
                File relativePathParent =
                        new File(relativePath.getCanonicalPath() + File.separator + modelParentRelativePath)
                                .getParentFile()
                                .getCanonicalFile();
                if (StringUtils.isNotBlank(modelParentRelativePath)
                        && StringUtils.containsIgnoreCase(
                        relativePathParent.getCanonicalPath(), multiModuleDirectory.getCanonicalPath())) {
                    model.getParent().setVersion(calculatedVersion);
                }
            }

            // we should only register the plugin once, on the main project
            if (relativePath.getCanonicalPath().equals(multiModuleDirectory.getCanonicalPath())) {
                if (Utils.shouldUseFlattenPlugin(mavenSession)) {
                    if (isFlattenPluginDirectlyUsed(model)) {
                        logger.info(
                                "maven-flatten-plugin detected, semantic-release will not define it's own execution");
                    } else {
                        logger.info("adding maven-flatten-plugin execution with semantic-release defaults");
                        addFlattenPlugin(model);
                    }
                } else {
                    addAttachPomMojo(model);
                }

                // TODO: 23.06.2021 Implement tag update in scm
                // updateScmTag(session.getCalculator(), model);
            }

            try {
                mavenSession
                        .getUserProperties()
                        .put(
                                Utils.SESSION_MAVEN_PROPERTIES_KEY,
                                SessionImpl.serializeTo(session));
            } catch (Exception ex) {
                throw new IOException("cannot serialize Session", ex);
            }
        } else {
            logger.debug("skipping Model from " + location);
        }

        return model;
    }

    private void addFlattenPlugin(Model model) {
        ensureBuildWithPluginsExistInModel(model);

        Plugin flattenPlugin = new Plugin();
        flattenPlugin.setGroupId(ORG_CODEHAUS_MOJO);
        flattenPlugin.setArtifactId(FLATTEN_MAVEN_PLUGIN);
        flattenPlugin.setVersion(System.getProperty("semantic-release.flatten.version", "1.0.1"));

        PluginExecution flattenPluginExecution = new PluginExecution();
        flattenPluginExecution.setId("semantic-release-flatten-pom");
        flattenPluginExecution.addGoal("flatten");
        flattenPluginExecution.setPhase(
                System.getProperty("semantic-release.pom-replacement-phase", "validate"));

        flattenPlugin.getExecutions().add(flattenPluginExecution);

        Xpp3Dom executionConfiguration = buildFlattenPluginConfiguration();
        flattenPluginExecution.setConfiguration(executionConfiguration);
        model.getBuild().getPlugins().add(flattenPlugin);
    }

    private void ensureBuildWithPluginsExistInModel(Model model) {
        if (Objects.isNull(model.getBuild())) {
            model.setBuild(new Build());
        }

        if (Objects.isNull(model.getBuild().getPlugins())) {
            model.getBuild().setPlugins(new ArrayList<>());
        }
    }

    private Xpp3Dom buildFlattenPluginConfiguration() {
        Xpp3Dom configuration = new Xpp3Dom("configuration");

        Xpp3Dom flattenMode = new Xpp3Dom("flattenMode");
        flattenMode.setValue("defaults");

        Xpp3Dom updatePomFile = new Xpp3Dom("updatePomFile");
        updatePomFile.setValue("true");

        Xpp3Dom pomElements = new Xpp3Dom("pomElements");

        Xpp3Dom dependencyManagement = new Xpp3Dom("dependencyManagement");
        dependencyManagement.setValue("keep");
        pomElements.addChild(dependencyManagement);

        List<String> pomElementsName =
                Arrays.asList(
                        "build",
                        "ciManagement",
                        "contributors",
                        "dependencies",
                        "description",
                        "developers",
                        "distributionManagement",
                        "inceptionYear",
                        "issueManagement",
                        "mailingLists",
                        "modules",
                        "name",
                        "organization",
                        "parent",
                        "pluginManagement",
                        "pluginRepositories",
                        "prerequisites",
                        "profiles",
                        "properties",
                        "reporting",
                        "repositories",
                        "scm",
                        "url",
                        "version");

        pomElementsName.forEach(
                elementName -> {
                    Xpp3Dom node = new Xpp3Dom(elementName);
                    node.setValue("resolve");
                    pomElements.addChild(node);
                });

        configuration.addChild(flattenMode);
        configuration.addChild(updatePomFile);
        configuration.addChild(pomElements);

        return configuration;
    }

    private boolean isFlattenPluginDirectlyUsed(Model model) {
        Predicate<Plugin> isFlattenPlugin =
                p ->
                        ORG_CODEHAUS_MOJO.equals(p.getGroupId())
                                && FLATTEN_MAVEN_PLUGIN.equals(p.getArtifactId());

        List<Plugin> pluginList =
                Optional.ofNullable(model.getBuild())
                        .map(Build::getPlugins)
                        .orElse(Collections.emptyList());

        return pluginList.stream().filter(isFlattenPlugin).findAny().isPresent();
    }

    private void addAttachPomMojo(Model model) {
        ensureBuildWithPluginsExistInModel(model);

        Optional<Plugin> pluginOptional =
                model.getBuild().getPlugins().stream()
                        .filter(
                                x ->
                                        Utils.EXTENSION_GROUP_ID.equalsIgnoreCase(x.getGroupId())
                                                && Utils.EXTENSION_ARTIFACT_ID.equalsIgnoreCase(x.getArtifactId()))
                        .findFirst();

        StringBuilder pluginVersion = new StringBuilder();

        try (InputStream inputStream =
                     getClass()
                             .getResourceAsStream(
                                     "/META-INF/maven/"
                                             + Utils.EXTENSION_GROUP_ID
                                             + "/"
                                             + Utils.EXTENSION_ARTIFACT_ID
                                             + "/pom"
                                             + ".properties")) {
            Properties properties = new Properties();
            properties.load(inputStream);
            pluginVersion.append(properties.getProperty("version"));
        } catch (IOException ignored) {
            // TODO we should not ignore in case we have to reuse it
            logger.warn(ignored.getMessage(), ignored);
        }

        Plugin plugin =
                pluginOptional.orElseGet(
                        () -> {
                            Plugin plugin2 = new Plugin();
                            plugin2.setGroupId(Utils.EXTENSION_GROUP_ID);
                            plugin2.setArtifactId(Utils.EXTENSION_ARTIFACT_ID);
                            plugin2.setVersion(pluginVersion.toString());

                            model.getBuild().getPlugins().add(0, plugin2);
                            return plugin2;
                        });

        if (Objects.isNull(plugin.getExecutions())) {
            plugin.setExecutions(new ArrayList<>());
        }

        String pluginRunPhase = System.getProperty("semantic-release.pom-replacement-phase", "prepare-package");
        Optional<PluginExecution> pluginExecutionOptional =
                plugin.getExecutions().stream()
                        .filter(x -> pluginRunPhase.equalsIgnoreCase(x.getPhase()))
                        .findFirst();

        PluginExecution pluginExecution =
                pluginExecutionOptional.orElseGet(
                        () -> {
                            PluginExecution pluginExecution2 = new PluginExecution();
                            pluginExecution2.setPhase(pluginRunPhase);

                            plugin.getExecutions().add(pluginExecution2);
                            return pluginExecution2;
                        });

        if (Objects.isNull(pluginExecution.getGoals())) {
            pluginExecution.setGoals(new ArrayList<>());
        }

        if (!pluginExecution
                .getGoals()
                .contains(AttachModifiedPomsMojo.GOAL_ATTACH_MODIFIED_POMS)) {
            pluginExecution.getGoals().add(AttachModifiedPomsMojo.GOAL_ATTACH_MODIFIED_POMS);
        }

        if (Objects.isNull(plugin.getDependencies())) {
            plugin.setDependencies(new ArrayList<>());
        }

        Optional<Dependency> dependencyOptional =
                plugin.getDependencies().stream()
                        .filter(
                                x ->
                                        Utils.EXTENSION_GROUP_ID.equalsIgnoreCase(x.getGroupId())
                                                && Utils.EXTENSION_ARTIFACT_ID.equalsIgnoreCase(x.getArtifactId()))
                        .findFirst();

        dependencyOptional.orElseGet(
                () -> {
                    Dependency dependency = new Dependency();
                    dependency.setGroupId(Utils.EXTENSION_GROUP_ID);
                    dependency.setArtifactId(Utils.EXTENSION_ARTIFACT_ID);
                    dependency.setVersion(pluginVersion.toString());

                    plugin.getDependencies().add(dependency);
                    return dependency;
                });
    }
}
