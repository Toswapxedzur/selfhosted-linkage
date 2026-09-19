package net.blopybox.selfhosted_linkage.fabric;

import net.blopybox.selfhosted_linkage.Linkage;
import net.fabricmc.api.ClientModInitializer;

public final class LinkageFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Linkage.initClient();
    }
}
