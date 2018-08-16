/*
 * Copyright 2005-2016 Sixth and Red River Software, Bas Leijdekkers
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.sixrr.metrics.ui.metricdisplay;

import com.intellij.analysis.AnalysisScope;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.sixrr.metrics.MetricCategory;
import com.sixrr.metrics.config.EnergyInsightsConfig;
import com.sixrr.metrics.export.CSVExporter;
import com.sixrr.metrics.export.XMLExporter;
import com.sixrr.metrics.metricModel.MetricsRun;
import com.sixrr.metrics.profile.MetricDisplaySpecification;
import com.sixrr.metrics.profile.MetricsProfile;
import com.sixrr.metrics.utils.MetricsReloadedBundle;
import javafx.application.Application;
import org.jetbrains.annotations.NotNull;
import sun.java2d.cmm.Profile;

import javax.management.Notification;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.File.*;

@SuppressWarnings({"ThisEscapedInObjectConstruction"})
public final class MetricsToolWindowImpl extends MetricsToolWindow {

    private final Project project;
    private final JPanel myContentPanel;
    private final MetricsDisplay metricsDisplay;
    private ToolWindow myToolWindow = null;
    private MetricsRun currentResults = null;
    private AnalysisScope currentScope = null;
    private MetricsProfile currentProfile = null;

    private MetricsToolWindowImpl(@NotNull Project project) {
        this.project = project;
        final DefaultActionGroup toolbarGroup = new DefaultActionGroup();
        toolbarGroup.add(new UpdateWithDiffAction(this, project));
        toolbarGroup.add(new ToggleAutoscrollAction());
        toolbarGroup.add(new ExportAction(this, project));
        toolbarGroup.add(new CreateSnapshotAction(this, project));
        toolbarGroup.add(new DiffSnapshotAction(this, project));
        toolbarGroup.add(new RemoveDiffAction(this));
        toolbarGroup.add(new EditThresholdsAction(this));
        toolbarGroup.add(new CloseMetricsViewAction(this));
        final ActionManager actionManager = ActionManager.getInstance();
        final ActionToolbar toolbar = actionManager.createActionToolbar(METRICS_TOOL_WINDOW_ID, toolbarGroup, false);
        myContentPanel = new JPanel(new BorderLayout());
        metricsDisplay = new MetricsDisplay(project);
        myContentPanel.add(toolbar.getComponent(), BorderLayout.WEST);
        myContentPanel.add(metricsDisplay.getTabbedPane(), BorderLayout.CENTER);
        register();
    }

    private void register() {
        final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        myToolWindow = toolWindowManager.registerToolWindow(METRICS_TOOL_WINDOW_ID, myContentPanel,
                ToolWindowAnchor.BOTTOM);
        myToolWindow.setTitle(MetricsReloadedBundle.message("metrics.reloaded.toolwindow.title"));
        myToolWindow.setIcon(IconLoader.getIcon(TOOL_WINDOW_ICON_PATH));
        myToolWindow.setAvailable(false, null);
    }

    @Override
    public void show(@NotNull MetricsRun results, @NotNull MetricsProfile profile, @NotNull AnalysisScope scope,
                     boolean showOnlyWarnings) {
        currentScope = scope;
        currentResults = showOnlyWarnings ? results.filterRowsWithoutWarnings(profile) : results;
        EnergyInsightsConfig.getInstance().setFilePath(project.getBasePath());
        EnergyInsightsConfig.getInstance().setProfileName(profile.getName());
        try {
            File f1=new File(project.getBasePath()+"\\Prev_"+profile.getName()+".csv");
            File f2=new File(project.getBasePath()+"\\New_"+profile.getName()+".csv");
            if(!f2.exists()){
                NotNullLazyValue<NotificationGroup> NOTIFICATION_GROUP = new NotNullLazyValue<NotificationGroup>() {
                    @NotNull
                    @Override
                    protected NotificationGroup compute() {
                        return new NotificationGroup("InstantRun",
                                NotificationDisplayType.STICKY_BALLOON, true);
                    }
                };
                ApplicationManager.getApplication()
                        .invokeLater(() -> Notifications.Bus.notify(NOTIFICATION_GROUP.getValue()
                                        .createNotification("This was your first run, there is no " +
                                                        "previous version for comparison to find energy insight"
                                                ,NotificationType.INFORMATION)));
            }
            f1.delete();
            f2.renameTo(f1);
            new CSVExporter(currentResults).export(project.getBasePath()+"\\New_"+profile.getName()+".csv");
            EnergyInsightsConfig.getInstance().generateValues();
        } catch (IOException e) {
        }
        currentProfile = profile;
        final MetricDisplaySpecification displaySpecification = currentProfile.getDisplaySpecification();
        metricsDisplay.setMetricsResults(displaySpecification, currentResults);
        myToolWindow.setAvailable(true, null);
        myToolWindow.setTitle(MetricsReloadedBundle.message("run.description.format",
                currentResults.getProfileName(),
                currentScope.getDisplayName(), currentResults.getTimestamp()) );
        myToolWindow.show(null);
    }

    @Override
    public void update(@NotNull MetricsRun results) {
        currentResults = results;
        final MetricDisplaySpecification displaySpecification =
                currentProfile.getDisplaySpecification();
        metricsDisplay.updateMetricsResults(results, displaySpecification);
    }

    @Override
    public void updateWithDiff(@NotNull MetricsRun results) {
        final MetricsRun prevResults = currentResults;
        currentResults = results;
        final MetricDisplaySpecification displaySpecification =
                currentProfile.getDisplaySpecification();
        metricsDisplay.updateMetricsResultsWithDiff(results, displaySpecification);
        myToolWindow.setTitle(MetricsReloadedBundle.message("run.comparison.message",
                currentResults.getProfileName(), currentScope.getDisplayName(),
                prevResults.getTimestamp(), currentResults.getTimestamp()));
    }

    @Override
    public void reloadAsDiff(@NotNull MetricsRun prevResults) {
        final MetricDisplaySpecification displaySpecification =
                currentProfile.getDisplaySpecification();
        metricsDisplay.overlayWithDiff(prevResults, displaySpecification);
        myToolWindow.setTitle(MetricsReloadedBundle.message("run.comparison.message",
                currentResults.getProfileName(), currentScope.getDisplayName(),
                prevResults.getTimestamp(), currentResults.getTimestamp()));
    }

    @Override
    public void removeDiffOverlay() {
        final MetricDisplaySpecification displaySpecification =
                currentProfile.getDisplaySpecification();
        metricsDisplay.removeDiffOverlay(displaySpecification);
        myToolWindow.setTitle(MetricsReloadedBundle.message("run.description.format",
                currentResults.getProfileName(), currentScope.getDisplayName(),
                currentResults.getTimestamp()));
    }

    @Override
    public boolean hasDiffOverlay() {
        return metricsDisplay != null && metricsDisplay.hasDiffOverlay();
    }

    @Override
    public void close() {
        myToolWindow.hide(null);
        myToolWindow.setAvailable(false, null);
    }

    @Override
    public MetricsRun getCurrentRun() {
        return currentResults;
    }

    @Override
    public AnalysisScope getCurrentScope() {
        return currentScope;
    }

    @Override
    public void dispose() {
        final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        toolWindowManager.unregisterToolWindow(METRICS_TOOL_WINDOW_ID);
    }

    @Override
    public MetricsProfile getCurrentProfile() {
        return currentProfile;
    }

    @Override
    public MetricCategory getSelectedCategory() {
        return metricsDisplay.getSelectedCategory();
    }
}
