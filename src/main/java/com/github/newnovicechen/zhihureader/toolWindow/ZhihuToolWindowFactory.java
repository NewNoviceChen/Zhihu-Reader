package com.github.newnovicechen.zhihureader.toolWindow;

import com.github.newnovicechen.zhihureader.model.Question;
import com.github.newnovicechen.zhihureader.services.ZhihuService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ZhihuToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ZhihuPanel panel = new ZhihuPanel(project);

        Content content = ContentFactory.getInstance().createContent(panel.getRoot(), "", false);
        toolWindow.getContentManager().addContent(content);

        panel.loadRecommend(); // 初始化加载
    }

    /** 可选：支持多个项目实例 */
    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }

    static class ZhihuPanel {
        private final Project project;

        private final JPanel root = new JPanel(new BorderLayout());
        private final DefaultListModel<Question> listModel = new DefaultListModel<>();
        private final JList<Question> menuList = new JList<>(listModel);
        private final JTextArea mainText = new JTextArea();

        ZhihuPanel(Project project) {
            this.project = project;

            // 左侧菜单
            menuList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            menuList.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof Question q) {
                        setText(q.getTitle()); // 确保 Question 有 getTitle()
                    }
                    return this;
                }
            });

            // 右侧主文本
            mainText.setEditable(false);
            mainText.setLineWrap(true);
            mainText.setWrapStyleWord(true);

            JSplitPane splitPane = new JSplitPane(
                    JSplitPane.HORIZONTAL_SPLIT,
                    new JScrollPane(menuList),
                    new JScrollPane(mainText)
            );
            splitPane.setDividerLocation(260);

            // 顶部按钮（可选）
            JButton refreshBtn = new JButton("刷新推荐");
            refreshBtn.addActionListener(e -> loadRecommend());

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            top.add(refreshBtn);

            root.add(top, BorderLayout.NORTH);
            root.add(splitPane, BorderLayout.CENTER);

            // 点击左侧项 -> 更新右侧文本（你可以在这里请求详情）
            // 点击左侧项 -> 拉取回答并更新右侧文本
            menuList.addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) return;
                Question q = menuList.getSelectedValue();
                if (q == null) return;

                mainText.setText("加载回答中...\n\n" + q.getTitle());

                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    try {
                        ZhihuService service = project.getService(ZhihuService.class);
                        List<com.github.newnovicechen.zhihureader.model.Answer> answers =
                                service.zhihuAnswer(q.getId());

                        SwingUtilities.invokeLater(() -> {
                            StringBuilder sb = new StringBuilder();
                            sb.append("问题：").append(q.getTitle())
                                    .append("\nID：").append(q.getId())
                                    .append("\n\n");

                            int i = 1;
                            for (var a : answers) {
                                sb.append("=== 回答 ").append(i++).append(" ===\n")
                                        .append("作者：").append(a.getAuthorName()).append("\n\n")
                                        .append(a.getAnswerContent()).append("\n\n");
                            }
                            mainText.setText(sb.toString());
                            mainText.setCaretPosition(0);
                        });
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() ->
                                mainText.setText("加载失败：\n" + ex.getMessage()));
                    }
                });
            });
        }

        JComponent getRoot() {
            return root;
        }

        void loadRecommend() {
            listModel.clear();
            mainText.setText("加载中...");

            // 不要在 EDT（UI线程）里直接发网络请求
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    ZhihuService service = project.getService(ZhihuService.class);
                    List<Question> questions = service.zhihuRecommend();

                    SwingUtilities.invokeLater(() -> {
                        listModel.clear();
                        for (Question q : questions) listModel.addElement(q);
                        mainText.setText("加载完成，左侧选择一个条目。");
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> mainText.setText("加载失败：\n" + ex.getMessage()));
                }
            });
        }
    }
}