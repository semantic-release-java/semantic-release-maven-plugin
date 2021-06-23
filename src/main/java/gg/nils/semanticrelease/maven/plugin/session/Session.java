package gg.nils.semanticrelease.maven.plugin.session;

import gg.nils.semanticrelease.maven.plugin.util.GAV;

import java.io.File;
import java.util.Set;

public interface Session {
    String getVersion();

    Set<GAV> getProjects();

    File getMultiModuleDirectory();

    void addProject(GAV gav);
}
