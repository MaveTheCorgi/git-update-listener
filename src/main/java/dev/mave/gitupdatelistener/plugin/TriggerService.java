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
import java.net.*;
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
            server = new ServerSocket(settings.listenPort, 0, InetAddress.getByName("0.0.0.0"));
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

            String commitMessage = extractCommitMessage(payload);
            String authorName = extractAuthorName(payload);
            String repoName = extractRepoName(payload);

            ApplicationManager.getApplication().invokeLater(() -> {
                rerunTargetConfig();

                if (settings.discordWebhookUrl != null && !settings.discordWebhookUrl.isEmpty()) {
                    String title = "🔄 Run Configuration Triggered: " + settings.targetConfigName;
                    String description = String.format("Branch `%s` in repo `%s` was updated.\n%s",
                            settings.targetBranch, repoName, commitMessage);
                    sendDiscordNotification(title, description, authorName);
                }
            });
        }
    }

    /**
     * Extract the commit message from the webhook payload
     */
    private String extractCommitMessage(String payload) {
        try {
            int headCommitIndex = payload.indexOf("\"head_commit\":");
            if (headCommitIndex >= 0) {
                int messageStart = payload.indexOf("\"message\":\"", headCommitIndex) + 11;
                int messageEnd = payload.indexOf("\"", messageStart);
                return payload.substring(messageStart, messageEnd);
            }
        } catch (Exception e) {
            LOG.warn("Could not extract commit message", e);
        }
        return "No commit message available";
    }

    /**
     * Extract the author name from the webhook payload
     */
    private String extractAuthorName(String payload) {
        try {
            int headCommitIndex = payload.indexOf("\"head_commit\":");
            if (headCommitIndex >= 0) {
                int authorIndex = payload.indexOf("\"author\":", headCommitIndex);
                int nameStart = payload.indexOf("\"name\":\"", authorIndex) + 8;
                int nameEnd = payload.indexOf("\"", nameStart);
                return payload.substring(nameStart, nameEnd);
            }
        } catch (Exception e) {
            LOG.warn("Could not extract author name", e);
        }
        return "Unknown";
    }

    /**
     * Extract the repository name from the webhook payload
     */
    private String extractRepoName(String payload) {
        try {
            int repoIndex = payload.indexOf("\"repository\":");
            if (repoIndex >= 0) {
                int nameStart = payload.indexOf("\"name\":\"", repoIndex) + 8;
                int nameEnd = payload.indexOf("\"", nameStart);
                return payload.substring(nameStart, nameEnd);
            }
        } catch (Exception e) {
            LOG.warn("Could not extract repository name", e);
        }
        return "Unknown";
    }

    /**
     * Sends a Discord notification with the commit details to the specified webhook URL.
     */
    private void sendDiscordNotification(String title, String description, String commitAuthor) {
        TriggerSettings settings = TriggerSettings.getInstance();
        if (settings.discordWebhookUrl == null || settings.discordWebhookUrl.isEmpty()) {
            return;
        }

        try {
            URL url = new URL(settings.discordWebhookUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            String jsonPayload = String.format(
                    "{\"embeds\":[{" +
                            "\"title\":\"%s\"," +
                            "\"description\":\"%s\"," +
                            "\"color\":5814783," +
                            "\"footer\":{\"text\":\"Commit by: %s\"}," +
                            "\"timestamp\":\"%s\"" +
                            "}]}",
                    escapeJson(title),
                    escapeJson(description),
                    escapeJson(commitAuthor),
                    java.time.Instant.now().toString()
            );

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                LOG.warn("Discord notification failed with code: " + responseCode);
            }
        } catch (Exception e) {
            LOG.error("Error sending Discord notification", e);
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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