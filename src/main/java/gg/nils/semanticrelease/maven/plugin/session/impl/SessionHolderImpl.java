package gg.nils.semanticrelease.maven.plugin.session.impl;

import gg.nils.semanticrelease.maven.plugin.session.Session;
import gg.nils.semanticrelease.maven.plugin.session.SessionHolder;
import org.codehaus.plexus.component.annotations.Component;

import java.util.Optional;

@Component(role = SessionHolder.class, instantiationStrategy = "singleton")
public class SessionHolderImpl implements SessionHolder {

    private Session session;

    @Override
    public void setSession(Session session) {
        this.session = session;
    }

    @Override
    public Optional<Session> session() {
        return Optional.ofNullable(session);
    }
}
