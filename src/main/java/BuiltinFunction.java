import java.util.List;

// 新增BuiltinFunction类
public class BuiltinFunction {
    private final java.util.function.Function<List<Object>, Object> func;

    public BuiltinFunction(java.util.function.Function<List<Object>, Object> func) {
        this.func = func;
    }

    public Object call(List<Object> args) {
        return func.apply(args);
    }
}