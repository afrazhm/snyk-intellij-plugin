package io.snyk.plugin.ui.toolwindow;

import com.intellij.openapi.project.Project;
import io.snyk.plugin.events.SnykCliDownloadListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SnykAuthPanel {
  private JPanel rootPanel;
  private JButton connectIntelliJToSnykButton;

  public SnykAuthPanel(@NotNull Project project) {
    connectIntelliJToSnykButton.addActionListener(e ->
      // todo
      project.getMessageBus().syncPublisher(SnykCliDownloadListener.Companion.getCLI_DOWNLOAD_TOPIC())
        .checkCliExistsFinished()
    );
  }

  public JPanel getRoot() {
    return rootPanel;
  }

}
