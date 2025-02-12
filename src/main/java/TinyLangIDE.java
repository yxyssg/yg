import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TinyLangIDE extends JFrame {
    // 替换原本的 JTextArea 为 JTextPane
    private Interpreter interpreter = new Interpreter();
    private JTextPane codeEditor = new JTextPane();
    private StyleContext styleContext = new StyleContext();
    private StyledDocument doc = codeEditor.getStyledDocument();
    // 在 IDE 类中添加行号面板
    private JTextArea lineNumbers = new JTextArea("1");
    private JScrollPane editorScroll;
    private JTextArea outputArea = new JTextArea();

    // 使用Timer延迟处理高亮
    private Timer highlightTimer = new Timer(300, e -> highlightSyntax());
    // 记录上次高亮状态
    private Map<Integer, Style> lastHighlightState = new HashMap<>();
    // 使用固定线程池
    private ExecutorService highlightExecutor = Executors.newSingleThreadExecutor();
    private Future<?> currentHighlightTask;

    // 新增字符串样式
    private Style stringStyle;

    // 在TinyLangIDE类新增成员变量
    private JTree variableTree = new JTree();
    private JPopupMenu autoCompletePopup = new JPopupMenu();
    private Set<Integer> breakpoints = new HashSet<>();
    private Debugger debugger = new Debugger();
    private ExecutorService codeExecutor = Executors.newSingleThreadExecutor();
    private Environment env = new Environment();
    private JSplitPane mainSplitPane;

    // 新增工具栏组件
    private JToolBar debugToolbar = new JToolBar();
    private JButton btnStepOver = new JButton("⏩ Step Over");
    private JButton btnAddWatch = new JButton("👁️ Add Watch");
    private JButton run = new JButton("▶ Run");

    // 新增状态栏
    private JLabel statusBar = new JLabel("Ready | Line: 1 Col: 1");

    private JComboBox<String> themeComboBox;
    // 主题列表
    private Map<String, Theme> themes = new HashMap<>();

    public TinyLangIDE() {
        setLayout(new BorderLayout());

        initThemes();

        // 初始化代码编辑区和行号面板
        editorScroll = new JScrollPane(codeEditor);
        editorScroll.setRowHeaderView(lineNumbers);

        // 动态更新行号
        codeEditor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                updateLineNumbers();
                scheduleHighlight();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                updateLineNumbers();
                scheduleHighlight();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateLineNumbers();
                scheduleHighlight();
            }

            private void updateLineNumbers() {
                int lines = codeEditor.getText().split("\n").length;
                StringBuilder numbers = new StringBuilder();
                for (int i = 1; i <= lines; i++) {
                    numbers.append(i).append("\n");
                }
                lineNumbers.setText(numbers.toString());
            }
        });

        // 初始化语法高亮
        initSyntaxHighlight();

        // 底部面板
        JPanel bottomPanel = new JPanel(new BorderLayout());
        run.addActionListener(e -> executeCode());

        // 输出区
        outputArea.setEditable(false);
        outputArea.setFont(new Font("微软雅黑", Font.PLAIN, 16));
        outputArea.setVisible(true);
        outputArea.setLineWrap(true);         // 启用自动换行
        outputArea.setWrapStyleWord(true);    // 按单词换行
        outputArea.setRows(5);                // 初始显示5行
        outputArea.setColumns(80);
        outputArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                scrollToBottom();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                scrollToBottom();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                scrollToBottom();
            }

            // 在DocumentListener中添加以下逻辑
            private void scrollToBottom() {
                SwingUtilities.invokeLater(() -> {
                    JScrollPane scrollPane = (JScrollPane) outputArea.getParent().getParent();

                    // 更新滚动条范围
                    scrollPane.getVerticalScrollBar().setValues(
                            scrollPane.getVerticalScrollBar().getValue(),
                            scrollPane.getVerticalScrollBar().getVisibleAmount(),
                            scrollPane.getVerticalScrollBar().getMinimum(),
                            scrollPane.getVerticalScrollBar().getMaximum()
                    );

                    // 滚动到底部
                    scrollPane.getVerticalScrollBar().setValue(
                            scrollPane.getVerticalScrollBar().getMaximum()
                    );
                });
            }
        });

        PrintStream ps = new PrintStream(new ByteArrayOutputStream() {
            @Override
            public void write(byte[] b, int off, int len) {
                SwingUtilities.invokeLater(() -> {
                    outputArea.append(new String(b, off, len));

                    // 强制更新布局
                    outputArea.revalidate();
                    outputArea.repaint();

                    // 自动滚动到底部
                    JScrollPane scrollPane = (JScrollPane) outputArea.getParent().getParent();
                    JScrollBar vertical = scrollPane.getVerticalScrollBar();
                    vertical.setValue(vertical.getMaximum());
                });
            }
        });

        bottomPanel.add(new JScrollPane(outputArea), BorderLayout.CENTER);

        JScrollPane watchPanel = new JScrollPane(variableTree);
        watchPanel.setPreferredSize(new Dimension(200, 600));

        // 主分割面板
        mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorScroll, watchPanel);
        mainSplitPane.setDividerLocation(600);

        // 创建一个新的面板来容纳 mainSplitPane 和 bottomPanel
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(mainSplitPane, BorderLayout.CENTER);
        centerPanel.add(bottomPanel, BorderLayout.SOUTH);

        // 将 centerPanel 添加到主窗口的 CENTER 位置
        add(centerPanel, BorderLayout.CENTER);

        // 初始化调试工具栏
        initDebugToolbar();

        // 初始化状态栏
        add(statusBar, BorderLayout.SOUTH);

        // 新增代码智能感知
        initCodeIntelligence();

        // 新增键盘监听
        initKeyBindings();

        // 主题选择下拉框
        themeComboBox = new JComboBox<>(themes.keySet().toArray(new String[0]));
        themeComboBox.addActionListener(e -> changeTheme());
        debugToolbar.add(themeComboBox);
        // 应用默认主题
        changeTheme();
        // 在TinyLangIDE构造函数末尾添加
        startVariableWatcher(); // 启动变量监视器
        initAIAssistant();      // 激活AI代码生成
        pack(); // 调整窗口大小以适应组件
        setSize(800, 600); // 设置窗口的初始大小
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);
    }
    // 新增方法：初始化调试工具栏
    private void initDebugToolbar() {

        debugToolbar.add(run);
        debugToolbar.add(btnStepOver);
        debugToolbar.add(new JButton("⏸ Pause"));
        debugToolbar.add(btnAddWatch);
        debugToolbar.addSeparator();

        // 添加断点管理按钮
        JButton btnToggleBreakpoint = new JButton("🔴 Toggle Breakpoint");
        btnToggleBreakpoint.addActionListener(e -> toggleBreakpoint(getCurrentLine()));
        debugToolbar.add(btnToggleBreakpoint);

        add(debugToolbar, BorderLayout.NORTH);
    }

    // 新增方法：代码智能感知
    private void initCodeIntelligence() {
        // 自动补全列表
        JList<String> suggestionList = new JList<>();
        autoCompletePopup.add(new JScrollPane(suggestionList));

        // 文档监听
        doc.addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) { updateSuggestions(); }
            public void insertUpdate(DocumentEvent e) { updateSuggestions(); }
            public void removeUpdate(DocumentEvent e) { updateSuggestions(); }
        });
    }

    // 新增方法：更新代码建议
    private void updateSuggestions() {
        List<String> suggestions = new ArrayList<>();

        // 从环境中获取变量
        env.getAllVariables().keySet().forEach(suggestions::add);

        // 获取当前输入上下文
        String currentWord = getCurrentWord();

        // 过滤建议
        List<String> filtered = suggestions.stream()
                .filter(s -> s.startsWith(currentWord))
                .collect(Collectors.toList());

        // 显示弹出窗口
        if (!filtered.isEmpty()) {
            showAutoComplete(filtered, codeEditor.getCaretPosition());
        }
    }

    // 新增方法：键盘快捷键
    private void initKeyBindings() {
        InputMap im = codeEditor.getInputMap();
        ActionMap am = codeEditor.getActionMap();

        // 添加Ctrl+S保存
        im.put(KeyStroke.getKeyStroke("control S"), "save");
        am.put("save", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                saveToFile();
            }
        });

        // 添加Ctrl+空格触发补全
        im.put(KeyStroke.getKeyStroke("control SPACE"), "complete");
        am.put("complete", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                forceShowAutoComplete();
            }
        });
    }

    // 新增方法：实时变量更新
    private void startVariableWatcher() {
        new Timer(1000, e -> {
            DefaultTreeModel model = (DefaultTreeModel) variableTree.getModel();
            DefaultMutableTreeNode root = new DefaultMutableTreeNode("Variables");

            // 从解释器环境获取变量
            env.getAllVariables().forEach((k, v) ->
                    root.add(new DefaultMutableTreeNode(k + " : " + v))
            );

            model.setRoot(root);
            model.reload();
        }).start();
    }

    // 新增断点管理
    private void toggleBreakpoint(int line) {
        if (breakpoints.contains(line)) {
            breakpoints.remove(line);
            removeLineHighlight(line);
        } else {
            breakpoints.add(line);
            highlightLine(line, new Color(255, 0, 0, 50)); // 半透明红色背景
        }
    }
    private void scheduleHighlight() {
        highlightTimer.stop();
        highlightTimer.start();

        // 取消之前的任务
        if(currentHighlightTask != null && !currentHighlightTask.isDone()) {
            currentHighlightTask.cancel(true);
        }

        currentHighlightTask = highlightExecutor.submit(() -> {
            try {
                Thread.sleep(300); // 等待输入停止
                if(!Thread.currentThread().isInterrupted()) {
                    highlightSyntax();
                }
            } catch (Exception ex) { /* 忽略 */ }
        });
    }

    private void highlightErrorLine(int line) {
        try {
            int start = codeEditor.getDocument().getDefaultRootElement().getElement(line - 1).getStartOffset();
            int end = codeEditor.getDocument().getDefaultRootElement().getElement(line - 1).getEndOffset();
            codeEditor.getHighlighter().addHighlight(start, end, new DefaultHighlighter.DefaultHighlightPainter(Color.PINK));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // 在构造方法中初始化样式
    private void initSyntaxHighlight() {
        // 定义基础样式
        Style defaultStyle = styleContext.addStyle("default", null);
        StyleConstants.setFontFamily(defaultStyle, "Consolas");
        StyleConstants.setFontSize(defaultStyle, 16);

        // 关键字样式（红色加粗）
        Style keywordStyle = styleContext.addStyle("keyword", defaultStyle);
        StyleConstants.setForeground(keywordStyle, Color.RED);
        StyleConstants.setBold(keywordStyle, true);

        // 数字样式（蓝色）
        Style numberStyle = styleContext.addStyle("number", defaultStyle);
        StyleConstants.setForeground(numberStyle, new Color(0, 0, 255));

        // 字符串样式（绿色）
        stringStyle = styleContext.addStyle("string", defaultStyle);
        StyleConstants.setForeground(stringStyle, Color.GREEN);

        // 修改DocumentListener
        codeEditor.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) {
                scheduleHighlight();
            }

            public void insertUpdate(DocumentEvent e) {
                scheduleHighlight();
            }

            public void removeUpdate(DocumentEvent e) {
                scheduleHighlight();
            }

            private void scheduleHighlight() {
                highlightTimer.stop(); // 取消之前的任务
                highlightTimer.restart(); // 重新计时

                // 取消之前的任务
                if (currentHighlightTask != null && !currentHighlightTask.isDone()) {
                    currentHighlightTask.cancel(true);
                }

                currentHighlightTask = highlightExecutor.submit(() -> {
                    try {
                        Thread.sleep(300); // 等待输入停止
                        if (!Thread.currentThread().isInterrupted()) {
                            highlightSyntax();
                        }
                    } catch (Exception ex) {
                        /* 忽略 */
                    }
                });
            }
        });
    }

    // 优化后的高亮方法
    private void highlightSyntax() {
        SwingUtilities.invokeLater(() -> {
            try {
                long startTime = System.currentTimeMillis();
                String text = doc.getText(0, doc.getLength());

                // 使用批量更新
                doc.setCharacterAttributes(0, doc.getLength(), styleContext.getStyle("default"), true);

                // 限制高亮范围（只高亮可见区域）
                Rectangle rect = codeEditor.getVisibleRect();
                int startOffset = codeEditor.viewToModel2D(new Point(0, rect.y));
                int endOffset = codeEditor.viewToModel2D(new Point(rect.width, rect.y + rect.height));

                // 优化正则表达式
                Pattern pattern = Pattern.compile(
                        "(//.*)|(\".*?\")|(\\b(var|circulate|print|if|else|for|function)\\b)|(\\d+)",
                        Pattern.MULTILINE
                );

                Matcher m = pattern.matcher(text);

                // 增量更新逻辑
                Map<Integer, Style> currentState = new HashMap<>();
                while (m.find()) {
                    if (m.start() >= startOffset && m.end() <= endOffset) {
                        for (int i = m.start(); i < m.end(); i++) {
                            Style style = m.group(1) != null ? styleContext.getStyle("keyword") :
                                    m.group(3) != null ? styleContext.getStyle("number") :
                                            styleContext.getStyle("string");
                            currentState.put(i, style);

                            // 只更新变化的部分
                            if (!style.equals(lastHighlightState.get(i))) {
                                doc.setCharacterAttributes(i, 1, style, false);
                            }
                        }
                    }
                }

                lastHighlightState = currentState;

                // 调试信息
                long time = System.currentTimeMillis() - startTime;
                if (time > 50) {
                    System.out.println("Syntax highlight took: " + time + "ms");
                }
            } catch (Exception ex) {
                /* 忽略 */
            }
        });
    }

    private void executeCode() {
        // 清空输出区域
        SwingUtilities.invokeLater(() -> outputArea.setText(""));

        // 重新设置输出重定向
        PrintStream ps = new PrintStream(new ByteArrayOutputStream() {
            @Override
            public void write(byte[] b, int off, int len) {
                SwingUtilities.invokeLater(() -> {
                    outputArea.append(new String(b, off, len));
                    JScrollPane scrollPane = (JScrollPane) outputArea.getParent().getParent();
                    JScrollBar vertical = scrollPane.getVerticalScrollBar();
                    vertical.setValue(vertical.getMaximum());
                });
            }
        });
        System.setOut(ps);

        codeExecutor.submit(() -> {
            try {
                Lexer lexer = new Lexer(codeEditor.getText());
                Parser parser = new Parser(lexer.tokenize());
                AST ast = parser.parseProgram();

                debugger.setAST(ast);
                debugger.startDebugging(breakpoints);

                // 调试模式下逐步执行
                while (debugger.isRunning()) {
                    if (debugger.shouldBreak()) {
                        SwingUtilities.invokeLater(() ->
                                statusBar.setText("Break at line: " + debugger.getCurrentLine())
                        );
                        debugger.waitForResume();
                    }

                    AST current = debugger.nextStep();
                    interpreter.interpret(current);
                    updateDebugViews();
                }
            } catch (Exception ex) {
                ex.printStackTrace(); // 打印详细异常信息
                showError(ex);
            }
        });
    }
    // 新增AI代码生成
    private void initAIAssistant() {
        JButton btnAIGenerate = new JButton("🤖 AI Generate");
        btnAIGenerate.addActionListener(e -> {
            String prompt = JOptionPane.showInputDialog("用自然语言描述你想要的功能：");
            String generatedCode = ChatGPT.generateCode(prompt);
            insertCodeAtCaret(generatedCode);
        });
        debugToolbar.add(btnAIGenerate);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TinyLangIDE().setVisible(true));
    }
// 在TinyLangIDE类添加以下方法实现

// 获取当前光标所在行号
private int getCurrentLine() {
    int caretPos = codeEditor.getCaretPosition();
    Element root = doc.getDefaultRootElement();
    return root.getElementIndex(caretPos) + 1;
}

// 高亮指定行
private void highlightLine(int lineNumber, Color color) {
    try {
        // 检查 lineNumber 是否在有效范围内
        int lineCount = doc.getDefaultRootElement().getElementCount();
        if (lineNumber >= 1 && lineNumber <= lineCount) {
            Element lineElement = doc.getDefaultRootElement().getElement(lineNumber - 1);
            int start = lineElement.getStartOffset();
            int end = lineElement.getEndOffset();
            codeEditor.getHighlighter().addHighlight(start, end,
                    new DefaultHighlighter.DefaultHighlightPainter(color));
        }
    } catch (Exception ex) {
        ex.printStackTrace();
    }
}
// 移除行高亮
private void removeLineHighlight(int lineNumber) {
    Arrays.stream(codeEditor.getHighlighter().getHighlights())
            .filter(h -> h.getStartOffset() == doc.getDefaultRootElement().getElement(lineNumber-1).getStartOffset())
            .forEach(h -> codeEditor.getHighlighter().removeHighlight(h));
}

// 保存到文件
private void saveToFile() {
    JFileChooser fileChooser = new JFileChooser();
    if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
        try (FileWriter writer = new FileWriter(fileChooser.getSelectedFile())) {
            writer.write(codeEditor.getText());
            statusBar.setText("文件保存成功: " + fileChooser.getSelectedFile().getName());
        } catch (IOException ex) {
            showError(ex);
        }
    }
}

// 强制显示自动补全
private void forceShowAutoComplete() {
    updateSuggestions();
    if (!autoCompletePopup.isVisible()) {
        autoCompletePopup.show(codeEditor,
                codeEditor.getCaret().getMagicCaretPosition().x,
                codeEditor.getCaret().getMagicCaretPosition().y + 20);
    }
}

// 显示自动补全列表
private void showAutoComplete(List<String> suggestions, int position) {
    JList<String> list = (JList<String>) ((JScrollPane)autoCompletePopup.getComponent(0)).getViewport().getView();
    list.setModel(new AbstractListModel<String>() {
        public int getSize() { return suggestions.size(); }
        public String getElementAt(int index) { return suggestions.get(index); }
    });

    list.addListSelectionListener(e -> {
        if (!e.getValueIsAdjusting()) {
            String selected = list.getSelectedValue();
            try {
                doc.insertString(position, selected, null);
                autoCompletePopup.setVisible(false);
            } catch (BadLocationException ex) { /* 忽略 */ }
        }
    });

    autoCompletePopup.show(codeEditor,
            codeEditor.getCaret().getMagicCaretPosition().x,
            codeEditor.getCaret().getMagicCaretPosition().y + 20);
}

// 获取当前输入词
private String getCurrentWord() {
    try {
        int caretPos = codeEditor.getCaretPosition();
        int start = caretPos;
        while (start > 0 && Character.isLetterOrDigit(doc.getText(start-1, 1).charAt(0))) {
            start--;
        }
        return doc.getText(start, caretPos - start);
    } catch (BadLocationException ex) {
        return "";
    }
}

// 更新调试视图
private void updateDebugViews() {
    // 更新变量树
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("Variables");
    env.getAllVariables().forEach((k, v) ->
            root.add(new DefaultMutableTreeNode(k + " : " + v))
    );
    variableTree.setModel(new DefaultTreeModel(root));

    // 高亮当前执行行
    int currentLine = debugger.getCurrentLine();
    highlightLine(currentLine, new Color(173, 216, 230, 80)); // 淡蓝色背景
}

// 显示错误信息
private void showError(Exception ex) {
    SwingUtilities.invokeLater(() -> {
        JOptionPane.showMessageDialog(this,
                "错误: " + ex.getMessage(),
                "运行时异常",
                JOptionPane.ERROR_MESSAGE);
        statusBar.setText("错误: " + ex.getMessage());
    });
}

// 在光标位置插入代码
private void insertCodeAtCaret(String code) {
    try {
        doc.insertString(codeEditor.getCaretPosition(), code, null);
    } catch (BadLocationException ex) {
        ex.printStackTrace();
    }
}
    // 在TinyLangIDE类中添加AI助手实现
    private static class ChatGPT {
        public static String generateCode(String prompt) {
            // 简单的规则匹配和代码生成
            if (prompt.contains("打印欢迎信息")) {
                return "print 'Welcome to the program!';";
            } else if (prompt.contains("定义变量")) {
                String[] parts = prompt.split("定义变量(.*)为(.*)");
                if (parts.length > 2) {
                    String variableName = parts[1].trim();
                    String value = parts[2].trim();
                    return "var " + variableName + " = " + value + ";";
                }
            }
            return "// 暂不支持根据此描述生成代码";
        }
    }
    private void initThemes() {
        // 浅色主题
        Theme lightTheme = new Theme(
                new Font("微软雅黑", Font.PLAIN, 16),
                Color.WHITE,
                Color.BLACK,
                Color.WHITE,
                Color.BLACK,
                Color.BLACK
        );
        themes.put("浅色主题", lightTheme);
        // 深色主题
        Theme darkTheme = new Theme(
                new Font("微软雅黑", Font.PLAIN, 16),
                Color.DARK_GRAY,
                Color.WHITE,
                Color.DARK_GRAY,
                Color.WHITE,
                Color.WHITE
        );
        themes.put("深色主题", darkTheme);
    }
    // 切换主题
    private void changeTheme() {
        String selectedTheme = (String) themeComboBox.getSelectedItem();
        Theme theme = themes.get(selectedTheme);
        // 更新代码编辑器样式
        codeEditor.setFont(theme.getFont());
        codeEditor.setBackground(theme.getEditorBackground());
        codeEditor.setForeground(theme.getEditorForeground());
        codeEditor.setCaretColor(theme.getCaretColor());
        lineNumbers.setFont(theme.getFont());
        lineNumbers.setBackground(theme.getEditorBackground());
        lineNumbers.setForeground(theme.getEditorForeground());
        // 更新输出区域样式
        outputArea.setFont(theme.getFont());
        outputArea.setBackground(theme.getOutputBackground());
        outputArea.setForeground(theme.getOutputForeground());
    }
}

class Debugger {
    private AST ast;
    private int currentStep;
    private boolean isRunning;
    private boolean shouldBreak;
    private List<AST> executionFlow = new ArrayList<>();
    private Set<Integer> breakpoints = new HashSet<>(); // 添加断点集合

    public void setAST(AST ast) {
        this.ast = ast;
        buildExecutionFlow();
        currentStep = 0;
    }

    private void buildExecutionFlow() {
        if (ast instanceof Program) {
            executionFlow = ((Program) ast).statements;
        }
    }

    public void startDebugging(Set<Integer> breakpoints) {
        this.breakpoints = new HashSet<>(breakpoints); // 初始化断点
        isRunning = true;
        currentStep = 0;
        shouldBreak = false;
    }

    public boolean isRunning() {
        return isRunning && currentStep < executionFlow.size();
    }

    public boolean shouldBreak() {
        return breakpoints.contains(getCurrentLine()) || shouldBreak;
    }

    public void waitForResume() {
        shouldBreak = true;
        while (shouldBreak) {
            try { Thread.sleep(100); }
            catch (InterruptedException e) { /* 忽略 */ }
        }
    }


    public AST nextStep() {
        if (currentStep < executionFlow.size()) {
            AST current = executionFlow.get(currentStep);
            currentStep++;
            return current;
        }
        return null;
    }

    public int getCurrentLine() {
        // 根据AST节点获取行号（需在AST节点中添加位置信息）
        return currentStep + 1; // 简化实现
    }

    public void step() { currentStep++; }
    public void continueExecution() { shouldBreak = false; }

    // 新增断点管理方法
    public void addBreakpoint(int line) {
        breakpoints.add(line);
    }

    public void removeBreakpoint(int line) {
        breakpoints.remove(line);
    }

    public boolean hasBreakpoint(int line) {
        return breakpoints.contains(line);
    }
}
// 字节码编译器
class BytecodeCompiler {
    public byte[] compile(AST tree) {
        // 将AST转换为字节码
        return new byte[0];
    }
}

// 虚拟机
class VM {
    public void execute(byte[] code) {
        // 执行字节码
    }
}
class Theme {
    private Font font;
    private Color editorBackground;
    private Color editorForeground;
    private Color outputBackground;
    private Color outputForeground;
    private Color caretColor;

    public Theme(Font font, Color editorBackground, Color editorForeground, Color outputBackground, Color outputForeground, Color caretColor) {
        this.caretColor = caretColor;
        this.font = font;
        this.editorBackground = editorBackground;
        this.editorForeground = editorForeground;
        this.outputBackground = outputBackground;
        this.outputForeground = outputForeground;
    }
    public Font getFont() {
        return font;
    }
    public Color getEditorBackground() {
        return editorBackground;
    }
    public Color getEditorForeground() {
        return editorForeground;
    }
    public Color getOutputBackground() {
        return outputBackground;
    }
    public Color getOutputForeground() {
        return outputForeground;
    }

    public Color getCaretColor() {
        return caretColor;
    }
}