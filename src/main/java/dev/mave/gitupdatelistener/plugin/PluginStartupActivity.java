package dev.mave.gitupdatelistener.plugin;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class PluginStartupActivity implements AppLifecycleListener {
    @Override
    public void appStarted() {
        ApplicationManager.getApplication().getService(TriggerService.class);
    }
}