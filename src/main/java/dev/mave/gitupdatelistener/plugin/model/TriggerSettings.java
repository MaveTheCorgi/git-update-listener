package dev.mave.gitupdatelistener.plugin.model;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
        name = "dev.mave.gitupdatelistener.plugin.model.TriggerSettings",
        storages = @Storage("GitUpdateListenerSettings.xml")
)
public class TriggerSettings implements PersistentStateComponent<TriggerSettings> {
    public String targetConfigName = "runProduction";
    public String targetBranch = "beta";
    public int listenPort = 12345;
    public String discordWebhookUrl = "";

    public static TriggerSettings getInstance() {
        return ApplicationManager.getApplication().getService(TriggerSettings.class);
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