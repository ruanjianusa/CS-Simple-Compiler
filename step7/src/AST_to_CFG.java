import java.util.*;

/**
 * Created by jianruan on 11/29/15.
 */
public class AST_to_CFG {
    //set to final to avoid any change
    //to preserve the order
    final private ArrayList<IRnode> nodes;
    private ArrayList<IRnode> leaderNodes;
    private HashMap<Integer, HashSet<Integer>> reverseAdjacency;
    private HashMap<Integer, HashSet<Integer>> adjacency;
    private HashSet<String> funcLabels;
    private HashMap<String, FuncInfo> funcMapping;
    int entryPoint = -1;
    int exitPoint = -1;

    /** For each function, find the begin and end
     *  add IN and OUT for inner nodes
     *  then take care of begin and end nodes
     *  in a case by case manner (main or regular func)
     * **/
    class FuncInfo {
        int funcBegin;
        HashSet<Integer> sources;
        HashSet<Integer> funcRet = new HashSet<>();
    }

    public AST_to_CFG (ArrayList<IRnode> nodes) {
        this.nodes = nodes;
        leaderNodes = new ArrayList<>();
        //simply treat every single statement as a block
        reverseAdjacency = new HashMap<>();
        adjacency = new HashMap<>();
        activateGeneration();
    }

    /***************************************** PUBLIC UTILITIES METHODS *******************************************/
    public HashMap<Integer, HashSet<Integer>> getGraph () {return adjacency;}
    public HashMap<Integer, HashSet<Integer>> getReverseGraph () {return reverseAdjacency;}
    public ArrayList<IRnode> getNodesList() {return nodes;}
    public HashSet<String> getAllFuncs() {return funcLabels;}
    public int getFuncEnter(String func) {return funcMapping.get(func).funcBegin;}
    public HashSet<Integer> getFuncExit(String func) {return funcMapping.get(func).funcRet;}
    /**************************************************************************************************************/

    /***************************************** CLASS UTILITIES METHODS ********************************************/
    private void activateGeneration () {
        generateLeaders();
        generateInOut();
        //addMergeNode();
        generateGraph();
    }
    private boolean isCmpNode(String op) {
        return op.contains("NE") || op.contains("EQ") || op.contains("GE") || op.contains("LE")
                || op.contains("GT") || (op.contains("LT") && !op.contains("MUL"));
    }
    private boolean isFuncEntryLabel(IRnode node) {
        boolean isLabel = node.opCode.contains("LABEL");
        boolean isFunc = false;
        if(node.result != null) {
            isFunc = !node.result.contains("label");
        }

        return isLabel&&isFunc;
    }
    private boolean isFuncRet(IRnode node) { return node.opCode.contains("RET");}
    private boolean isFuncCall(IRnode node) {return node.opCode.contains("JSR");}
    /**************************************************************************************************************/


    private void generateLeaders () {
        ArrayList<Integer> leaders = new ArrayList<>();
        IRnode previous;
        IRnode current = null;

        for (int i = 0; i < nodes.size(); i++)
        {
            previous = current;
            current = nodes.get(i);
            //case 1: if previous is null, the current statement is the first in program
            //case 2: identify explicit targets, the statements with "Label"
            //case 3: identify implicit targets, the first statements after conditional jump
            boolean cond0 = (previous == null);
            boolean cond1 = false;
            boolean cond2 = false;
            if (!cond0) {
                cond1 = current.opCode.contains("LABEL");
                cond2 = isCmpNode(previous.opCode);
            }
            if(cond0 || cond1 || cond2 ) { leaders.add(i);}
        }

        Collections.sort(leaders);
        for(Integer index : leaders)
        {
            IRnode node = nodes.get(index);
            node.isleader = true;
            leaderNodes.add(node);
        }

        /** For debug **/
//        System.out.println("\nLeaders :");
//        for(IRnode node : nodes) {
//            if(node.isleader) {
//                System.out.println(nodes.indexOf(node)+1+": "+node.opCode +" "+ node.operand1 +" " + node.result);
//            }
//        }
    }

    private void generateInOut() {

        /****************************************** Resources Data Structures *****************************************/
        /** find all labels */
        HashSet<String> labels = new HashSet<>();/** <--------------------------------------  Resources Data Structure*/
        for (IRnode node : nodes) {
            String op = node.opCode;
            if (op.equals("LABEL"))
                labels.add(node.result);
        }

        /** find occurrences of all labels */
        HashMap<String, HashSet<Integer>> labels2Indexes = new HashMap<>();/** <------------  Resources Data Structure*/
        //for each label find all locations of it
        for (String label : labels)
        {
            HashSet<Integer> indexes = new HashSet<>();
            for (IRnode node : nodes) {
                String current = node.result;
                if (current != null)
                {
                    if(current.equals(label))
                    {
                        int index = nodes.indexOf(node);
                        indexes.add(index);
                    }
                }
            }
            labels2Indexes.put(label, indexes);
        }

        /** There are two passes, their operations may overlap, but it doesn't hurt */
        funcLabels = new HashSet<>(); /** <--------------------------------  Resources Data Structure*/

        funcMapping = new HashMap<>();/** <----------------------  Resources Data Structure*/

        /**************************************************************************************************************/



        /** PASS 1: handle all the nodes involving labels **/
        //this pass takes care of function calls and explicit/implicit targets of conditional jump
        for (String label : labels)
        {
            HashSet<Integer> occurs = labels2Indexes.get(label);
            for (int occur : occurs)
            {
                IRnode node = nodes.get(occur);
                //"LABEL label_x" are destinations
                //"JUMP/JSR label_x" / "NE/LE xx xx xx label_x" are sources

                /** If occurrences are destinations */
                if (node.opCode.equals("LABEL"))
                { //-----> not covering Comparison Operation and JSR/JUMP
                    int des = nodes.indexOf(node);
                    HashSet<Integer> sources = (HashSet<Integer>)occurs.clone();
                    sources.remove(des);

                    /** If Destinations are functions */
                    //this part takes care of RET of functions
                    //by finding the first RET after the func label
                    //and add all nodes following the sources of the function to its {OUT} -->case1
                    //also need to add the RET to {IN} of those nodes-->case2
                    if(!label.contains("label"))//--> if function
                    {
                        /*************************************************************************************/
                        /***/if(label.equals("main")) { entryPoint = des;} /**     ->store entryPoint
                        /*************************************************************************************/


                        /**step1:add to function labels set*/
                        funcLabels.add(label);
                        /**step2:initiate a mapping for it*/
                        FuncInfo thisFunc = new FuncInfo();
                        funcMapping.put(label, thisFunc);
                        /**step3: get the beginning of a function*/
                        thisFunc.funcBegin = nodes.indexOf(node);
                        /**step4: add sources and begin of this functions*/
                        thisFunc.sources = sources;
                        /**step5: find the boundaries of this func and all of its RET*/

                        int funcStart = thisFunc.funcBegin + 1;
                        while(true) {
                            IRnode needle = nodes.get(funcStart);
                            //corner case: at the end end of nodes, there's no another func label
                            if( funcStart == nodes.size() - 1 ) {
                                thisFunc.funcRet.add(funcStart);
                                break;
                            }
                            if(isFuncEntryLabel(needle)){
                                break;
                            }
                            if(isFuncRet(needle)) {
                                thisFunc.funcRet.add(nodes.indexOf(needle));
                            }
                            funcStart++;
                        }

                    }

                    /** NOTICE: if the label represents a function,
                     *          then all of its info is built till this point**/

                    /** For any destination node in general (INCLUDING functions)
                     * 1. add source nodes to the {IN} of this label node
                     * 2. add this label node to the {OUT} of all source nodes
                     * 3. they are always a pair
                     * */
                    IRnode des_node = nodes.get(des);
                    for (int source : sources) {
                        //case like JSR JUMP are also taken care here
                        //add "sources" into {IN} of des node
                        if(!nodes.get(source).opCode.contains("JSR")) {
                            /** 1 */
                            des_node.addToIN(source);
                            /** this line handles part of the sources OUTs
                             * all the source need add des as one of its {OUT}   <------------------------------
                             * but it's only OK for unconditional jump                                         |
                             * situations when source is conditional jumps handles below in "else" part        |
                             * */
                            /** 2 */
                            //add "des" into {OUT} of every source node
                            IRnode source_node = nodes.get(source);
                            source_node.addToOUT(des); //---> explicit targets
                        } else {
                            //if it is JSR
                            //make it a straight line through
                            IRnode JSRnode = nodes.get(source);
                            JSRnode.addToOUT(source+1);
                            nodes.get(source+1).addToIN(source);
                        }

                    }
                }
                else
                {
                    /**                                                                                    |
                     * If occurrences are sources and conditional jumps *  <--------------------------------
                     */
                    if(isCmpNode(node.opCode)) {
                        //add follow node into {OUT} of cmp node
                        int indexOfCmp = nodes.indexOf(node);
                        node.addToOUT(indexOfCmp+1);//the following node of cmp node is implicit targets
                        //add cmp node into {IN} of follow node
                        IRnode implicit_node = nodes.get(indexOfCmp+1);
                        implicit_node.addToIN(indexOfCmp);
                    }

                }

            }
        }

        /** Validation: Validate entry point, aka "main"
         *  and Find the Exit point of the program */
        if(entryPoint == -1) {
            System.out.println("main function is not set");
            System.exit(-1);
        } else {
            String entryFuncName = nodes.get(entryPoint).result;
            HashSet<Integer> Rets = funcMapping.get(entryFuncName).funcRet;
            if(Rets.size() == 1)
                for(int ret : Rets) exitPoint = ret;
            else {
                System.out.println("main function's RET is not set properly");
                System.exit(-1);
            }
        }


        /** For Debug */
//        System.out.println();
//        System.out.println("Program entry point is at line: " + (entryPoint+1) );
//        for (String func : funcLabels) {
//            FuncInfo thisFunc = funcMapping.get(func);
//            System.out.print("Function "+func+": Begins at "+(thisFunc.funcBegin+1)+" and RET(s) at ");
//            for(Integer index : thisFunc.funcRet) {
//                System.out.print((index+1)+" ");
//            }
//            System.out.print("; And it's called from: ");
//            for(Integer index : thisFunc.sources) {
//                System.out.print((index+1)+" ");
//            }
//            System.out.print("\n");
//        }


        /** PASS 2: handle all other cases in general **/
        /** So far,
         *  1. {IN} for Labels are taken cared
         *  2. {OUT} for other JUMPs are taken cared
         *  3. Entry point for the program is Found
         *  4. Info of All Functions
         * */

        /** functions and funcMapping is built at this Point
         * TODO:
         *  1. take care IN and OUT for Enter & Exit of Functions
         *  2. take care IN and OUT for regular nodes in between basic blocks
         * */


        /** connect nodes in between Leaders */
        for (IRnode leader : leaderNodes)
        {
            int needle =  nodes.indexOf(leader);
            while(true) {
                if(needle == nodes.size() - 1) break;

                IRnode node1 = nodes.get(needle);
                IRnode node2 = nodes.get(needle + 1);

                //if the first node is RET
                if(isFuncRet(node1)) break;

                //when node1 is JSR xxx, it's unconditional jump
                //don't add
                if(!isFuncCall(node1)) {
                    node1.addToOUT(needle + 1);
                    node2.addToIN(needle);
                }

                needle++;
            }
        }


    }

    private void addMergeNode() {
        //find all the Nodes with IN's size > 1
        int size = nodes.size();
        int i = 0;
        while(i < size)
        {
            IRnode node = nodes.get(i);

            if(node.getINsize() > 1)
            {
                int indexOfThisNode = nodes.indexOf(node);
                //step1 create merge node
                HashSet<Integer> inForMergeNode = (HashSet<Integer>)node.getIN().clone();
                int outForMergeNode = indexOfThisNode;
                IRnode mergeNode = new IRnode(inForMergeNode, outForMergeNode);
                //step2 insert mergeNode in nodes list
                nodes.add(mergeNode);
                //step3 link outgoing neighbours correctly
                if(node.clearIN())
                    node.addToIN(nodes.indexOf(mergeNode));
                else {
                    System.out.println("IN not cleared;");
                    System.exit(-1);
                }
                mergeNode.addToOUT(indexOfThisNode);
                //step4 link incoming neighbours correctly
                HashSet<Integer> inComings = mergeNode.getIN();
                for(int incoming : inComings)
                {
                    IRnode inNode = nodes.get(incoming);
                    if(inNode.removeOUT(indexOfThisNode))
                        inNode.addToOUT(nodes.indexOf(mergeNode));
                    else {
                        System.out.println("OUT not cleared;");
                        System.exit(-1);
                    }
                }
            }

            i++;
        }
    }

    private void generateGraph() {
        int index = 0;
        for(IRnode node : nodes) {
            // 1
            HashSet<Integer> ins = node.getIN();
            reverseAdjacency.put(index, ins);
            // 2
            HashSet<Integer> outs = node.getOUT();
            adjacency.put(index, outs);
            index++;
        }

        /**************************** For Debug ******************************/
//        System.out.print("\n");
//        for(int i : adjacency.keySet()) {
//            HashSet<Integer> from = reverseAdjacency.get(i);
//            if(from.isEmpty()) {System.out.print(" NULL");}
//            for(int j : from) {System.out.print(" "+(j+1));}
//
//            System.out.print(" into [Node "+(i+1)+"] links to");
//
//            HashSet<Integer> links = adjacency.get(i);
//            if(links.isEmpty()) {System.out.print(" NULL");}
//            for(int j : links) {System.out.print(" "+(j+1));}
//            System.out.print("\n");
//        }
        /**********************************************************************/
    }

}
