package com.tom.cpm.api;

/** STAND-IN for CPM's real interface (compile only, never in the jar). Signatures match CPM's. */
public interface ICPMPlugin {
    void initClient(IClientAPI api);

    void initCommon(ICommonAPI api);

    String getOwnerModId();
}
