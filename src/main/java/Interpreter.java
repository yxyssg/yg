import java.util.*;

public class Interpreter {
    private final Environment env = new Environment();
    private final Map<String, FunctionDef> functions = new HashMap<>();
    public Interpreter() {
        // 初始化全局作用域
        env.enterScope();
    }
    public Object interpret(AST tree) {
        if (tree instanceof Program) {
            Program program = (Program) tree;
            Object result = null;
            for (AST statement : program.statements) {
                result = interpret(statement);
                if (result instanceof ReturnValue) {
                    return ((ReturnValue) result).value;
                }
            }
            return result;
        } else if (tree instanceof NUM) {
            return ((NUM) tree).value;
        } else if (tree instanceof StringLiteral) {
            return ((StringLiteral) tree).value;
        } else if (tree instanceof BooleanNode) {
            return ((BooleanNode) tree).value;
        } else if (tree instanceof BinOp) {
            BinOp binOp = (BinOp) tree;
            Object leftVal = interpret(binOp.Left);
            Object rightVal = interpret(binOp.Right);
            // 强制类型检查（突然变出类型检测器）
            if (!(leftVal instanceof Integer) || !(rightVal instanceof Integer)) {
                throw new RuntimeException("Line " + binOp.op.line + ": 数字运算需要整数哦笨蛋！");
            }
            int left = (Integer) leftVal;
            int right = (Integer) rightVal;
            // 运算符大作战（用教鞭戳屏幕）
            switch (binOp.op.type) {
                case add:
                    return left + right;
                case remove:
                    return left - right;
                case multiply:
                    return left * right;
                case Dividing:
                    if (right == 0) {
                        throw new RuntimeException("Line " + binOp.op.line + ": 除数不能为零");
                    }
                    return left / right;
                case Remainder: // 添加取余运算处理
                    if (right == 0) {
                        throw new RuntimeException("Line " + binOp.op.line + ": 取余运算除数不能为零");
                    }
                    return left % right;
                case EQUAL:
                    return left == right;
                case NOT_EQUAL:
                    return left != right;
                case GREATER:
                    return left > right;
                case LESS:
                    return left < right;
                default:
                    throw new RuntimeException("Line " + binOp.op.line + ": 不支持的运算符 " + binOp.op.type);
            }
        } else if (tree instanceof UnaryOp) {
            UnaryOp unaryOp = (UnaryOp) tree;
            Object operandValue = interpret(unaryOp.operand);
            Token op = unaryOp.op;
            if (op.type == TokenType.INCREMENT) {
                if (operandValue instanceof Integer) {
                    int newValue = (int) operandValue + 1;
                    // 假设 operand 是一个变量引用，更新变量的值
                    if (unaryOp.operand instanceof VarRef) {
                        VarRef varRef = (VarRef) unaryOp.operand;
                        env.put(varRef.varName, newValue);
                    }
                    return newValue;
                } else {
                    throw new RuntimeException("自增运算符只能用于整数类型");
                }
            } else if (op.type == TokenType.NOT) {
                if (operandValue instanceof Boolean) {
                    return !(boolean) operandValue;
                }
            }
            throw new RuntimeException("Unsupported operation: " + op.type);
        } else if (tree instanceof VarDecl) {
            VarDecl varDecl = (VarDecl) tree;
            Object value = interpret(varDecl.expression);
            env.put(varDecl.varName, value);
            return value;
        } else if (tree instanceof PrintStmt) {
            PrintStmt printStmt = (PrintStmt) tree;
            Object value = interpret(printStmt.expr);
            System.out.println(value);
            return value;
        } else if (tree instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) tree;
            Object conditionValue = interpret(ifStmt.condition);
            if (conditionValue instanceof Boolean) {
                if ((boolean) conditionValue) {
                    // 处理 thenBlock 列表中的每个语句
                    for (AST stmt : ifStmt.thenBlock) {
                        interpret(stmt);
                    }
                } else {
                    // 处理 else if 链
                    for (AST elseIfBlock : ifStmt.elseIfBlocks) {
                        if (elseIfBlock instanceof IfStmt) {
                            IfStmt elseIf = (IfStmt) elseIfBlock;
                            Object elseIfConditionValue = interpret(elseIf.condition);
                            if (elseIfConditionValue instanceof Boolean && (boolean) elseIfConditionValue) {
                                // 处理 else if 的 thenBlock 列表中的每个语句
                                for (AST stmt : elseIf.thenBlock) {
                                    interpret(stmt);
                                }
                                return null;
                            }
                        }
                    }
                    // 处理 else 块
                    if (ifStmt.elseBlock != null) {
                        // 处理 elseBlock 列表中的每个语句
                        for (AST stmt : ifStmt.elseBlock) {
                            interpret(stmt);
                        }
                    }
                }
            } else {
                throw new RuntimeException("条件表达式必须计算为布尔值，实际得到: " + conditionValue.getClass().getName());
            }
            return null;
        } else if (tree instanceof cycleStmt) {
            cycleStmt whileStmt = (cycleStmt) tree;
            // 直接使用布尔值进行判断
            while ((boolean) interpret(whileStmt.condition)) {
                for (AST stmt : whileStmt.bodyStatements) {
                    interpret(stmt);
                }
            }
            return null;
        } else if (tree instanceof ForStmt) {
            ForStmt forStmt = (ForStmt) tree;
            // 解释初始化部分的每个表达式
            for (AST initExpr : forStmt.init) {
                interpret(initExpr);
            }
            // 循环执行，直到条件为假
            while ((boolean) interpret(forStmt.condition)) {
                // 解释循环体中的每个语句
                for (AST stmt : forStmt.body) {
                    interpret(stmt);
                }
                // 解释迭代部分的每个表达式
                for (AST incrExpr : forStmt.increment) {
                    interpret(incrExpr);
                }
            }
            return null;
        } else if (tree instanceof VarRef) {
            VarRef varRef = (VarRef) tree;
            try {
                return env.get(varRef.varName);
            } catch (RuntimeException e) {
                throw new RuntimeException("Line " + varRef.varName + ": " + e.getMessage());
            }
        } else if (tree instanceof FunctionDef) {
            FunctionDef func = (FunctionDef) tree;
            functions.put(func.funcName, func);
            return null;
        }else if (tree instanceof FunctionCall) {
            FunctionCall call = (FunctionCall) tree;
            FunctionDef func = functions.get(call.funcName);

            // 创建新作用域
            env.pushScope();

            // 绑定参数
            for (int i=0; i<func.params.size(); i++) {
                Object value = interpret(call.args.get(i));
                env.put(func.params.get(i), value);
            }

            // 执行函数体
            Object result = null;
            for (AST stmt : func.body.statements) {
                result = interpret(stmt);
                if (result instanceof ReturnValue) {
                    break;
                }
            }

            // 销毁作用域
            env.popScope();
            return result instanceof ReturnValue ? ((ReturnValue)result).value : result;
        } else if (tree instanceof AssignOp) {
            VarRef varRef = (VarRef) ((AssignOp) tree).left;
            Object value = interpret(((AssignOp) tree).right);
            env.put(varRef.varName, value);
            return value;
        } else if (tree instanceof ReturnStmt) {
            Object value = interpret(((ReturnStmt) tree).expression);
            return new ReturnValue(value);
        } else {
            throw new RuntimeException("Unsupported AST node type: " + tree.getClass().getName());
        }
    }
    public Environment getEnvironment() {
        return env;
    }
}
class ReturnValue extends RuntimeException {
    final Object value;
    public ReturnValue(Object value) {
        this.value = value;
    }
}