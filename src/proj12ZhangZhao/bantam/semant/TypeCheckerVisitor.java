/*
* TypeCheckerVisitor.java
* Editing Authors: Tia Zhang and Danqing Zhao
* Class: CS461
* Date: February 25, 2019
*/


package proj12ZhangZhao.bantam.semant;

import proj12ZhangZhao.bantam.util.*;
import proj12ZhangZhao.bantam.ast.*;
import proj12ZhangZhao.bantam.util.Error;
import proj12ZhangZhao.bantam.visitor.Visitor;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;


import com.sun.source.tree.ClassTree;
import proj12ZhangZhao.proj12.SemanticAnalyzer;

import java.util.Hashtable;


public class TypeCheckerVisitor extends Visitor {
    private ClassTreeNode currentClass;
    private SymbolTable currentSymbolTable;
    private ErrorHandler errorHandler;
    private String currentMethod;

    /**
     * @param type1 a string
     * @param type2 a string
     * @return boolean (if type2 is a subclass of type1)
     */
    private boolean isSubClass(String type1, String type2){
        if(type1.equals(type2)){
            return true;
        }
        Hashtable<String, ClassTreeNode> classMap = this.currentClass.getClassMap();
        ClassTreeNode classTree = classMap.get(type2);

        while(classTree.getParent()!=null){
            if(classTree.getParent().getName().equals(type1)){
                return true;
            }
            classTree = classTree.getParent();
        }

        return false;
    }

    /**
     * Checks if the given type exists (is declared in the file or is a built-in class)
     * @param objectName is a String indicating the name of the type it's checking for
     * @param lineNum is the line number containing the statement which has a type to be checked
     * @return the ClassTreeNode of the type if the class exists. Otherwise, return null
     * For arrays, since they do not have a class tree node, Object node's is returned
     */
    private ClassTreeNode checkTypeExistence(String objectName, int lineNum) {
        Hashtable<String, ClassTreeNode> classMap = currentClass.getClassMap();
        ClassTreeNode objectNode = classMap.get(objectName);
        if (objectNode == null) {
            String objectAsArray = objectName.substring(0, objectName.length() - 2);
            objectNode = classMap.get(objectAsArray);
            if (objectNode == null) {
                errorHandler.register(Error.Kind.SEMANT_ERROR,
                        currentClass.getASTNode().getFilename(), lineNum,
                        "The class " + objectName + " does not exist in this file");
            }
            else{ //If it's an array, use Object (because Dispatch needs to check Object methods for arrays)
                // TODO CHECK DISPATCH IS THE ONLY ONE THIS MATTERS FOR
                objectNode = classMap.get("Object");
            }
        }
        return objectNode;
    }
    /**
     * Visit a field node
     *
     * @param node the field node
     * @return null
     */

    public Object visit(Field node) {
        // The fields should have already been added to the symbol table by the
        // SemanticAnalyzer so the only thing to check is the compatibility of the init
        // expr's type with the field's type.
        String type = node.getType();
        Hashtable<String, ClassTreeNode> classMap = this.currentClass.getClassMap();
        if (!classMap.contains(type)) {
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "The declared type " + node.getType() + " of the field "
                            + node.getName() + " is undefined.");
        }
        Expr initExpr = node.getInit();

        if (initExpr != null) {
            initExpr.accept(this);

            if (!isSubClass(initExpr.getExprType(), type)) {
                //...the initExpr's type is not a subtype of the node's type...
                errorHandler.register(Error.Kind.SEMANT_ERROR,
                        currentClass.getASTNode().getFilename(), node.getLineNum(),
                        "The type of the initializer is " + initExpr.getExprType()
                                + " which is not compatible with the " + node.getName() +
                                " field's type " + node.getType());
            }
        }
        //Note: if there is no initExpr, then leave it to the Code Generator to
        //      initialize it to the default value since it is irrelevant to the
        //      SemanticAnalyzer.
        return null;
    }


/**
 * Visit a method node
 *
 * @param node the Method node to visit
 * @return null
 */

    public Object visit(Method node) {
        String type = node.getReturnType();
        Hashtable<String, ClassTreeNode> classMap = this.currentClass.getClassMap();
        if (!classMap.contains(type)) {
            //...the node's return type is not a defined type and not "void"...
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "The return type " + node.getReturnType() + " of the method "
                            + node.getName() + " is undefined.");
        }

        //create a new scope for the method body
        currentSymbolTable.enterScope();
        node.getFormalList().accept(this);
        node.getStmtList().accept(this);
        currentSymbolTable.exitScope();
        return null;
    }


/**
 * Visit a formal parameter node
 *
 * @param node the Formal node
 * @return null
 */

    public Object visit(Formal node) {
        String type = node.getType();
        Hashtable<String, ClassTreeNode> classMap = this.currentClass.getClassMap();
        if (!classMap.contains(type) && !type.equals("void")) {
            //...the node's type is not a defined type...
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "The declared type " + node.getType() + " of the formal" +
                            " parameter " + node.getName() + " is undefined.");
        }
        // add it to the current scope
        currentSymbolTable.add(node.getName(), node.getType());
        return null;
    }


/**
 * Visit a while statement node
 *
 * @param node the while statement node
 * @return null
 */

    public Object visit(WhileStmt node) {
        node.getPredExpr().accept(this);
        if(!node.getPredExpr().getExprType().equals("boolean")) {
            //...the predExpr's type is not "boolean"...
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "The type of the predicate is " + node.getPredExpr().getExprType()
                            + " which is not boolean.");
        }
        currentSymbolTable.enterScope();
        node.getBodyStmt().accept(this);
        currentSymbolTable.exitScope();
        return null;
    }


/**
 * Visit a block statement node
 *
 * @param node the block statement node
 * @return null
 */

    public Object visit(BlockStmt node) {
        currentSymbolTable.enterScope();
        node.getStmtList().accept(this);
        currentSymbolTable.exitScope();
        return null;
    }


/**
 * Visit a new expression node
 *
 * @param node the new expression node
 * @return null
 */

    public Object visit(NewExpr node) {
        String type = node.getType();
        Hashtable<String, ClassTreeNode> classMap = this.currentClass.getClassMap();
        if(!classMap.contains(type)) {
            //...the node's type is not a defined class type...
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "The type " + node.getType() + " does not exist.");
            node.setExprType("Object"); // to allow analysis to continue
        }
        else {
            node.setExprType(node.getType());
        }
        return null;
    }

    /**
     * visit a binary arithmetic divide expression node
     * @param node the binary arithmetic divide expression node
     * @return
     */

    public Object visit(BinaryArithDivideExpr node){
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        String type1 = node.getLeftExpr().getExprType();
        String type2 = node.getRightExpr().getExprType();
        if(!type1.equals("int")||!type2.equals("int")) {
            //...if neither type1 nor type2 is a subtype of the other...
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "can only divide between integers");
        }
        node.setExprType("int");
        return null;
    }

    /**
     * visit a binary arithmetic minus expression node
     * @param node the binary arithmetic minus expression node
     * @return
     */
    public Object visit(BinaryArithMinusExpr node){
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        String type1 = node.getLeftExpr().getExprType();
        String type2 = node.getRightExpr().getExprType();
        if(!type1.equals("int")||!type2.equals("int")) {
            //...if neither type1 nor type2 is a subtype of the other...
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "can only minus between integers");
        }
        node.setExprType("int");
        return null;
    }

    /**
     * visit a binary arithmetic modulus expression node
     * @param node the binary arithmetic modulus expression node
     * @return
     */
    public Object visit(BinaryArithModulusExpr node){
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        String type1 = node.getLeftExpr().getExprType();
        String type2 = node.getRightExpr().getExprType();
        if(!type1.equals("int")||!type2.equals("int")) {
            //...if neither type1 nor type2 is a subtype of the other...
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "can only modulus between integers");
        }
        node.setExprType("int");
        return null;
    }

    /**
     * visit a binary arithmetic plus expression node
     * @param node the binary arithmetic plus expression node
     * @return
     */
    public Object visit(BinaryArithPlusExpr node){
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        String type1 = node.getLeftExpr().getExprType();
        String type2 = node.getRightExpr().getExprType();
        if(!type1.equals("int")||!type2.equals("int")) {
            //...if neither type1 nor type2 is a subtype of the other...
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "can only add between integers");
        }
        node.setExprType("int");
        return null;
    }


    /**
     * visit a binary arithmetic times expression node
     * @param node the binary arithmetic times expression node
     * @return
     */
    public Object visit(BinaryArithTimesExpr node){
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        String type1 = node.getLeftExpr().getExprType();
        String type2 = node.getRightExpr().getExprType();
        if(!type1.equals("int")||!type2.equals("int")) {
            //...if neither type1 nor type2 is a subtype of the other...
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "can only time between integers");
        }
        node.setExprType("int");
        return null;
    }

    /**
     * Visit a binary comparison equals expression node
     *
     * @param node the binary comparison equals expression node
     * @return null
     */

    public Object visit(BinaryCompGeqExpr node) {
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        String type1 = node.getLeftExpr().getExprType();
        String type2 = node.getRightExpr().getExprType();

        if(!type1.equals("int")||!type2.equals("int")) {
            //...if neither type1 nor type2 is a subtype of the other...
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                currentClass.getASTNode().getFilename(), node.getLineNum(),
                "The two values being compared are not integers.");
        }
        node.setExprType("boolean");
        return null;
    }

    /**
     * Visit a binary comparison greater than expression node
     *
     * @param node the binary comparison greater than expression node
     * @return null
     */
    public Object visit(BinaryCompGtExpr node) {
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        String type1 = node.getLeftExpr().getExprType();
        String type2 = node.getRightExpr().getExprType();

        if(!(isSubClass(type1, type2)||isSubClass(type2, type1))) {
            //...if neither type1 nor type2 is a subtype of the other...
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "The two values being compared are not integers.");
        }
        node.setExprType("boolean");
        return null;
    }

    /**
     * Visit a binary comparison less than expression node
     *
     * @param node the binary comparison less than expression node
     * @return null
     */
    public Object visit(BinaryCompLtExpr node) {
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        String type1 = node.getLeftExpr().getExprType();
        String type2 = node.getRightExpr().getExprType();

        if(!type1.equals("int")||!type2.equals("int")) {
            //...if neither type1 nor type2 is a subtype of the other...
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "The two values being compared are not integers");
        }
        node.setExprType("boolean");
        return null;
    }

    /**
     * Visit a binary comparison equal to expression node
     *
     * @param node the binary comparison equal to expression node
     * @return null
     */
    public Object visit(BinaryCompEqExpr node) {
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        String type1 = node.getLeftExpr().getExprType();
        String type2 = node.getRightExpr().getExprType();

        if(!(isSubClass(type1, type2)||isSubClass(type2, type1))) {
            //...if neither type1 nor type2 is a subtype of the other...
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "The two values being compared for equality are not compatible types.");
        }
        node.setExprType("boolean");
        return null;
    }

    /**
     * Visit a binary comparison not equal expression node
     *
     * @param node the binary comparison not equal expression node
     * @return null
     */
    public Object visit(BinaryCompNeExpr node) {
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        String type1 = node.getLeftExpr().getExprType();
        String type2 = node.getRightExpr().getExprType();

        if(!(isSubClass(type1, type2)||isSubClass(type2, type1))) {
            //...if neither type1 nor type2 is a subtype of the other...
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "The two values being compared for equality are not compatible types.");
        }
        node.setExprType("boolean");
        return null;
    }


    /**
     * Visit a unary NOT expression node
     *
     * @param node the unary NOT expression node
     * @return null
     */

    public Object visit(UnaryNotExpr node) {
        node.getExpr().accept(this);
        String type = node.getExpr().getExprType();
        if(!type.equals("boolean")) {
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "The not (!) operator applies only to boolean expressions," +
                            " not " + type + " expressions.");
        }
        node.setExprType("boolean");
        return null;
    }

    /**
     * Visit an int constant expression node
     *
     * @param node the int constant expression node
     * @return null
     */

    public Object visit(ConstIntExpr node) {
        node.setExprType("int");
        return null;
    }


    /**
     * Visit a boolean constant expression node
     *
     * @param node the boolean constant expression node
     * @return null
     */

    public Object visit(ConstBooleanExpr node) {
        node.setExprType("boolean");
        return null;
    }


    /**
     * Visit a string constant expression node
     *
     * @param node the string constant expression node
     * @return null
     */

    public Object visit(ConstStringExpr node) {
        node.setExprType("String");
        return null;
    }









    /**
     * Visit a DeclStmt expression node
     *
     * @param node the DeclStmt expression node
     * @return null
     */

    public Object visit(DeclStmt node) {
        node.accept(this);
        String id = node.getName();
        Object existingDef = currentSymbolTable.lookup(id);
        if ((existingDef != null) && (currentSymbolTable.lookup(id, 0) == null)) {
            //If it's in the table and it's not a field
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "This variable name has already been defined in this scope");
        }
        if (SemanticAnalyzer.reservedIdentifiers.contains(id)){
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    id + " is a reserved word in Bantam Java and can't be used as an identifier");
        }

        Expr initExpr = node.getInit();
        if (initExpr == null) {
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "The variable has not been initialized");
        } else {
            String varType = initExpr.getExprType();
            ClassTreeNode varClassNode = checkTypeExistence(varType, node.getLineNum());
            if(varClassNode != null){
                currentSymbolTable.add(id, varType);//TODO FIGURE OUT IF NON EXISTENT TYPE SHOULD STILL BE SET IN SYMBOL TABLE
            }
        }
        return null;

    }



    /**
     * Visit a NewArrayExpr expression node
     *
     * @param node the NewArrayExpr expression node
     * @return null
     */

    public Object visit(NewArrayExpr node) {
        node.accept(this); //Do I only need to visit size?
        Expr size = node.getSize();
        if(size == null){ //I think this should be possible, if you put in a variable that has a value of null, for instance
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "Array requires a size when initialized");
        }
        else {
            String sizeType = size.getExprType();
            if (!"int".equals(sizeType)) {
                errorHandler.register(Error.Kind.SEMANT_ERROR,
                        currentClass.getASTNode().getFilename(), node.getLineNum(),
                        "The size expression for an array must be an integer," +
                                " not " + sizeType);
            }
        }
        String type = node.getType();
        type = type.substring(0, type.length()-2); //Cut off the brackets []
        Hashtable<String, ClassTreeNode> classMap = currentClass.getClassMap();
        ClassTreeNode arrayType = classMap.get(type);
        if(arrayType == null){
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "The declared type of the array cannot be found.");
        }

        node.setExprType(type); //Even if the type doesn't exist, let's pretend so I can get on with analysis

        return null;
    }




    /**
     * Visit a ReturnStmt expression node
     *
     * @param node the ReturnStmt expression node
     * @return null
     */

    public Object visit(ReturnStmt node) {
        node.getExpr().accept(this);
        String type = node.getExpr().getExprType();
        if(type != null) {
            checkTypeExistence(type, node.getLineNum());
        }
        else{
            type = "void";
        }

        Method method = (Method) currentClass.getMethodSymbolTable().lookup(currentMethod);
        String returnType = method.getReturnType();
        if(returnType == null){
            type = "void";
        }
        if(returnType!=null && !returnType.equals(type)){
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "The returned type of the method " + currentMethod + " does not equal the declared return type");
        }

        return null;
    }








    /**
     * Visit a unary DECR expression node
     *
     * @param node the unary DECR expression node
     * @return null
     */

    public Object visit(UnaryDecrExpr node) {
        node.getExpr().accept(this);
        Expr expr = node.getExpr();
        if(expr == null){
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "The decrement (--) operator is missing its expression.");
        }
        else {
            String type = expr.getExprType();
            if (!type.equals("int")) {
                errorHandler.register(Error.Kind.SEMANT_ERROR,
                        currentClass.getASTNode().getFilename(), node.getLineNum(),
                        "The decrement (--) operator applies only to integer expressions," +
                                " not " + type + " expressions.");
            }
        }
        node.setExprType("int");
        return null;
    }

    /**
     * Visit a unary INCR expression node
     *
     * @param node the unary INCR expression node
     * @return null
     */

    public Object visit(UnaryIncrExpr node) {
        node.getExpr().accept(this);
        Expr expr = node.getExpr();
        if(expr == null){
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "The increment (++) operator is missing its expression.");
        }
        else {
            String type = expr.getExprType();
            if (!type.equals("int")) {
                errorHandler.register(Error.Kind.SEMANT_ERROR,
                        currentClass.getASTNode().getFilename(), node.getLineNum(),
                        "The increment (++) operator applies only to integer expressions," +
                                " not " + type + " expressions.");
            }
        }
        node.setExprType("int");
        return null;
    }

    /**
     * Visit a unary NEG expression node
     *
     * @param node the unary NEG expression node
     * @return null
     */

    public Object visit(UnaryNegExpr node) {
        node.getExpr().accept(this);
        Expr expr = node.getExpr(); //In case the expression returns null
        if(expr == null){
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "The negative (-) operator is missing its expression.");
        }
        else {
            String type = expr.getExprType();
            if (!type.equals("int")) {
                errorHandler.register(Error.Kind.SEMANT_ERROR,
                        currentClass.getASTNode().getFilename(), node.getLineNum(),
                        "The negative (-) operator applies only to integer expressions," +
                                " not " + type + " expressions.");
            }
        }
        node.setExprType("int"); //Type needs to be set to int even if expression is missing
        return null;
    }








    /**
     * Visit a string constant expression node
     *
     * @param node the string constant expression node
     * @return null
     */

    public Object visit(VarExpr node) {
        node.accept(this);
        String varName = node.getName();
        //TODO double check on the usage of this and super
        String type = (String) currentSymbolTable.lookup(varName, currentSymbolTable.getCurrScopeLevel());
        if( (type == null)&& (!"super".equals(varName) && (!"this".equals(varName))) ){
            errorHandler.register(Error.Kind.SEMANT_ERROR,
                    currentClass.getASTNode().getFilename(), node.getLineNum(),
                    "The variable " + varName + " does not exist in this scope");

        }
        else {
            node.setExprType(type); //TODO how to handle it if the variable hasn't been defined - there is no type!
            // Leave it null? Put in the string "null" or non-existent?
        }
        return null;
    }

}

