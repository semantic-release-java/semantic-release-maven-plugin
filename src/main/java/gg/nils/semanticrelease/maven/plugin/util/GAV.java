package gg.nils.semanticrelease.maven.plugin.util;

import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.project.MavenProject;

import java.util.Objects;

/**
 * Wrapper for a maven project/dependency identified by a groupId/artifactId/version.
 */
public class GAV { // SUPPRESS CHECKSTYLE AbbreviationAsWordInName
    private String groupId;
    private String artifactId;
    private String version;

    public GAV() {
    }

    /**
     * Builds an immutable GAV object.
     *
     * @param groupId    the groupId of the maven object
     * @param artifactId the artifactId of the maven object
     * @param version    the version of the maven object
     */
    public GAV(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    /**
     * Builds a GAV object from the given MavenProject object.
     *
     * @param project the project to extract info from
     * @return a new GAV object
     */
    public static GAV from(MavenProject project) {
        return new GAV(project.getGroupId(), project.getArtifactId(), project.getVersion());
    }

    /**
     * Builds a GAV object from the given Model object.
     *
     * @param model the project model to extract info from
     * @return a new GAV object
     */
    public static GAV from(Model model) {
        String groupId =
                (model.getGroupId() != null)
                        ? model.getGroupId()
                        : (model.getParent() != null ? model.getParent().getGroupId() : null);
        String version =
                (model.getVersion() != null)
                        ? model.getVersion()
                        : (model.getParent() != null ? model.getParent().getVersion() : null);

        return new GAV(groupId, model.getArtifactId(), version);
    }

    /**
     * Builds a GAV object from the given Parent object.
     *
     * @param parent the parent to extract info from
     * @return a new GAV object
     */
    public static GAV from(Parent parent) {
        return new GAV(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
    }

    /**
     * Retrieves the groupId.
     *
     * @return the groupId
     */
    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    /**
     * Retrieves the artifactId.
     *
     * @return the artifactId
     */
    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    /**
     * Retrieves the version.
     *
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((artifactId == null) ? 0 : artifactId.hashCode());
        result = prime * result + ((groupId == null) ? 0 : groupId.hashCode());
        result = prime * result + ((version == null) ? 0 : version.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GAV gav = (GAV) o;
        return Objects.equals(groupId, gav.groupId) && Objects.equals(artifactId, gav.artifactId) && Objects.equals(version, gav.version);
    }

    @Override
    public String toString() {
        return String.format("%s::%s::%s", groupId, artifactId, version);
    }
}
