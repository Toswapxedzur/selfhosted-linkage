package net.blopybox.selfhosted_linkage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Selfhosted Linkage — shared entry point (client-only mod). */
public final class Linkage {
    public static final String MOD_ID = "selfhosted_linkage";
    public static final Logger LOGGER = LoggerFactory.getLogger("SelfhostedLinkage");

    private Linkage() {}

    public static void initClient() {
        // helper subprocesses must never outlive the game
        Runtime.getRuntime().addShutdownHook(new Thread(net.blopybox.selfhosted_linkage.core.LinkageSession::closeActive, "Linkage-shutdown"));
        LOGGER.info("Selfhosted Linkage client initialised");
    }
}
