import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final List<Token> tokens;
    private int currentTokenIndex = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }
    private AST parseTerm() {
        Token numberToken = currentToken();
        eat(TokenType.Number);
        return new NUM(Integer.parseInt(numberToken.value));
    }

    // 假设 comparison 方法如下
    private AST comparison() {
        AST left = additive();
        while (currentToken() != null && (currentToken().type == TokenType.GREATER || currentToken().type == TokenType.LESS ||
                currentToken().type == TokenType.GREATER_EQUAL || currentToken().type == TokenType.LESS_EQUAL)) {
            Token op = currentToken();
            eat(op.type);
            AST right = additive();
            left = new BinOp(left, op, right);
        }
        return left;
    }
    private boolean isComparisonOperator(TokenType type) {
        return type == TokenType.EQUAL || type == TokenType.NOT_EQUAL
                || type == TokenType.GREATER || type == TokenType.LESS
                || type == TokenType.GREATER_EQUAL || type == TokenType.LESS_EQUAL;
    }

    private AST parseWhileStatement() {
        eat(TokenType.WHILE);
        // 解析条件表达式
        AST condition = expression();
        eat(TokenType.LBRACE);

        List<AST> bodyStatements = new ArrayList<>();
        // 解析语句块，直到遇到右花括号
        while (currentToken() != null && currentToken().type != TokenType.RBRACE) {
            bodyStatements.add(parseStatement());
        }
        eat(TokenType.RBRACE);

        return new cycleStmt(condition, bodyStatements);
    }

    // 重写表达式解析逻辑
    public AST expression() {
        return assignment(); // 从最顶层开始解析赋值
    }
    private AST assignment() {
        AST left = logicalOr();
        if (isAssignmentOperator(currentToken().type)) {
            return parseAssignment();
        }
        return left;
    }

    private AST logicalOr() {
        AST left = logicalAnd();
        Token op = currentToken();
        int line = op.line;
        int column = op.column;
        while (eatIf(TokenType.OR) != null) {
            left = new BinOp(left, new Token(TokenType.OR, "||",line,column), logicalAnd());
        }
        return left;
    }

    private AST logicalAnd() {
        AST left = equality();
        Token op = currentToken();
        int line = op.line;
        int column = op.column;
        while (eatIf(TokenType.AND) != null) {
            left = new BinOp(left, new Token(TokenType.AND, "&&",line,column), equality());
        }
        return left;
    }

    // 继续实现equality()→comparison()→term()→factor()的层级
    private AST parsePrintStatement() {
        eat(TokenType.PRINT);
        AST expression;
        if (currentToken().type == TokenType.STRING) {
            // 处理字符串字面量
            String stringValue = currentToken().value;
            eat(TokenType.STRING);
            expression = new StringLiteral(stringValue); // 假设存在 StringLiteral 类表示字符串字面量
        } else if (currentToken().type == TokenType.IDENTIFIER) {
            String varName = currentToken().value;
            eat(TokenType.IDENTIFIER);
            expression = new VarRef(varName);
        } else {
            expression = expression();
        }
        eat(TokenType.SEMICOLON);
        return new PrintStmt(expression);
    }

    private AST parseIfStatement() {
        eat(TokenType.IF);
        AST condition = expression();
        eat(TokenType.LBRACE);
        List<AST> thenBlock = parseBlock();
        eat(TokenType.RBRACE);

        List<AST> elseIfBlocks = new ArrayList<>();
        List<AST> elseBlock = null;

        while (currentToken() != null && currentToken().type == TokenType.ELSE) {
            eat(TokenType.ELSE);
            if (currentToken().type == TokenType.IF) {
                elseIfBlocks.add(parseIfStatement());
            } else {
                eat(TokenType.LBRACE);
                elseBlock = parseBlock();
                eat(TokenType.RBRACE);
                break;
            }
        }

        return new IfStmt(condition, thenBlock, elseIfBlocks, elseBlock, currentToken().line, currentToken().column);
    }
    private List<AST> parseBlock() {
        List<AST> statements = new ArrayList<>();
        while (currentToken() != null && currentToken().type != TokenType.RBRACE) {
            statements.add(parseStatement());
        }
        return statements;
    }
    private AST parseVarDeclaration() {
        eat(TokenType.Var);
        String varName = currentToken().value;
        eat(TokenType.IDENTIFIER);
        eat(TokenType.assignment);
        AST expression = expression();

        // 返回变量声明节点，不消耗分号
        return new VarDecl(varName, expression);
    }
    // 修改parseForStatement方法
    private AST parseForStatement() {
        eat(TokenType.FOR);

        // ===== 初始化部分 =====
        List<AST> inits = new ArrayList<>();
        if (currentToken().type != TokenType.SEMICOLON) {
            inits.add(parseForInit());
            while (eatIf(TokenType.COMMA) != null) {
                inits.add(parseForInit());
            }
        }
        eat(TokenType.SEMICOLON);

        // ===== 条件部分 =====
        AST condition = new BooleanNode(true);
        if (currentToken().type != TokenType.SEMICOLON) {
            condition = expression();
        }
        eat(TokenType.SEMICOLON);

        // ===== 迭代部分 =====（重点修改！）
        List<AST> increments = new ArrayList<>();
        if (currentToken().type != TokenType.LBRACE) {
            do {
                // 使用parseExpressionStatement来处理带分号的表达式
                increments.add(parseExpressionStatement());
            } while (eatIf(TokenType.COMMA) != null);
        }

        // ===== 强制锁定{ =====
        eat(TokenType.LBRACE);
        List<AST> body = parseBlock();
        eat(TokenType.RBRACE);

        return new ForStmt(inits, condition, increments, body);
    }

    // 新增表达式语句解析
    private AST parseExpressionStatement() {
        AST expr = expression();
        // 吃掉可能存在的分号（允许迭代部分带分号）
        if (currentToken().type == TokenType.SEMICOLON) {
            eat(TokenType.SEMICOLON);
        }
        return expr;
    }

    // 新增辅助方法
    private Token eatIf(TokenType type) {
        if (currentToken().type == type) {
            return eat(type);
        }
        return null;
    }

    private AST parseFunctionDeclaration() {
        // 在函数声明开始时记录行号和列号
        int startLine = currentToken().line;
        int startColumn = currentToken().column;

        eat(TokenType.FUNCTION);
        String funcName = currentToken().value;
        eat(TokenType.IDENTIFIER);
        eat(TokenType.LPAREN);
        List<String> params = new ArrayList<>();

        if (currentToken().type == TokenType.IDENTIFIER) {
            params.add(currentToken().value);
            eat(TokenType.IDENTIFIER);
            while (currentToken().type == TokenType.COMMA) {
                eat(TokenType.COMMA);
                if (currentToken().type != TokenType.IDENTIFIER) {
                    throw new RuntimeException("Line " + currentToken().line + " Col " + currentToken().column + ": 期望参数标识符，却得到 " + currentToken().type);
                }
                params.add(currentToken().value);
                eat(TokenType.IDENTIFIER);
            }
        }
        eat(TokenType.RPAREN);

        String returnType = null;
        if (currentToken().type == TokenType.RETURNS) {
            eat(TokenType.RETURNS);
            if (currentToken().type != TokenType.IDENTIFIER) {
                throw new RuntimeException("Line " + currentToken().line + " Col " + currentToken().column + ": 期望返回值类型标识符，却得到 " + currentToken().type);
            }
            returnType = currentToken().value;
            eat(TokenType.IDENTIFIER);
        }

        eat(TokenType.LBRACE);
        List<AST> body = parseBlock();
        eat(TokenType.RBRACE);

        return new FunctionDef(funcName, params, new Program(body), returnType, startLine, startColumn);
    }
    // 解析函数调用
    private AST parseFunctionCall(String funcName) {
        eat(TokenType.LPAREN);
        List<AST> args = new ArrayList<>();
        if (currentToken().type != TokenType.RPAREN) {
            args.add(expression());
            while (currentToken().type == TokenType.COMMA) {
                eat(TokenType.COMMA);
                args.add(expression());
            }
        }
        eat(TokenType.RPAREN);
        return new FunctionCall(funcName, args);
    }
    private AST parseReturnStatement() {
        eat(TokenType.RETURNS);
        AST expression = expression(); // 解析 return 后面的表达式
        eat(TokenType.SEMICOLON); // 消耗分号
        return new ReturnStmt(expression);
    }
    public AST parseProgram() {
        List<AST> statements = new ArrayList<>();
        while (currentToken().type != TokenType.End) {
            AST statement = parseStatement();
            statements.add(statement);
        }
        return new Program(statements); // 返回一个表示程序的 AST 节点
    }

    private AST parseStatement() {
        if (currentToken() != null) {
            switch (currentToken().type) {
                case Var:
                    AST varDecl = parseVarDeclaration();
                    // 单独调用 var 声明变量时，消耗分号
                    if (currentToken().type == TokenType.SEMICOLON) {
                        eat(TokenType.SEMICOLON);
                    }
                    return varDecl;
                case IDENTIFIER:
                    String funcName = currentToken().value;
                    eat(TokenType.IDENTIFIER);
                    System.out.println("遇到标识符: " + funcName);
                    if (currentToken() != null && currentToken().type == TokenType.LPAREN) {
                        System.out.println("即将调用 parseFunctionCall 方法");
                        return parseFunctionCall(funcName);
                    }
                    System.out.println("不是函数调用，作为变量引用处理");
                    return new VarRef(funcName);
                case PRINT:
                    return parsePrintStatement();
                case IF:
                    return parseIfStatement();
                case WHILE:
                    return parseWhileStatement();
                case FOR:
                    return parseForStatement();
                case FUNCTION:
                    return parseFunctionDeclaration();
                case RETURNS:
                    return parseReturnStatement();
                case INCREMENT:
                case DECREMENT:
                    AST incrementOrDecrementExpr = expression();
                    if (currentToken().type == TokenType.SEMICOLON) {
                        eat(TokenType.SEMICOLON);
                    }
                    return incrementOrDecrementExpr;
                default:
                    AST expr = expression();
                    if (currentToken().type == TokenType.SEMICOLON) {
                        eat(TokenType.SEMICOLON);
                    }
                    return expr;
            }
        }
        return null;
    }


    public Token currentToken(){
        if (currentTokenIndex >= tokens.size()){
            return tokens.getLast();
        }
        return tokens.get(currentTokenIndex);
    }
    public void advance(){
        currentTokenIndex++;
    }
    public Token eat(TokenType expected) {
        if (currentToken() == null) {
            throw new ParseError("代码突然结束啦！你是不是忘记写 " + expected + " 了？", -1, -1);
        }
        if (currentToken().type != expected) {
            String template = "\n正确示例：\n"
                    + "if (a > 0 && b < 10) {\n"
                    + "    print '条件成立';\n"
                    + "}";
            throw new ParseError("期望看到 " + expected + " 但是发现了 " + currentToken().type
                    + " (值: '" + currentToken().value + "')" + template,
                    currentToken().line, currentToken().column);
        }
        Token consumed = currentToken();
        advance();
        return consumed;
    }
    private AST factor() {
        AST node = primary();
        while (currentToken().type == TokenType.INCREMENT
                || currentToken().type == TokenType.DECREMENT) {
            node = parseAssignment(); // 关键修改！调用parseAssignment
        }
        return node;
    }
    private AST primary() {
        Token token = currentToken();
        int line = token.line;
        int column = token.column;
        switch (token.type) {
            case Number:
                eat(TokenType.Number);
                return new NUM(Integer.parseInt(token.value));
            case TRUE:
                eat(TokenType.TRUE);
                return new BooleanNode(true);
            case FALSE:
                eat(TokenType.FALSE);
                return new BooleanNode(false);
            case STRING:
                eat(TokenType.STRING);
                return new StringLiteral(token.value);
            case IDENTIFIER:
                String funcOrVarName = token.value;
                eat(TokenType.IDENTIFIER);
                if (currentToken() != null && currentToken().type == TokenType.LPAREN) {
                    return parseFunctionCall(funcOrVarName);
                }
                return new VarRef(funcOrVarName);
            case LPAREN:
                eat(TokenType.LPAREN);
                AST expression = expression();
                eat(TokenType.RPAREN);
                return expression;
            default:
                throw new ParseError("Line " + line + " Col " + column + ": Unexpected token in primary: " + token.type, line, column);
        }
    }

    private AST term() {
        AST node = factor();
        while (currentToken().type == TokenType.multiply
                || currentToken().type == TokenType.Dividing
                || currentToken().type == TokenType.Remainder) {
            Token op = currentToken();
            eat(op.type);
            node = new BinOp(node, op, factor());
        }
        return node;
    }


    public AST parse() {
        AST result = parseProgram();
        // 检查当前 token 类型
        if (currentToken() == null || currentToken().type != TokenType.End) {
            throw new RuntimeException("Expected END token, but got " + (currentToken() != null ? currentToken().type : "null"));
        }
        eat(TokenType.End);
        return result;
    }
    // 修改parseAssignment方法
    private AST parseAssignment() {
        AST left = term();

        // 处理自增自减
        if (currentToken().type == TokenType.INCREMENT) {
            Token op = currentToken();
            int line = op.line;
            int column = op.column;
            eat(TokenType.INCREMENT);
            return new AssignOp(left, new BinOp(left, new Token(TokenType.add, "+",line,column), new NUM(1)));
        } else if (currentToken().type == TokenType.DECREMENT) {
            Token op = currentToken();
            int line = op.line;
            int column = op.column;
            eat(TokenType.DECREMENT);
            return new AssignOp(left, new BinOp(left, new Token(TokenType.remove, "-",line,column), new NUM(1)));
        }

        // 处理复合赋值
        if (isAssignmentOperator(currentToken().type)) {
            Token op = currentToken();
            int line = op.line;
            int column = op.column;
            eat(op.type);
            AST right = expression();
            return new AssignOp(    left,
                    new BinOp(left, convertAssignOp(op),right));
        }

        return left;
    }

    // 新增 convertAssignOp 方法，根据赋值运算符类型转换为对应的运算符 Token
    private Token convertAssignOp(TokenType assignOpType, int line, int column) {
        switch (assignOpType) {
            case ADD_ASSIGN:
                return new Token(TokenType.add, "+", line, column);
            case SUB_ASSIGN:
                return new Token(TokenType.remove, "-", line, column);
            case MUL_ASSIGN:
                return new Token(TokenType.multiply, "*", line, column);
            case DIV_ASSIGN:
                return new Token(TokenType.Dividing, "/", line, column);
            // 可以根据需要添加更多复合赋值运算符的转换
            default:
                throw new RuntimeException("Unsupported assignment operator: " + assignOpType);
        }
    }
    private AST handleIncrement() {
        Token op = currentToken();
        eat(TokenType.INCREMENT);
        AST left = term(); // 获取自增操作的目标
        return new BinOp(left, op, new NUM(1)); // 构建一个 BinOp 节点，相当于 left + 1
    }
    private AST handleDecrement() {
        Token op = currentToken();
        eat(TokenType.DECREMENT);
        AST left = term(); // 获取自减操作的目标
        return new BinOp(left, op, new NUM(1)); // 构建一个 BinOp 节点，相当于 left - 1
    }

    // 新增 isAssignmentOperator 方法，判断是否为赋值运算符
    private boolean isAssignmentOperator(TokenType type) {
        return type == TokenType.assignment || type == TokenType.ADD_ASSIGN || type == TokenType.SUB_ASSIGN
                || type == TokenType.MUL_ASSIGN || type == TokenType.DIV_ASSIGN;
    }

    private Token convertAssignOp(Token originalOpToken) {
        return switch (originalOpToken.type) {
            case ADD_ASSIGN ->
                    new Token(TokenType.add, "+", originalOpToken.line, originalOpToken.column);
            case SUB_ASSIGN ->
                    new Token(TokenType.remove, "-", originalOpToken.line, originalOpToken.column);
            case MUL_ASSIGN ->
                    new Token(TokenType.multiply, "*", originalOpToken.line, originalOpToken.column);
            case DIV_ASSIGN ->
                    new Token(TokenType.Dividing, "/", originalOpToken.line, originalOpToken.column);
            default -> throw new RuntimeException("不支持的运算符: " + originalOpToken.type);
        };
    }
    private AST equality() {
        AST left = comparison();
        while (currentToken().type == TokenType.EQUAL
                || currentToken().type == TokenType.NOT_EQUAL) {
            Token op = currentToken();
            eat(op.type);
            left = new BinOp(left, op, comparison());
        }
        return left;
    }

    // 补全parseForInit方法
    private AST parseForInit() {
        if (currentToken().type == TokenType.Var) {
            return parseVarDeclaration();
        } else {
            return expression();
        }
    }
    private AST unary() {
        if (currentToken() != null && currentToken().type == TokenType.IDENTIFIER) {
            AST operand = primary(); // 解析变量
            if (currentToken() != null && currentToken().type == TokenType.INCREMENT) {
                Token op = currentToken();
                eat(TokenType.INCREMENT);
                return new UnaryOp(op, operand);
            }
            return operand;
        }
        return primary();
    }
    private AST multiplicative() {
        AST left = unary();
        while (currentToken() != null && (currentToken().type == TokenType.multiply || currentToken().type == TokenType.Dividing)) {
            Token op = currentToken();
            eat(op.type);
            AST right = unary();
            left = new BinOp(left, op, right);
        }
        return left;
    }
    private AST additive() {
        AST left = multiplicative();
        while (currentToken() != null && (currentToken().type == TokenType.add || currentToken().type == TokenType.remove)) {
            Token op = currentToken();
            eat(op.type);
            AST right = multiplicative();
            left = new BinOp(left, op, right);
        }
        return left;
    }
}
class ParseError extends RuntimeException {
    private final int line;
    private final int column;

    public ParseError(String message, int line, int column) {
        super(message);
        this.line = line;
        this.column = column;
    }

    @Override
    public String getMessage() {
        return "Line " + line + " Col " + column + ": " + super.getMessage();
    }
}
