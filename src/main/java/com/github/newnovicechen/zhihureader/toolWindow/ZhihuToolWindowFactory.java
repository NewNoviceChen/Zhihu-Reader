package com.github.newnovicechen.zhihureader.toolWindow;

import com.github.newnovicechen.zhihureader.model.Question;
import com.github.newnovicechen.zhihureader.services.ZhihuService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages; // 引入 Messages 类
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.util.List;

// 定义一个存储阅读模式状态的服务
// 这样每次IDE重启，工具窗口能记住上次的阅读模式
@State(
        name = "ZhihuReaderSettings",
        storages = @Storage("ZhihuReaderSettings.xml")
)
final class ZhihuReaderSettings implements PersistentStateComponent<ZhihuReaderSettings> {
    public boolean isHtmlMode = true; // 默认HTML模式
    public String zhihuCookie = ""; // 添加知乎Cookie字段

    public static ZhihuReaderSettings getInstance() {
        return ApplicationManager.getApplication().getService(ZhihuReaderSettings.class);
    }

    @Override
    public @Nullable ZhihuReaderSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ZhihuReaderSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}


public class ZhihuToolWindowFactory implements ToolWindowFactory {
    // 辅助方法：将 HTML 转换为纯文本，保留了段落和列表的换行
    public static String htmlToPlainText(String html) {
        if (html == null) return "";
        Document doc = Jsoup.parse(html);

        // 把段落/换行类标签转换成换行，避免全挤在一行
        for (Element br : doc.select("br")) br.after("\n");
        for (Element p : doc.select("p")) p.after("\n");
        for (Element li : doc.select("li")) li.prepend("• ").after("\n");

        String text = doc.text();

        // doc.text() 会把 \n 吃掉，这里用 wholeText 取文本并保留我们插入的换行
        text = doc.body().wholeText();

        return text.replace("\u00A0", " ").trim(); // &nbsp;
    }

    /**
     * 预处理 HTML 内容，强制限制图片的宽度以适应 Swing 的 JEditorPane 渲染。
     * 对于图片，直接设置 width 属性，并保持 height 为 auto。
     *
     * @param htmlContent 原始 HTML 字符串
     * @param maxWidth 图片的最大宽度限制（像素）
     * @return 处理后的 HTML 字符串
     */
    public static String preprocessHtmlForSwing(String htmlContent, int maxWidth) {
        if (htmlContent == null || htmlContent.isEmpty()) {
            return "";
        }
        Document doc = Jsoup.parse(htmlContent);

        // 遍历所有 img 标签
        for (Element img : doc.select("img")) {
            // 移除可能存在的 style 属性中的 width/height/max-width
            String style = img.attr("style");
            if (style != null && !style.isEmpty()) {
                style = style.replaceAll("width:[^;]+;?", "");
                style = style.replaceAll("height:[^;]+;?", "");
                style = style.replaceAll("max-width:[^;]+;?", "");
                img.attr("style", style);
            }

            // 强制设置 width 属性，让 JEditorPane 直接使用这个固定宽度
            img.attr("width", String.valueOf(maxWidth));
            img.attr("height", "auto"); // 保持高度自动调整，防止变形
        }
        return doc.body().html(); // 返回修改后的 body 内部 HTML
    }


    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ZhihuPanel panel = new ZhihuPanel(project);

        Content content = ContentFactory.getInstance().createContent(panel.getRoot(), "", false);
        toolWindow.getContentManager().addContent(content);

        // 插件启动时，从配置加载Cookie并设置到Service
        ZhihuService zhihuService = project.getService(ZhihuService.class);
        String savedCookie = ZhihuReaderSettings.getInstance().zhihuCookie;
        zhihuService.setUserCookie(savedCookie);

        // 插件初始化时，根据Cookie状态决定是否加载推荐
        if (zhihuService.isCookieSetAndValid()) {
            panel.loadRecommend();
        } else {
            panel.showCookieRequiredMessage();
        }
    }

    /** 可选：支持多个项目实例 */
    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }

    static class ZhihuPanel {
        private final Project project;
        private final ZhihuService zhihuService; // 持有ZhihuService的引用

        private final JPanel root = new JPanel(new BorderLayout());
        private final DefaultListModel<Question> listModel = new DefaultListModel<>();
        private final JList<Question> menuList = new JList<>(listModel);

        // 两种显示内容面板
        private final JEditorPane htmlTextPane = new JEditorPane();
        private final JTextArea plainTextPane = new JTextArea();
        private final CardLayout cardLayout = new CardLayout();
        private final JPanel contentPanel = new JPanel(cardLayout); // 用于切换 htmlTextPane 和 plainTextPane

        private static final String HTML_MODE_CARD = "HTML_MODE";
        private static final String PLAIN_TEXT_MODE_CARD = "PLAIN_TEXT_MODE";

        private boolean isHtmlMode; // 当前是否为HTML模式

        // 分页相关字段
        private static final int PAGE_SIZE = 10;
        private int offsetCurrent = 0;
        private final JLabel pageLabel = new JLabel("页: 1");

        private JButton refreshBtn; // 将refreshBtn改为成员变量
        private JButton prevBtn;
        private JButton nextBtn;
        private JButton toggleModeBtn; // 切换模式按钮
        private JButton setCookieBtn; // 设置Cookie按钮

        // 定义图片的最大宽度，这个值应该根据你的工具窗口右侧面板的预期宽度来设定
        // 例如，如果右侧面板在正常情况下是800px宽，减去padding和边框，图片最大宽度可以设置为750-780px
        private static final int IMAGE_MAX_WIDTH = 600; // 预设一个合理的图片最大宽度

        ZhihuPanel(Project project) {
            this.project = project;
            this.zhihuService = project.getService(ZhihuService.class); // 获取ZhihuService实例

            // 从设置中加载上次的模式
            ZhihuReaderSettings settings = ZhihuReaderSettings.getInstance();
            this.isHtmlMode = settings.isHtmlMode;
            // 初始化时将保存的Cookie设置到Service
            this.zhihuService.setUserCookie(settings.zhihuCookie);


            // 左侧菜单
            menuList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            menuList.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof Question q) {
                        setText(q.getTitle());
                    }
                    return this;
                }
            });

            // HTML 模式下的 JEditorPane 设置
            htmlTextPane.setEditable(false);
            htmlTextPane.setContentType("text/html");
            htmlTextPane.setOpaque(false);

            // HTMLEditorKit 和 StyleSheet 配置
            HTMLEditorKit kit = new HTMLEditorKit();
            StyleSheet styleSheet = kit.getStyleSheet();
            // 这里的 max-width: 100% 依然保留，作为辅助，但主要由预处理的 width 属性控制
            styleSheet.addRule("img { max-width: 100%; height: auto; display: block; margin: 0 auto; }");
            htmlTextPane.setEditorKit(kit);

            // 纯文本模式下的 JTextArea 设置
            plainTextPane.setEditable(false);
            plainTextPane.setLineWrap(true);
            plainTextPane.setWrapStyleWord(true);
            plainTextPane.setMargin(new Insets(10, 10, 10, 10)); // 设置内边距
            plainTextPane.setFont(new Font("Monospaced", Font.PLAIN, 13)); // 设置字体

            // 将两种模式的组件添加到 CardLayout 面板
            contentPanel.add(new JScrollPane(htmlTextPane), HTML_MODE_CARD);
            contentPanel.add(new JScrollPane(plainTextPane), PLAIN_TEXT_MODE_CARD);

            JSplitPane splitPane = new JSplitPane(
                    JSplitPane.HORIZONTAL_SPLIT,
                    new JScrollPane(menuList),
                    contentPanel
            );
            splitPane.setDividerLocation(260);
            splitPane.setResizeWeight(1.0); // 左侧面板优先获得额外空间，右侧面板宽度相对稳定

            // 顶部按钮
            refreshBtn = new JButton("刷新推荐");
            refreshBtn.addActionListener(e -> loadRecommend());

            prevBtn = new JButton("上一页");
            nextBtn = new JButton("下一页");

            prevBtn.addActionListener(e -> {
                if (offsetCurrent >= PAGE_SIZE) {
                    offsetCurrent -= PAGE_SIZE;
                    updatePageLabel();
                    loadSelectedQuestionAnswers();
                }
            });

            nextBtn.addActionListener(e -> {
                offsetCurrent += PAGE_SIZE;
                updatePageLabel();
                loadSelectedQuestionAnswers();
            });

            // 切换模式按钮
            toggleModeBtn = new JButton();
            toggleModeBtn.addActionListener(e -> toggleDisplayMode());
            updateToggleModeButtonText(); // 初始化按钮文本

            // 设置Cookie按钮
            setCookieBtn = new JButton("设置Cookie");
            setCookieBtn.addActionListener(e -> showCookieInputDialog());


            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            top.add(setCookieBtn); // 将设置Cookie按钮放在最前面
            top.add(refreshBtn);
            top.add(prevBtn);
            top.add(nextBtn);
            top.add(pageLabel);
            top.add(toggleModeBtn); // 添加切换按钮

            root.add(top, BorderLayout.NORTH);
            root.add(splitPane, BorderLayout.CENTER);

            // 初始化显示模式
            cardLayout.show(contentPanel, isHtmlMode ? HTML_MODE_CARD : PLAIN_TEXT_MODE_CARD);

            // 点击左侧项 -> 更新右侧文本
            menuList.addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) return;
                Question q = menuList.getSelectedValue();
                if (q == null) return;

                if (!zhihuService.isCookieSetAndValid()) {
                    showCookieRequiredMessage();
                    return;
                }

                offsetCurrent = 0;
                updatePageLabel();
                prevBtn.setEnabled(false); // 选中新问题时，上一页按钮通常禁用

                // 统一设置加载中提示
                String loadingText = "加载回答中...\n\n" + q.getTitle();
                htmlTextPane.setText("<html><body><h2>" + loadingText.replace("\n", "<br/>") + "</h2></body></html>");
                plainTextPane.setText(loadingText);

                // 根据当前模式显示对应的面板
                cardLayout.show(contentPanel, isHtmlMode ? HTML_MODE_CARD : PLAIN_TEXT_MODE_CARD);

                loadSelectedQuestionAnswers(); // 异步加载答案并更新
            });

            // 初始化按钮状态
            updateFunctionalityButtonsState();
        }

        JComponent getRoot() {
            return root;
        }

        private void updatePageLabel() {
            int page = (offsetCurrent / PAGE_SIZE) + 1;
            pageLabel.setText("页: " + page);
        }

        private void updateToggleModeButtonText() {
            toggleModeBtn.setText(isHtmlMode ? "切换到纯文本模式" : "切换到HTML模式");
        }

        private void toggleDisplayMode() {
            isHtmlMode = !isHtmlMode;
            updateToggleModeButtonText(); // 更新按钮文本

            // 保存新的模式状态
            ZhihuReaderSettings settings = ZhihuReaderSettings.getInstance();
            settings.isHtmlMode = isHtmlMode;

            // 切换卡片显示
            cardLayout.show(contentPanel, isHtmlMode ? HTML_MODE_CARD : PLAIN_TEXT_MODE_CARD);

            // 重新加载当前选中的问题答案，以在新模式下显示
            Question selectedQuestion = menuList.getSelectedValue();
            if (selectedQuestion != null) {
                loadSelectedQuestionAnswers();
            } else {
                // 如果没有选中问题，清空内容或显示默认提示
                String welcomeText = "<html><body><p>加载完成，左侧选择一个条目。</p></body></html>";
                htmlTextPane.setText(welcomeText);
                plainTextPane.setText(htmlToPlainText(welcomeText));
            }
        }

        // 显示需要Cookie的提示信息
        private void showCookieRequiredMessage() {
            String msg = "请先点击“设置Cookie”按钮，输入Cookie才能使用插件。";
            htmlTextPane.setText("<html><body><p style='color:red;'>" + msg + "</p></body></html>");
            plainTextPane.setText(msg);
            listModel.clear(); // 清空列表
        }

        // 统一更新功能按钮的启用状态
        private void updateFunctionalityButtonsState() {
            boolean enabled = zhihuService.isCookieSetAndValid();
            refreshBtn.setEnabled(enabled);
            // 只有在有内容且Cookie有效时才启用分页按钮
            prevBtn.setEnabled(enabled && offsetCurrent > 0);
            nextBtn.setEnabled(enabled && !listModel.isEmpty()); // 假设如果listModel不空，就有可能有多页

            if (!enabled) {
                // 如果Cookie无效，强制禁用所有功能按钮并显示提示
                prevBtn.setEnabled(false);
                nextBtn.setEnabled(false);
                listModel.clear();
                showCookieRequiredMessage();
            }
        }

        // 弹出Cookie输入对话框
        private void showCookieInputDialog() {
            JTextArea cookieInput = new JTextArea(10, 40); // 10行40列
            cookieInput.setLineWrap(true);
            cookieInput.setWrapStyleWord(true);

            // 预填充当前保存的Cookie
            cookieInput.setText(zhihuService.getUserCookie());

            // 创建一个 JScrollPane 来包裹 JTextArea，防止内容溢出
            JScrollPane scrollPane = new JScrollPane(cookieInput);

            // 定义对话框的选项按钮
            String[] options = {"确定", "取消"};

            // 使用 JOptionPane.showOptionDialog 创建一个自定义对话框
            // project 参数对于 JOptionPane.showOptionDialog 并不直接使用，
            // 它是为了获取父组件的上下文，这里我们用 root 作为父组件。
            int result = JOptionPane.showOptionDialog(root, // 父组件
                    scrollPane, // 对话框的主要内容组件
                    "输入知乎Cookie", // 对话框标题
                    JOptionPane.YES_NO_OPTION, // 按钮类型
                    JOptionPane.QUESTION_MESSAGE, // 消息类型（图标）
                    null, // 自定义图标，null表示使用默认图标
                    options, // 按钮文本数组
                    options[0]); // 默认选中的按钮

            if (result == JOptionPane.YES_OPTION) { // 如果用户点击了“确定”按钮 (对应 options[0])
                String newCookie = cookieInput.getText().trim();
                // 保存Cookie到设置
                ZhihuReaderSettings settings = ZhihuReaderSettings.getInstance();
                settings.zhihuCookie = newCookie;
                // 更新Service中的Cookie
                zhihuService.setUserCookie(newCookie);

                // 根据新的Cookie状态更新UI
                updateFunctionalityButtonsState();
                if (zhihuService.isCookieSetAndValid()) {
                    loadRecommend(); // Cookie设置成功后，尝试加载推荐
                } else {
                    showCookieRequiredMessage(); // Cookie仍无效，显示提示
                }
            }
        }

        // 加载当前选中问题和 offset 对应的答案
        void loadSelectedQuestionAnswers() {
            Question q = menuList.getSelectedValue();
            if (q == null) return;

            if (!zhihuService.isCookieSetAndValid()) {
                showCookieRequiredMessage();
                return;
            }

            // 更新分页按钮状态
            prevBtn.setEnabled(offsetCurrent > 0);

            // 设置加载中提示
            String loadingText = "加载回答中...\n\n" + q.getTitle();
            htmlTextPane.setText("<html><body><h2>" + loadingText.replace("\n", "<br/>") + "</h2></body></html>");
            plainTextPane.setText(loadingText);

            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    // ZhihuService service = project.getService(ZhihuService.class); // 已持有引用，直接使用
                    List<com.github.newnovicechen.zhihureader.model.Answer> answers =
                            zhihuService.zhihuAnswer(q.getId(), offsetCurrent);

                    SwingUtilities.invokeLater(() -> {
                        StringBuilder htmlSb = new StringBuilder();
                        StringBuilder plainTextSb = new StringBuilder();

                        htmlSb.append("<html><body>");
                        htmlSb.append("<h2>问题：").append(q.getTitle()).append("</h2>");
                        htmlSb.append("<hr/>");

                        plainTextSb.append("问题：").append(q.getTitle()).append("\n\n");


                        if (answers.isEmpty()) {
                            String noAnswerText = "<p>没有找到更多回答。</p>";
                            htmlSb.append(noAnswerText);
                            plainTextSb.append(htmlToPlainText(noAnswerText));
                            nextBtn.setEnabled(false); // 如果当前页没有答案，下一页禁用
                        } else {
                            int i = offsetCurrent + 1;
                            for (var a : answers) {
                                // 预处理 HTML 内容，限制图片宽度
                                String processedHtmlContent = preprocessHtmlForSwing(a.getAnswerContent(), IMAGE_MAX_WIDTH);

                                // HTML 模式内容
                                htmlSb.append("<div class='answer-container'>")
                                        .append("<h3>回答 ").append(i).append(" - 作者：").append(a.getAuthorName()).append("</h3>")
                                        .append(processedHtmlContent) // 使用处理过的 HTML
                                        .append("</div>");

                                // 纯文本模式内容
                                plainTextSb.append("=== 回答 ").append(i).append(" ===\n")
                                        .append("作者：").append(a.getAuthorName()).append("\n\n")
                                        .append(htmlToPlainText(a.getAnswerContent())).append("\n\n");
                                i++;
                            }
                            nextBtn.setEnabled(answers.size() == PAGE_SIZE); // 只有当返回的数量达到PAGE_SIZE时才认为可能有下一页
                        }

                        htmlSb.append("</body></html>");
                        htmlTextPane.setText(htmlSb.toString());
                        plainTextPane.setText(plainTextSb.toString());

                        // 确保滚动到顶部
                        htmlTextPane.setCaretPosition(0);
                        plainTextPane.setCaretPosition(0);

                        // 切换到正确的视图
                        cardLayout.show(contentPanel, isHtmlMode ? HTML_MODE_CARD : PLAIN_TEXT_MODE_CARD);

                        // 无论如何，更新按钮状态（例如，如果没有答案，下一页按钮应禁用）
                        updateFunctionalityButtonsState();
                    });
                } catch (IllegalStateException ex) { // 捕获Service抛出的Cookie未设置异常
                    SwingUtilities.invokeLater(() -> {
                        showCookieRequiredMessage();
                        updateFunctionalityButtonsState();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        String errorHtml = "<html><body><b>加载失败：</b><br/>" + ex.getMessage() + "<br/>请检查Cookie是否有效或网络连接。</body></html>";
                        String errorPlainText = "加载失败：\n" + ex.getMessage() + "\n请检查Cookie是否有效或网络连接。";
                        htmlTextPane.setText(errorHtml);
                        plainTextPane.setText(errorPlainText);

                        cardLayout.show(contentPanel, isHtmlMode ? HTML_MODE_CARD : PLAIN_TEXT_MODE_CARD);
                        updateFunctionalityButtonsState();
                    });
                }
            });
        }

        // 初始加载推荐
        void loadRecommend() {
            if (!zhihuService.isCookieSetAndValid()) {
                showCookieRequiredMessage();
                updateFunctionalityButtonsState();
                return;
            }

            listModel.clear();
            String loadingHtml = "<html><body><p>加载中...</p></body></html>";
            String loadingPlainText = "加载中...";
            htmlTextPane.setText(loadingHtml);
            plainTextPane.setText(loadingPlainText);

            offsetCurrent = 0;
            updatePageLabel();

            // 在加载推荐前，先禁用分页按钮，待加载完成后再根据情况启用
            prevBtn.setEnabled(false);
            nextBtn.setEnabled(false);


            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    // ZhihuService service = project.getService(ZhihuService.class); // 已持有引用，直接使用
                    List<Question> questions = zhihuService.zhihuRecommend();

                    SwingUtilities.invokeLater(() -> {
                        listModel.clear();
                        if (questions.isEmpty()) {
                            String noRecommendHtml = "<html><body><p>没有加载到推荐内容，请检查Cookie是否有效。</p></body></html>";
                            String noRecommendPlainText = "没有加载到推荐内容，请检查Cookie是否有效。";
                            htmlTextPane.setText(noRecommendHtml);
                            plainTextPane.setText(noRecommendPlainText);
                        } else {
                            for (Question q : questions) listModel.addElement(q);

                            String loadedHtml = "<html><body><p>加载完成，左侧选择一个条目。</p></body></html>";
                            String loadedPlainText = "加载完成，左侧选择一个条目。";
                            htmlTextPane.setText(loadedHtml);
                            plainTextPane.setText(loadedPlainText);
                        }

                        // 确保显示当前模式的面板
                        cardLayout.show(contentPanel, isHtmlMode ? HTML_MODE_CARD : PLAIN_TEXT_MODE_CARD);

                        // 加载完成后更新按钮状态，例如，如果推荐列表为空，下一页按钮应禁用
                        updateFunctionalityButtonsState();
                    });
                } catch (IllegalStateException ex) { // 捕获Service抛出的Cookie未设置异常
                    SwingUtilities.invokeLater(() -> {
                        showCookieRequiredMessage();
                        updateFunctionalityButtonsState();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        String errorHtml = "<html><body><b>加载失败：</b><br/>" + ex.getMessage() + "<br/>请检查Cookie是否有效或网络连接。</body></html>";
                        String errorPlainText = "加载失败：\n" + ex.getMessage() + "\n请检查Cookie是否有效或网络连接。";
                        htmlTextPane.setText(errorHtml);
                        plainTextPane.setText(errorPlainText);

                        cardLayout.show(contentPanel, isHtmlMode ? HTML_MODE_CARD : PLAIN_TEXT_MODE_CARD);
                        updateFunctionalityButtonsState();
                    });
                }
            });
        }
    }
}