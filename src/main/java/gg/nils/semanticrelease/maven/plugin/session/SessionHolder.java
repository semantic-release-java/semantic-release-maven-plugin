package gg.nils.semanticrelease.maven.plugin.session;

import java.util.Optional;

public interface SessionHolder {

    void setSession(Session session);

    Optional<Session> session();
}
