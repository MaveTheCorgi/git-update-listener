package dev.mave.gitupdatelistener.plugin;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import dev.mave.gitupdatelistener.plugin.model.TriggerSettings;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public final class TriggerService {
    private static final Logger LOG = Logger.getInstance(TriggerService.class);

    /** Background thread that accepts webhook connections */
    private final Thread listenerThread;
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private ServerSocket server;

    /**
     * The constructor is called automatically by IntelliJ since this class is annotated with @Service.
     */
    public TriggerService() {
        listenerThread = new Thread(this::runServer, "GitHub-Update-Listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
        TriggerSettings settings = TriggerSettings.getInstance();
        LOG.info("GitHub Update Listener service started on port " + settings.listenPort);
    }

    /**
     * Server loop: accept GitHub webhook connections and process them
     */
    private void runServer() {
        TriggerSettings settings = TriggerSettings.getInstance();
        try {
            server = new ServerSocket(settings.listenPort);
            while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
                try (Socket client = server.accept()) {
                    processWebhookRequest(client);
                } catch (Exception e) {
                    if (!isRunning.get()) break;
                    LOG.error("Error handling incoming webhook request", e);
                }
            }
        } catch (Exception e) {
            if (isRunning.get()) {
                LOG.error("Failed to open ServerSocket on port " + settings.listenPort, e);
            }
        }
    }

    /**
     * Process an incoming webhook request
     */
    private void processWebhookRequest(Socket client) throws Exception {
        TriggerSettings settings = TriggerSettings.getInstance();
        BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));

        String line;
        String eventType = null;
        int contentLength = 0;

        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            if (line.startsWith("X-GitHub-Event:")) {
                eventType = line.substring("X-GitHub-Event:".length()).trim();
            } else if (line.startsWith("Content-Length:")) {
                contentLength = Integer.parseInt(line.substring("Content-Length:".length()).trim());
            }
        }

        StringBuilder payloadBuilder = new StringBuilder();
        if (contentLength > 0) {
            char[] buffer = new char[contentLength];
            int bytesRead = reader.read(buffer, 0, contentLength);
            payloadBuilder.append(buffer, 0, bytesRead);
        }

        String payload = payloadBuilder.toString();
        LOG.info("Received GitHub webhook event: " + eventType);

        OutputStream output = client.getOutputStream();
        String response = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK";
        output.write(response.getBytes(StandardCharsets.UTF_8));
        output.flush();

        if ("push".equals(eventType) && payload.contains("\"ref\":\"refs/heads/" + settings.targetBranch + "\"")) {
            LOG.info("Detected update to target branch '" + settings.targetBranch + "'. Scheduling configuration rerun...");
            ApplicationManager.getApplication().invokeLater(this::rerunTargetConfig);
        }
    }

    /**
     * Locate any open project, fetch its RunManager, find the configuration named TARGET_CONFIG_NAME,
     * and rerun it using the DefaultRunExecutor.
     */
    private void rerunTargetConfig() {
        TriggerSettings settings = TriggerSettings.getInstance();
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        if (openProjects.length == 0) {
            LOG.error("No open projects found. Aborting rerun.");
            return;
        }

        Project project = openProjects[0];
        if (project.isDisposed()) {
            LOG.error("Selected project is disposed. Aborting rerun.");
            return;
        }

        RunManager runManager = RunManager.getInstance(project);
        if (runManager == null) {
            LOG.error("RunManager is null for project: " + project.getName());
            return;
        }

        List<RunnerAndConfigurationSettings> allSettings = runManager.getAllSettings();
        RunnerAndConfigurationSettings targetSettings = null;
        for (RunnerAndConfigurationSettings configSettings : allSettings) {
            if (settings.targetConfigName.equals(configSettings.getName())) {
                targetSettings = configSettings;
                break;
            }
        }

        if (targetSettings == null) {
            LOG.error("Run Configuration '" + settings.targetConfigName + "' not found in project: " + project.getName());
            return;
        }

        ExecutionUtil.runConfiguration(targetSettings, DefaultRunExecutor.getRunExecutorInstance());
        LOG.info("Rerun triggered for configuration: " + settings.targetConfigName);
    }

    /**
     * Clean up resources when the plugin is unloaded
     */
    public void dispose() {
        isRunning.set(false);
        if (server != null) {
            try {
                server.close();
            } catch (Exception e) {
                LOG.error("Error closing server socket", e);
            }
        }
        if (listenerThread != null && listenerThread.isAlive()) {
            listenerThread.interrupt();
        }
    }
}