import java.util.HashMap;

/**
 * Created by jianruan on 9/19/15.
 */
public class Symbol_Func extends Symbol {

    private Scope funcScope;
    private String returnType;
    private HashMap<String, funcVar> locals;
    int spaceNeeded;


    public Symbol_Func(String name, Scope parent) {
        super("FUNCTION", name, parent);
        funcScope = new Scope("FUNCTION", parent);
        locals = new HashMap<>();
    }

    private class funcVar {
        public Symbol symbol;
        public String type;
        public String label;
    }

    public String getFuncVarLabel(String var) {
        return locals.get(var).label;
    }
    public String getFuncVarType(String var) {
        return locals.get(var).type;
    }
    public int getNumOfLocals() {
        int counter = 0;
        for(funcVar var: locals.values()) {
            //System.out.println(var.type +" "+var.label);
            if(var.label.contains("L") && !var.type.contains("BLOCK")) {
                counter++;
            }
        }
        return counter;
    }
    public int getNumOfParas() {
        int counter = 0;
        for(funcVar var: locals.values()) {
            //System.out.println(var.type +" "+var.label);
            if(var.label.contains("P") && !var.type.contains("BLOCK")) {
                counter++;
            }
        }
        return counter;
    }

    public void localSymbolTable () {
        generalUtils.paraCounter = 1;
        generalUtils.localCounter = 1;

        for(Symbol s : funcScope.getSymbolList()) {
            String key = s.sym_getName();
            funcVar newVar = new funcVar();
            newVar.symbol = s;
            newVar.type = generalUtils.getVarType(key);
            if(getLocalOrPara(key)) {
                newVar.label = generalUtils.generateParaName();
            } else {
                newVar.label = generalUtils.generateLocalName();
            }
            locals.put(key, newVar);
        }
        String funcName = sym_getName();
        generalUtils.directoryLookup.put(funcName, locals);
    }

    public boolean isLocal (String var) {
        for (Symbol s :funcScope.getSymbolList()) {
            if (s.sym_getName().equals(var)) return true;
        }
        return false;
    }
    public String getLocalType(String var) {
        for (Symbol s :funcScope.getSymbolList()) {
            if (s.sym_getName().equals(var)) return s.sym_getType();
        }
        return "ERR";
    }
    public boolean getLocalOrPara(String var)
    {

        for(Symbol s :funcScope.getSymbolList()){
            if(s.sym_getName().equals(var)) {
                if(s instanceof Symbol_Int) {
                    return ((Symbol_Int)s).isPara();
                }
                if(s instanceof Symbol_Float) {
                    return ((Symbol_Float)s).isPara();
                }
            }
        }
        return false;
    }

    public Scope getOwnScope() {
        return funcScope;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String type) {
        returnType = type;
    }

}
