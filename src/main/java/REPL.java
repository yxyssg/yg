import java.util.Scanner;

// 新增REPL类
public class REPL {
    public static void main(String[] args) {
        Interpreter interpreter = new Interpreter();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print(">> ");
            String code = scanner.nextLine();
            if (code.equals("exit")) break;

            try {
                Lexer lexer = new Lexer(code);
                Parser parser = new Parser(lexer.tokenize());
                Object result = interpreter.interpret(parser.parse());
                if (result != null) System.out.println(result);
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }
}