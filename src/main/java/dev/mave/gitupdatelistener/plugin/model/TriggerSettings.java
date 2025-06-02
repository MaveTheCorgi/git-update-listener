package dev.mave.gitupdatelistener.plugin.model;

import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
        name = "dev.mave.gitupdatelistener.plugin.TriggerSettings",
        storages = @Storage("GitUpdateListenerSettings.xml")
)
public class TriggerSettings implements PersistentStateComponent<TriggerSettings> {
    public String targetConfigName = "runProduction";
    public String targetBranch = "beta";
    public int listenPort = 12345;

    public static TriggerSettings getInstance() {
        return ServiceManager.getService(TriggerSettings.class);
    }

    @Nullable
    @Override
    public TriggerSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull TriggerSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}