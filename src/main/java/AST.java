import java.util.List;

abstract public class AST {
}
class NUM extends AST{
    int value;

    NUM(int value){
        this.value = value;
    }
}
class BinOp extends AST{
    AST Left;
    Token op;
    AST Right;

    BinOp(AST Left,Token op,AST Right){
        this.Left = Left;
        this.op = op;
        this.Right = Right;
    }
}
class VarDecl extends AST {
    String varName;
    AST expression;

    public VarDecl(String varName, AST expression) {
        this.varName = varName;
        this.expression = expression;
    }
}

// 打印节点
class PrintStmt extends AST {
    AST expr;

    PrintStmt(AST expr) {
        this.expr = expr;
    }
}

class IfStmt extends AST {
    AST condition;
    List<AST> thenBlock; // 修改为 List<AST> 类型
    List<AST> elseIfBlocks;
    List<AST> elseBlock; // 修改为 List<AST> 类型

    public IfStmt(AST condition, List<AST> thenBlock, List<AST> elseIfBlocks, List<AST> elseBlock, int line, int column) {
        this.condition = condition;
        this.thenBlock = thenBlock;
        this.elseIfBlocks = elseIfBlocks;
        this.elseBlock = elseBlock;
    }
}


// while 循环节点
class cycleStmt extends AST {
    AST condition;
    List<AST> bodyStatements; // 定义 bodyStatements 属性

    cycleStmt(AST condition, List<AST> bodyStatements) {
        this.condition = condition;
        this.bodyStatements = bodyStatements;
    }
}

// ForStmt 类
class ForStmt extends AST {
    List<AST> init;
    AST condition;
    List<AST> increment;
    List<AST> body;

    public ForStmt(List<AST> init, AST condition, List<AST> increment, List<AST> body) {
        this.init = init;
        this.condition = condition;
        this.increment = increment;
        this.body = body;
    }
}

class FunctionDef extends AST {
    String funcName;
    List<String> params;
    Program body; // 确保 body 属性类型为 Program
    String returnType;
    int startLine;
    int startColumn;

    public FunctionDef(String funcName, List<String> params, Program body, String returnType, int startLine, int startColumn) {
        this.funcName = funcName;
        this.params = params;
        this.body = body;
        this.returnType = returnType;
        this.startLine = startLine;
        this.startColumn = startColumn;
    }
}
class FunctionCall extends AST {
    String funcName;
    List<AST> args;
    public FunctionCall(String funcName, List<AST> args) {
        this.funcName = funcName;
        this.args = args;
    }
}
// 返回语句节点
class ReturnStmt extends AST {
    AST expression;
    public ReturnStmt(AST expression) {
        this.expression = expression;
    }
}

// 定义 Identifier 类，用于标识符
class Identifier extends AST {
    String name;

    public Identifier(String name) {
        this.name = name;
    }
}
class VarRef extends AST {
    String varName;

    public VarRef(String varName) {
        this.varName = varName;
    }
}
class Program extends AST {
    List<AST> statements;

    public Program(List<AST> statements) {
        this.statements = statements;
    }
}
class StringLiteral extends AST {
    String value;

    public StringLiteral(String value) {
        this.value = value;
    }
}
class BooleanNode extends AST {
    boolean value;

    public BooleanNode(boolean value) {
        this.value = value;
    }
}
class UnaryOp extends AST {
    Token op;
    AST operand;

    public UnaryOp(Token op, AST operand) {
        this.op = op;
        this.operand = operand;
    }
}
// AST.java 新增赋值节点
class AssignOp extends AST {
    AST left;
    AST right;

    public AssignOp(AST left, AST right) {
        this.left = left;
        this.right = right;
    }
}

