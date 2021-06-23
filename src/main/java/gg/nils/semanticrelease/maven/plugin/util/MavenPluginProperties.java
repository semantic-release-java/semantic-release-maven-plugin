package gg.nils.semanticrelease.maven.plugin.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class MavenPluginProperties {
    static Properties properties = new Properties();

    static {
        try {
            InputStream is = MavenPluginProperties.class.getResourceAsStream("/META-INF/semantic-release-maven-plugin-project.properties");

            if (is != null) {
                properties.load(is);
            }
        } catch (IOException e) {
            // ignore
        }
    }

    public static String getVersion() {
        return properties.getProperty("version", "Unknown");
    }

    public static String getSHA1() {
        return properties.getProperty("sha1", "not git sha1");
    }
}
