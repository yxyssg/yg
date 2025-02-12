public class Token {
    TokenType type;
    String value;
    int line;
    int column;

    public Token(TokenType type, String value, int line, int column) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.column = column;
    }


    @Override
    public String toString() {
        return "Token{"+"Type="+type+"vault="+ value +"}";
    }
}
