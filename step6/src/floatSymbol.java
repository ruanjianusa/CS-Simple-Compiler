/**
 * Created by jianruan on 9/20/15.
 */
public class floatSymbol extends Symbol {

    private float value;
    private boolean isPara;

    public floatSymbol(String name, Scope scope, boolean isPara)
    {
        super("FLOAT", name, scope);
        this.isPara =  isPara;
    }

    public floatSymbol(String name, String value, Scope scope) {
        super("FLOAT", name, scope);
        this.value = Float.valueOf(value);
    }

    public boolean isPara() {return isPara;}
    public float sym_getFloat() {
        return value;
    }
    public void sym_setValue(float f) { value = f;}

}
