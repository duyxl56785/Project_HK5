package com.projecthk5.security;

import java.util.HashMap;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

/** Test-only provisioning; production receivers never synthesize a manifest. */
public final class PairManifestFixture {
    private PairManifestFixture() { }
    public static void install(ConfigurableEnvironment environment) {
        environment.getPropertySources().addLast(new PropertySource<String>("test-pair-manifest") {
            @Override public Object getProperty(String name) {
                if (!PairCredentialManifest.PROPERTY.equals(name)) return null;
                var secrets = new HashMap<String, String>();
                for (String pair : PairCredentialManifest.PAIRS) {
                    secrets.put(pair + "SECRET", "test-only-distinct-credential-0123456789-" + pair);
                    for (String slot : new String[]{"SECRET", "PREVIOUS_SECRET"}) {
                        String value = environment.getProperty(pair + slot);
                        if (value != null) secrets.put(pair + slot, value);
                    }
                }
                return PairCredentialManifest.provision(secrets::get);
            }
        });
    }
}
