package gg.nils.semanticrelease.maven.plugin.session.impl;

import gg.nils.semanticrelease.maven.plugin.session.Session;
import gg.nils.semanticrelease.maven.plugin.util.GAV;
import org.simpleframework.xml.*;
import org.simpleframework.xml.convert.AnnotationStrategy;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.Strategy;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Root(name = "semantic-version")
@Default(DefaultType.FIELD)
public class SessionImpl implements Session {

    @Element(name = "calculatedVersion")
    private String version;

    @Element(name = "multiModuleProjectDirectory")
    private File multiModuleDirectory;

    @ElementList(name = "projects", entry = "gav")
    private Set<GAV> projects = new LinkedHashSet<>();

    /* jaxb constructor */
    SessionImpl() {
    }

    /**
     * Standard constructor using mandatory fields. The class does not use final attributes dues to
     * its jaxb nature that requires an empty constructor.
     *
     * @param version              the final version
     * @param multiModuleDirectory the base maven directory
     */
    public SessionImpl(String version, File multiModuleDirectory) {
        this.version = version;
        this.multiModuleDirectory = multiModuleDirectory;
    }

    /**
     * Serializes as a String the given configuration object.
     *
     * @param session the object to serialize
     * @return a non null String representation of the given object serialized
     * @throws IOException if the serialized form cannot be written
     * @see SessionImpl#serializeFrom(String)
     */
    public static String serializeTo(Session session) throws Exception {
        Strategy strategy = new AnnotationStrategy();
        Serializer serializer = new Persister(strategy);
        StringWriter sw = new StringWriter();
        serializer.write(session, sw);
        return sw.toString();
    }

    /**
     * De-serializes the given string as a {@link SessionImpl}.
     *
     * @param content the string to de-serialize
     * @return a non null configuration object
     * @throws Exception if the given string could not be interpreted by simplexml
     */
    public static Session serializeFrom(String content) throws Exception {
        Strategy strategy = new AnnotationStrategy();
        Serializer serializer = new Persister(strategy);
        return serializer.read(SessionImpl.class, content);
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public File getMultiModuleDirectory() {
        return multiModuleDirectory;
    }

    @Override
    public void addProject(GAV project) {
        projects.add(project);
    }

    @Override
    public Set<GAV> getProjects() {
        return Collections.unmodifiableSet(projects);
    }
}
