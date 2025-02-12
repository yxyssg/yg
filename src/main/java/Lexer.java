import java.util.ArrayList;
import java.util.List;

public class Lexer {
    private final String input;
    private int position;
    private int line = 1;
    private int column = 1;
    public Lexer(String input) {
        this.input = input;
    }
    public char currentChar() {
        if (position >= input.length()) {
            return '\0';
        }
        return input.charAt(position);
    }
    private void advance() {
        if (currentChar() == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        position++;
    }
    public void skipWhitespace() {
        while (Character.isWhitespace(currentChar())) {
            advance();
        }
    }
    public String number() {
        StringBuilder result = new StringBuilder();
        while (Character.isDigit(currentChar())) {
            result.append(currentChar());
            advance();
        }
        return result.toString();
    }
    public String identifier() {
        StringBuilder result = new StringBuilder();
        while (Character.isLetterOrDigit(currentChar())) {
            result.append(currentChar());
            advance();
        }
        return result.toString();
    }
    private Token handleString() {
        advance();
        StringBuilder sb = new StringBuilder();
        while (currentChar() != '"' && currentChar() != '\0') {
            if (currentChar() == '\\') { // 处理转义
                advance();
                switch (currentChar()) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    // 其他转义字符...
                }
            } else {
                sb.append(currentChar());
            }
            advance();
        }
        String value = sb.toString();
        if ("true".equals(value)) {
            return new Token(TokenType.TRUE, value, line, column - sb.length() - 2);
        } else if ("false".equals(value)) {
            return new Token(TokenType.FALSE, value, line, column - sb.length() - 2);
        }
        if (currentChar() == '"') {
            advance(); // 消耗掉结束的双引号
            return new Token(TokenType.STRING, sb.toString(), line, column - sb.length() - 2);
        } else {
            throw new RuntimeException("Line " + line + " Col " + column + ": 引号未闭合");
        }
    }
    private char peekNextChar() {
        return (position + 1 < input.length()) ? input.charAt(position + 1) : '\0';
    }
    private Token handleDoubleCharOperator(char firstChar, TokenType singleType, TokenType doubleType, String doubleValue) {
        advance();
        if (currentChar() == doubleValue.charAt(1)) {
            advance();
            return new Token(doubleType, doubleValue, line, column - 2);
        } else {
            return new Token(singleType, String.valueOf(firstChar), line, column - 1);
        }
    }
    private void skipSingleLineComment() {
        while (currentChar() != '\0' && currentChar() != '\n') {
            advance();
        }
        if (currentChar() == '\n') {
            advance();
        }
    }
    public Token nextToken() {
        skipWhitespace();
        if (currentChar() == '\0') {
            return new Token(TokenType.End, "", line, column);
        }
        if (currentChar() == '/' && peekNextChar() == '/') {
            skipSingleLineComment();
            return nextToken();
        } else if (currentChar() == '/' && peekNextChar() == '*') {
            skipMultiLineComment();
            return nextToken();
        }

        if (Character.isDigit(currentChar())) {
            String num = number();
            return new Token(TokenType.Number, num, line, column - num.length());
        } else if (Character.isLetter(currentChar())) {
            String id = identifier();
            TokenType type;
            switch (id) {
                case "var":
                    type = TokenType.Var;
                    break;
                case "print":
                    type = TokenType.PRINT;
                    break;
                case "if":
                    type = TokenType.IF;
                    break;
                case "else":
                    type = TokenType.ELSE;
                    break;
                case "circulate":
                    type = TokenType.WHILE;
                    break;
                case "for":
                    type = TokenType.FOR;
                    break;
                case "funi":
                    type = TokenType.FUNCTION;
                    break;
                case "true":
                    type = TokenType.TRUE;
                    break;
                case "false":
                    type = TokenType.FALSE;
                    break;
                case "return":
                    type = TokenType.RETURNS;
                    break;
                case "switch":
                    type = TokenType.SWICH;
                    break;
                default:
                    type = TokenType.IDENTIFIER;
                    break;
            }
            return new Token(type, id, line, column - id.length());
        } else if (currentChar() == '"') {
            return handleString();
        } else {
            char ch = currentChar();
            TokenType type;
            switch (ch) {
                case '=':
                    return handleDoubleCharOperator(ch, TokenType.assignment, TokenType.EQUAL, "==");
                case '!':
                    return handleDoubleCharOperator(ch, null, TokenType.NOT_EQUAL, "!=");
                case '>':
                    return handleDoubleCharOperator(ch, TokenType.GREATER, TokenType.GREATER_EQUAL, ">=");
                case '<':
                    return handleDoubleCharOperator(ch, TokenType.LESS, TokenType.LESS_EQUAL, "<=");
                case '&':
                    if (peekNextChar() == '&') {
                        advance();
                        advance();
                        return new Token(TokenType.AND, "&&", line, column - 2);
                    }
                    break;
                case '|':
                    if (peekNextChar() == '|') {
                        advance();
                        advance();
                        return new Token(TokenType.OR, "||", line, column - 2);
                    }
                    break;
                case '+':
                    if (peekNextChar() == '+') {
                        advance();
                        advance();
                        return new Token(TokenType.INCREMENT, "++", line, column - 2);
                    } else if (peekNextChar() == '=') {
                        advance();
                        advance();
                        return new Token(TokenType.ADD_ASSIGN, "+=", line, column - 2);
                    } else {
                        advance();
                        return new Token(TokenType.add, "+", line, column - 1);
                    }
                case '-':
                    if (peekNextChar() == '-') {
                        advance();
                        advance();
                        return new Token(TokenType.DECREMENT, "--", line, column - 2);
                    } else if (peekNextChar() == '=') {
                        advance();
                        advance();
                        return new Token(TokenType.SUB_ASSIGN, "-=", line, column - 2);
                    } else {
                        advance();
                        return new Token(TokenType.remove, "-", line, column - 1);
                    }
                case '*':
                    advance();
                    return new Token(TokenType.multiply, "*", line, column - 1);
                case '/':
                    advance();
                    return new Token(TokenType.Dividing, "/", line, column - 1);
                case '%':
                    advance();
                    return new Token(TokenType.Remainder, "%", line, column - 1);
                case '(':
                    advance();
                    return new Token(TokenType.LPAREN, "(", line, column - 1);
                case ')':
                    advance();
                    return new Token(TokenType.RPAREN, ")", line, column - 1);
                case '{':
                    advance();
                    return new Token(TokenType.LBRACE, "{", line, column - 1);
                case '}':
                    advance();
                    return new Token(TokenType.RBRACE, "}", line, column - 1);
                case ';':
                    advance();
                    return new Token(TokenType.SEMICOLON, ";", line, column - 1);
                case ',':
                    advance();
                    return new Token(TokenType.COMMA, ",", line, column - 1);
                default:
                    throw new RuntimeException("Line " + line + " Col " + (column - 1) + ": 非法字符 '" + ch + "'");
            }
        }
        throw new RuntimeException("Unexpected state in lexer");
    }
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        Token token;
        while ((token = nextToken()).type != TokenType.End) {
            tokens.add(token);
        }
        tokens.add(token);
        return tokens;
    }
    private void skipMultiLineComment() {
        advance(); // 跳过 /* 中的 *
        while (currentChar() != '\0') {
            if (currentChar() == '*' && peekNextChar() == '/') {
                advance(); // 跳过 *
                advance(); // 跳过 /
                break;
            }
            if (currentChar() == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
            position++;
        }
    }
}