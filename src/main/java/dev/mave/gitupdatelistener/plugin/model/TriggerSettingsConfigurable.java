package dev.mave.gitupdatelistener.plugin.model;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class TriggerSettingsConfigurable implements Configurable {
    private JBTextField branchField;
    private JBTextField portField;
    private ComboBox<String> configComboBox;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Git Update Listener";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        TriggerSettings settings = TriggerSettings.getInstance();

        branchField = new JBTextField(settings.targetBranch);
        portField = new JBTextField(Integer.toString(settings.listenPort));
        configComboBox = new ComboBox<>();

        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        if (projects.length > 0) {
            List<String> configNames = new ArrayList<>();
            RunManager runManager = RunManager.getInstance(projects[0]);
            for (RunnerAndConfigurationSettings config : runManager.getAllSettings()) {
                configNames.add(config.getName());
            }
            configComboBox.setModel(new DefaultComboBoxModel<>(configNames.toArray(new String[0])));
            configComboBox.setSelectedItem(settings.targetConfigName);
        }

        return FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Run Configuration:"), configComboBox)
                .addLabeledComponent(new JBLabel("Target Branch:"), branchField)
                .addLabeledComponent(new JBLabel("Listen Port:"), portField)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    @Override
    public boolean isModified() {
        TriggerSettings settings = TriggerSettings.getInstance();
        boolean modified = false;

        if (configComboBox.getSelectedItem() != null) {
            modified |= !settings.targetConfigName.equals(configComboBox.getSelectedItem());
        }
        modified |= !settings.targetBranch.equals(branchField.getText());

        try {
            int port = Integer.parseInt(portField.getText());
            modified |= settings.listenPort != port;
        } catch (NumberFormatException e) {
            modified = true;
        }

        return modified;
    }

    @Override
    public void apply() {
        TriggerSettings settings = TriggerSettings.getInstance();
        if (configComboBox.getSelectedItem() != null) {
            settings.targetConfigName = (String) configComboBox.getSelectedItem();
        }
        settings.targetBranch = branchField.getText();
        try {
            settings.listenPort = Integer.parseInt(portField.getText());
        } catch (NumberFormatException ignored) { }
    }

    @Override
    public void reset() {
        TriggerSettings settings = TriggerSettings.getInstance();
        configComboBox.setSelectedItem(settings.targetConfigName);
        branchField.setText(settings.targetBranch);
        portField.setText(Integer.toString(settings.listenPort));
    }
}