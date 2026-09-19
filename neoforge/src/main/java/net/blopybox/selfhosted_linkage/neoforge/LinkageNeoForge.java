package net.blopybox.selfhosted_linkage.neoforge;

import net.blopybox.selfhosted_linkage.Linkage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value = Linkage.MOD_ID, dist = Dist.CLIENT)
public final class LinkageNeoForge {
    public LinkageNeoForge() {
        Linkage.initClient();
    }
}
