import java.util.*;

public class Environment {
    // 使用 Deque 来管理作用域
    private final Deque<Map<String, Object>> scopeStack = new ArrayDeque<>();

    public Environment() {
        pushScope();
    }

    public void pushScope() {
        scopeStack.push(new HashMap<>());
    }

    public void popScope() {
        if (scopeStack.size() <= 1) {
            throw new RuntimeException("不能弹出全局作用域！当前栈深度：" + scopeStack.size());
        }
        scopeStack.pop();
    }


    /**
     * 将一个变量名和对应的值存储到当前作用域中。
     *
     * @param name  变量名
     * @param value 变量的值
     */
    public void put(String name, Object value) {
        if (name == null) {
            throw new IllegalArgumentException("Variable name cannot be null");
        }
        if (scopeStack.peek() != null) {
            scopeStack.peek().put(name, value);
        }
    }

    /**
     * 查找指定名称的变量，并返回其值。
     * 从当前作用域开始，逐层向外查找，直到找到该变量或者遍历完所有作用域。
     *
     * @param name 变量名
     * @return 变量的值
     * @throws RuntimeException 如果变量未定义
     */
    public Object get(String name) {
        for (Map<String, Object> scope : scopeStack) {
            if (scope.containsKey(name)) {
                return scope.get(name);
            }
        }
        throw new RuntimeException("Undefined variable: " + name);
    }

    public void enterScope() {
        pushScope();
    }

    public void exitScope() {
        popScope();
    }

    /**
     * 获取当前所有作用域中的所有变量及其值
     *
     * @return 包含所有变量及其值的映射
     */
    public Map<String, Object> getAllVariables() {
        Map<String, Object> allVariables = new HashMap<>();
        for (Map<String, Object> scope : scopeStack) {
            allVariables.putAll(scope);
        }
        return allVariables;
    }
}