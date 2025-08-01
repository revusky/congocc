package org.congocc.codegen.csharp;

import java.util.*;

import org.congocc.core.Grammar;
import org.congocc.codegen.Translator;
import org.congocc.codegen.java.CodeInjector;
import org.congocc.parser.tree.*;

public class CSharpTranslator extends Translator {
    public CSharpTranslator(Grammar grammar) {
        super(grammar);
        methodIndent = 8;
        fieldIndent = 8;
        isTyped = true;
    }

    public String translateOperator(String operator) {
        return operator;
    }

    private static final Set<String> specialPrefixes = new HashSet<>();

    private static boolean isSpecialPrefix(String ident) {
        boolean result = false;

        for (String p : specialPrefixes) {
            if (ident.startsWith(p)) {
                result = true;
                break;
            }
        }
        return result;
    }

    private static final Set<String> propertyIdentifiers = makeSet("image", "lastConsumedToken", "lexicalState");

    @Override public String translateIdentifier(String ident, TranslationContext kind) {
        // TODO proper method name translation
        if (kind == TranslationContext.TYPE) {
            return translateTypeName(ident);
        }
        String result = ident;

        if (specialPrefixes.isEmpty()) {
            specialPrefixes.add(grammar.getAppSettings().generateIdentifierPrefix("tokenHook"));
        }
        if (ident.equals("toString")) {
            result = "ToString";
        }
        else if (ident.equals("nodeArity")) {
            result = "NodeArity";
        }
        else if (ident.equals("currentNodeScope")) {
            result = "CurrentNodeScope";
        }
        else if (ident.equals("addAll")) {
            result = "AddRange";
        }
        else if (ident.equals("preInsert")) {
            result = "PreInsert";
        }
        else if (ident.equals("size")) {
            result = "Count";
        }
        else if (ident.equals("String")) {
            result = "string";
        }
        else if (ident.equals("isUnparsed")) {
            result = "IsUnparsed";
        }
        else if (ident.equals("token_source")) {
            result = "tokenSource";
        }
        else if (ident.equals("LEXER_CLASS") || ident.equals(appSettings.getLexerClassName())) {
            result = "Lexer";
        }
        else if (ident.equals("PARSER_CLASS") || ident.equals(appSettings.getParserClassName())) {
            result = "Parser";
        }
        else if (ident.equals("THIS_PRODUCTION")) {
            result = "thisProduction";
        }
        else if (ident.equals("BASE_TOKEN_CLASS") || ident.equals(appSettings.getBaseTokenClassName())) {
            result = "Token";
        }
        else if (ident.startsWith("NODE_PACKAGE.")) {
            result = ident.substring(13);
        }
        else if (ident.startsWith(appSettings.getNodePackage().concat("."))) {
            int prefixLength = appSettings.getNodePackage().length() + 1;
            result = ident.substring(prefixLength);
        }
        else if ((kind != TranslationContext.VARIABLE || propertyIdentifiers.contains(ident)) && kind != TranslationContext.PARAMETER && Character.isLowerCase(ident.charAt(0)) && !isSpecialPrefix(ident)) {
            result = Character.toUpperCase(ident.charAt(0)) + ident.substring(1);
        }
        return result;
    }

    public String translateGetter(String getterName) {
        if (getterName.startsWith("is")) {
            return translateIdentifier(getterName, TranslationContext.METHOD);
        }
        String result = Character.toLowerCase(getterName.charAt(3)) +
                getterName.substring(4);
        return translateIdentifier(result, TranslationContext.METHOD);
    }

    @Override protected void translatePrimaryExpression(ASTPrimaryExpression expr, TranslationContext ctx, StringBuilder result) {
        String s = expr.getLiteral();
        String n = expr.getName();
        boolean isName = false;

        if (s == null) {
            s = translateIdentifier(n, TranslationContext.VARIABLE);
            isName = true;
        }
        if (isName && fields.containsKey(n)) {  // must be a field, then
            if (properties.containsKey(n)) {
                result.append('_');
            }
        }
        if ((ctx == TranslationContext.PARAMETER) && (expr instanceof ASTTypeExpression)) {
            result.append("typeof(");
            result.append(s);
            result.append(')');
        }
        else {
            result.append(s);
        }
    }

    @Override protected void translateUnaryExpression(ASTUnaryExpression expr, TranslationContext ctx, StringBuilder result) {
        String xop = translateOperator(expr.getOp());
        boolean parens = needsParentheses(expr);

        if (xop.equals("++") || xop.equals("--")) {
            internalTranslateExpression(expr.getOperand(), ctx, result);
            result.append(' ');
            result.append(xop.charAt(0));
            result.append("= 1");
        }
        else {
            if (parens) {
                result.append('(');
            }
            result.append(xop);
            internalTranslateExpression(expr.getOperand(), ctx, result);
            if (parens) {
                result.append(')');
            }
        }
    }

    @Override protected void translateBinaryExpression(ASTBinaryExpression expr, StringBuilder result) {
        String xop = translateOperator(expr.getOp());
        boolean parens = needsParentheses(expr);
        ASTExpression lhs = expr.getLhs();
        ASTExpression rhs = expr.getRhs();

        processBinaryExpression(parens, lhs, xop, rhs, result);
    }

    @Override protected void translateInstanceofExpression(ASTInstanceofExpression expr, StringBuilder result) {
        boolean parens = expr.getParent() != null;

        if (parens) {
            result.append('(');
        }
        internalTranslateExpression(expr.getInstance(), TranslationContext.UNKNOWN, result);
        result.append(" is ");
        internalTranslateExpression(expr.getTypeExpression(), TranslationContext.UNKNOWN, result);
        if (parens) {
            result.append(')');
        }
    }

    @SuppressWarnings("DuplicatedCode")
    @Override protected void translateTernaryExpression(ASTTernaryExpression expr, StringBuilder result) {
        boolean parens = needsParentheses(expr);
        ASTExpression condition = expr.getCondition();
        ASTExpression trueValue = expr.getTrueValue();
        ASTExpression falseValue = expr.getFalseValue();

        if (parens) {
            result.append('(');
        }
        internalTranslateExpression(condition, TranslationContext.UNKNOWN, result);
        result.append(" ? ");
        internalTranslateExpression(trueValue, TranslationContext.UNKNOWN, result);
        result.append(" : ");
        internalTranslateExpression(falseValue, TranslationContext.UNKNOWN, result);
        if (parens) {
            result.append(')');
        }
    }

    boolean renderReceiver(ASTExpression expr, StringBuilder result) {
        boolean rendered;
        if (expr instanceof ASTBinaryExpression) {
            internalTranslateExpression(((ASTBinaryExpression) expr).getLhs(), TranslationContext.UNKNOWN, result);
            rendered = true;
        }
        else if (expr instanceof ASTPrimaryExpression) {
            // Do nothing
            rendered = false;
        }
        else {
            String s = String.format("Cannot render receiver %s", getSimpleName(expr));
            throw new UnsupportedOperationException(s);
        }
        return rendered;
    }

    protected void translateArguments(List<ASTExpression> arguments, StringBuilder result) {
        int nargs;

        if ((arguments == null) || ((nargs = arguments.size()) == 0)) {
            result.append("()");
        }
        else {
            result.append('(');
            for (int i = 0; i < nargs; i++) {
                internalTranslateExpression(arguments.get(i), TranslationContext.PARAMETER, result);
                if (i < (nargs - 1))
                    result.append(", ");
            }
            result.append(')');
        }
    }

    private static final Set<String> propertyNames = makeSet("getImage", "getType", "getBeginLine", "getBeginColumn",
            "getEndLine", "getEndColumn", "getBeginOffset", "getEndOffset", "getLocation", "getTokenSource",
            "getPreviousToken", "getLastChild");

    @Override protected void translateInvocation(ASTInvocation expr, StringBuilder result) {
        String methodName = expr.getMethodName();
        int nargs = expr.getArgCount();
        ASTExpression receiver = expr.getReceiver();
        boolean treatAsProperty = propertyNames.contains(methodName);
        ASTExpression firstArg = (nargs != 1) ? null : expr.getArguments().get(0);
        boolean needsGeneric = methodName.equals("firstChildOfType") || methodName.equals("childrenOfType") ||
                               methodName.equals("descendantsOfType") || methodName.equals("descendants") ||
                               methodName.equals("hasChildOfType") || methodName.equals("hasDescendantOfType");

        needsGeneric = needsGeneric && (firstArg instanceof ASTPrimaryExpression);

        if (methodName.equals("size") && (nargs == 0)) {
            if (renderReceiver(receiver, result)) {
                result.append('.');
            }
            result.append("Count");
        }
        else if (methodName.equals("length") && (nargs == 0)) {
            if (renderReceiver(receiver, result)) {
                result.append('.');
            }
            result.append("Length");
        }
        else if (methodName.equals("children") && (nargs == 0)) {
            if (renderReceiver(receiver, result)) {
                result.append('.');
            }
            result.append("Children");
        }
        else if ((methodName.equals("charAt") || methodName.equals("codePointAt")) && (nargs == 1)) {
            renderReceiver(receiver, result);
            result.append('[');
            internalTranslateExpression(firstArg, TranslationContext.UNKNOWN, result);
            result.append(']');
        }
        else if (methodName.equals("isParserTolerant") && (nargs == 0)) {
            int n = result.length();
            renderReceiver(receiver, result);
            if (n < result.length()) {
                result.append('.');
            }
            result.append("IsTolerant");
        }
        else if (methodName.equals("previousCachedToken") && (nargs == 0)) {
            int n = result.length();
            renderReceiver(receiver, result);
            if (n < result.length()) {
                result.append('.');
            }
            result.append("PreviousCachedToken");
        }
        else if (methodName.equals("get") && (nargs == 1) && !(receiver instanceof ASTPrimaryExpression)) {
            // get(X) -> Get(x), but a.get(x) -> a[x]
            renderReceiver(receiver, result);
            result.append('[');
            internalTranslateExpression(firstArg, TranslationContext.UNKNOWN, result);
            result.append(']');
        }
        else if (treatAsProperty && isGetter(methodName) && (nargs == 0)) {
            // treat as a property
            int n = result.length();
            renderReceiver(receiver, result);
            if (n < result.length()) {
                result.append('.');
            }
            result.append(translateGetter(methodName));
        }
        else if (methodName.equals("nodeArity") && (nargs == 0)) {
            renderReceiver(receiver, result);
            result.append(".NodeArity");
        }
        else if (methodName.equals("isUnparsed") && (nargs == 0)) {
            renderReceiver(receiver, result);
            result.append(".IsUnparsed");
        }
        else if (methodName.equals("getSimpleName") && (nargs == 0) && belongsToClass(expr)) {
            renderReceiver(receiver, result);
            result.append(".Name");
        }
        else if (methodName.equals("setUnparsed") && (nargs == 1)) {
            renderReceiver(receiver, result);
            result.append(".IsUnparsed = ");
            internalTranslateExpression(firstArg, TranslationContext.UNKNOWN, result);
        }
        else if (methodName.equals("of") && isEnumSet(receiver)) {
            result.append("Utils.EnumSet(");
            if (nargs > 0) {
                translateArguments(expr.getArguments(), false, result);
            }
            result.append(")");
        }
        else if (isSetter(methodName) && (nargs == 1)) {
            String s = translateIdentifier(methodName, TranslationContext.METHOD);
            renderReceiver(receiver, result);
            result.append('.');
            result.append(s.substring(3));
            result.append(" = ");
            internalTranslateExpression(firstArg, TranslationContext.UNKNOWN, result);
        }
        else if (expr instanceof ASTAllocation) {
            if (isList(receiver)) {
                result.append("new ListAdapter<");
                List<ASTTypeExpression> tps = ((ASTTypeExpression) receiver).getTypeParameters();
                if (tps != null) {
                    translateType(tps.get(0), result);
                }
                result.append(">");
            }
            else if (isSet(receiver)) {
                result.append("new HashSet<");
                List<ASTTypeExpression> tps = ((ASTTypeExpression) receiver).getTypeParameters();
                if (tps != null) {
                    translateType(tps.get(0), result);
                }
                result.append(">");
            }
            else if (isMap(receiver)) {
                result.append("new MapAdapter<");
                List<ASTTypeExpression> tps = ((ASTTypeExpression) receiver).getTypeParameters();
                if (tps != null) {
                    translateType(tps.get(0), result);
                    result.append(", ");
                    translateType(tps.get(1), result);
                }
                result.append(">");
            }
            else {
                result.append("new ");
                internalTranslateExpression(receiver, TranslationContext.UNKNOWN, result);
            }
            translateArguments(expr.getArguments(), result);
        }
        else {
            if (!methodName.equals("newToken")) {
                int n = result.length();
                renderReceiver(receiver, result);
                if (n < result.length()) {
                    result.append('.');
                }
            }
            else {
                result.append("Token.");  // FIXME hardcoding
            }
            if (methodName.equals("getClass")) {
                methodName = "GetType";
            }
            String ident = translateIdentifier(methodName, TranslationContext.METHOD);
            if (ident.equals("DirectiveLine")) {
                // FIXME hard-coding for parser method names
                ident = "Parse" + ident;
            }
            result.append(ident);
            if (needsGeneric) {
                result.append('<');
                result.append(((ASTPrimaryExpression) firstArg).getName());
                result.append('>');
            }
            translateArguments(expr.getArguments(), result);
        }
    }

    @Override public String translateTypeName(String name) {
        return switch (name) {
            case "List", "java.util.List" -> "ListAdapter";
            case "Map", "java.util.Map", "java.util.HashMap" -> "MapAdapter";
            case "Set", "HashSet", "EnumSet" -> "HashSet";
            case "Iterator", "java.util.Iterator" -> "Iterator";
            case "boolean" -> "bool";
            case "Integer" -> "int";
            case "BASE_TOKEN_CLASS" -> "Token";
            case "LEXER_CLASS" -> "Lexer";
            case "PARSER_CLASS" -> "Parser";
            default -> {
                if (name.equals(appSettings.getLexerClassName()))
                    yield "Lexer";
                else if (name.equals(appSettings.getParserClassName()))
                    yield "Parser";
                else if (name.equals(appSettings.getBaseTokenClassName()))
                    yield "Token";
                else if (name.startsWith(appSettings.getNodePackage().concat("."))) {
                    int prefixLength = appSettings.getNodePackage().length() + 1;
                    yield name.substring(prefixLength);
                }
                yield name;
            }
        };
    }

    @Override protected void translateType(ASTTypeExpression expr, StringBuilder result) {
        String s = expr.getName();

        if (s == null) {
            s = expr.getLiteral();
        }
        String tn = translateTypeName(s);
        result.append(tn);
        List<ASTTypeExpression> tp = expr.getTypeParameters();
        if (tp != null) {
            result.append('<');
            int n = tp.size();
            for (int i = 0; i < n; i++) {
                translateType(tp.get(i), result);
                if (i < (n - 1)) {
                    result.append(", ");
                }
            }
            result.append('>');
        }
    }

    protected static final HashSet<String> accessModifiers = new LinkedHashSet<>(Arrays.asList("public", "protected", "private"));

    protected void translateModifiers(List<String> modifiers, StringBuilder result) {
        Set<String> mods = new LinkedHashSet<>(modifiers);
        List<String> translated_mods = new ArrayList<>();
        boolean accessModifierAdded = false;

        mods.remove("default");
        mods.remove("final");
        for (String s : accessModifiers) {
            if (mods.contains(s)) {
                mods.remove(s);
                translated_mods.add(s);
                accessModifierAdded = true;
            }
        }
        if (!accessModifierAdded && !inInterface) {
            translated_mods.add("internal");
        }
        if (mods.contains("static")) {
            translated_mods.add("static");
            mods.remove("static");
        }
        if (mods.size() > 0) {
            String s = String.format("Unable to translate modifier %s", String.join(", ", mods));
            throw new UnsupportedOperationException(s);
        }
        for (String mod: translated_mods) {
            result.append(mod);
            result.append(' ');
        }
    }

    protected boolean isNullable(ASTTypeExpression expr) {
/*
        boolean result = true;
        String literal = expr.getLiteral();

        if (literal != null) {
            if (literal.equals("boolean")) {
                result = false;
            }
        }
 */
        return false;
    }

    protected void closeBrace(int indent, StringBuilder result) {
        addIndent(indent, result);
        result.append("}\n");
    }

    @Override protected void internalTranslateStatement(ASTStatement stmt, int indent, StringBuilder result) {
        boolean addNewline = false;
        if (!(stmt instanceof ASTStatementList)) {  // it adds its own indents
            addIndent(indent, result);
        }
        if (stmt instanceof ASTExpressionStatement) {
            if (stmt instanceof ASTThrowStatement) {
                result.append("throw ");
            }
            internalTranslateExpression(((ASTExpressionStatement) stmt).getValue(), TranslationContext.UNKNOWN, result);
            result.append(';');
            addNewline = true;
        }
        else if (stmt instanceof ASTStatementList asl) {
            boolean isInitializer =asl.isInitializer();
            List<ASTStatement> statements = asl.getStatements();

            if (isInitializer) {
                addIndent(indent, result);
                result.append("{\n");
                indent += 4;
            }
            if (statements != null) {
                for (ASTStatement s : statements) {
                    internalTranslateStatement(s, indent, result);
                }
            }
            if (isInitializer) {
                indent -= 4;
                closeBrace(indent, result);
            }
        }
        else if (stmt instanceof ASTVariableOrFieldDeclaration vd) {
            List<ASTPrimaryExpression> names = vd.getNames();
            List<ASTExpression> initializers = vd.getInitializers();
            ASTTypeExpression type = vd.getTypeExpression();
            int n = names.size();
            boolean isProperty = vd.hasAnnotation("Property");
            boolean isField = vd.isField();
            List<String> modifiers = vd.getModifiers();

            if (modifiers == null) {
                if (isField) {
                    result.append("internal ");  // default access modifier
                }
            }
            else {
                translateModifiers(modifiers, result);
            }
            translateType(type, result);
            if (isNullable(type)) {
                result.append('?');
            }
            result.append(' ');
            for (int i = 0; i < n; i++) {
                ASTPrimaryExpression name = names.get(i);
                ASTExpression initializer = initializers.get(i);

                processVariableDeclaration(type, name, isField, isProperty);
                TranslationContext ctx = isField ? TranslationContext.FIELD : TranslationContext.VARIABLE;
                internalTranslateExpression(name, ctx, result);
                if (initializer != null) {
                    result.append(" = ");
                    internalTranslateExpression(initializer, TranslationContext.UNKNOWN, result);
                }
                if (i < (n - 1)) {
                    result.append(", ");
                }
                addNewline = true;
            }
            result.append(';');
        }
        else if (stmt instanceof ASTReturnStatement ars) {
            result.append("return");
            ASTExpression value = ars.getValue();
            if (value != null) {
                result.append(' ');
                internalTranslateExpression(value, TranslationContext.UNKNOWN, result);
            }
            result.append(';');
            addNewline = true;
        }
        else if (stmt instanceof ASTIfStatement s) {
            result.append("if (");
            internalTranslateExpression(s.getCondition(), TranslationContext.UNKNOWN, result);
            result.append(") {\n");
            internalTranslateStatement(s.getThenStmts(), indent + 4, result);
            if (s.getElseStmts() != null) {
                closeBrace(indent, result);
                addIndent(indent, result);
                result.append("else {\n");
                internalTranslateStatement(s.getElseStmts(), indent + 4, result);
            }
            closeBrace(indent, result);
        }
        else if (stmt instanceof ASTWhileStatement s) {
            result.append("while (");
            internalTranslateExpression(s.getCondition(), TranslationContext.UNKNOWN, result);
            result.append(") {\n");
            internalTranslateStatement(s.getStatements(), indent + 4, result);
            closeBrace(indent, result);
        }
        else if (stmt instanceof ASTForStatement s) {
            ASTExpression iterable;
            ASTVariableOrFieldDeclaration decl = s.getVariable();

            if ((iterable = s.getIterable()) != null) {
                // iterating for
                ASTVariableOrFieldDeclaration vd = s.getVariable();
                result.append("foreach (var ");
                internalTranslateExpression(vd.getNames().get(0), TranslationContext.UNKNOWN, result);
                result.append(" in ");
                internalTranslateExpression(iterable, TranslationContext.UNKNOWN, result);
                result.append(") {\n");
                internalTranslateStatement(s.getStatements(), indent + 4, result);
            }
            else {
                // counting for
                List<ASTPrimaryExpression> names = decl.getNames();
                List<ASTExpression> initializers = decl.getInitializers();
                int n = names.size();
                for (int i = 0; i < n; i++) {
                    ASTExpression name = names.get(i);
                    ASTExpression initializer = initializers.get(i);
                    if (initializer == null) {
                        String msg = String.format("Unexpected null initializer for %s", getSimpleName(name));
                        throw new UnsupportedOperationException(msg);
                    }
                    else {
                        translateType(decl.getTypeExpression(), result);
                        result.append(' ');
                        internalTranslateExpression(name, TranslationContext.UNKNOWN, result);
                        result.append(" = ");
                        internalTranslateExpression(initializer, TranslationContext.UNKNOWN, result);
                        if (i < (n - 1)) {
                            result.append("; ");
                        }
                    }
                }
                result.append(";\n");
                addIndent(indent, result);
                result.append("while (");
                internalTranslateExpression(s.getCondition(), TranslationContext.UNKNOWN, result);
                result.append(") {\n");
                internalTranslateStatement(s.getStatements(), indent + 4, result);
                List<ASTExpression> iteration = s.getIteration();
                if (iteration != null) {
                    processForIteration(iteration, indent + 4, result);
                    result.append(";\n");
                }
            }
            closeBrace(indent, result);
        }
        else if (stmt instanceof ASTSwitchStatement s) {
            String tv = getTempVarName();
            ASTExpression expr = s.getVariable();
            boolean isTT = isTokenType(expr);
            result.append("var ");
            result.append(tv);
            result.append(" = ");
            internalTranslateExpression(expr, TranslationContext.UNKNOWN, result);
            result.append(";\n");

            addIndent(indent, result);
            result.append("switch (");
            result.append(tv);
            result.append(") {\n");
            for (ASTCaseStatement c : s.getCases()) {
                List<ASTExpression> labels = c.getCaseLabels();
                int lc = labels.size();
                if (lc == 0) {
                    addIndent(indent, result);
                    result.append("default:\n");
                }
                else {
                    for (ASTExpression label : labels) {
                        addIndent(indent, result);
                        result.append("case ");
                        if (isTT) {
                            result.append("TokenType.");
                        }
                        internalTranslateExpression(label, TranslationContext.UNKNOWN, result);
                        result.append(":\n");
                    }
                }
                internalTranslateStatement(c.getStatements(), indent + 4, result);
                if (!hasUnconditionalExit(c.getStatements())) {
                    addIndent(indent + 4, result);
                    result.append("break;\n");
                }
            }
            closeBrace(indent, result);
        }
        else if (stmt instanceof ASTMethodDeclaration decl) {
            String methodName = translateIdentifier(decl.getName(), TranslationContext.METHOD);
            List<ASTFormalParameter> formals = decl.getParameters();
            SymbolTable symbols = new SymbolTable();
            List<String> modifiers = decl.getModifiers();
            boolean isOverride = methodName.equals("Equals") || methodName.equals("ToString"); // TODO generalise

            pushSymbols(symbols);
            if (modifiers == null) {
                result.append("internal ");  // default access modifier
            }
            else {
                if (methodName.equals("GetIndents") || methodName.equals("IsVirtual")) { // TODO generalise
                    if ("Token".equals(currentClass)) {
                        result.append("virtual ");
                    }
                    else {
                        result.append("override ");
                    }
                }
                if (methodName.equals("IsAssignableTo")) {  // TODO generalise
                    // These are productions in the C# grammar
                    if ("UnaryExpression".equals(currentClass) || "ElementAccess".equals(currentClass) ||
                        "SimpleName".equals(currentClass) || "BaseAccess".equals(currentClass) ||
                        "This".equals(currentClass) || "Tuple".equals(currentClass) ||
                        "ParenthesizedExpression".equals(currentClass) || "InvocationExpression".equals(currentClass) ||
                        "LiteralExpression".equals(currentClass) || "MemberAccess".equals(currentClass) ||
                        "PointerMemberAccess".equals(currentClass)) {
                        result.append("virtual ");
                    }
                    // These are productions in th Java grammar
                    else if ("Parentheses".equals(currentClass) || "Name".equals(currentClass) ||
                            "DotName".equals(currentClass) || "ArrayAccess".equals(currentClass)) {
                        result.append("virtual ");
                    }
                    else if (!"Expression".equals(currentClass)) {
                        result.append("override ");
                    }
                }
                translateModifiers(modifiers, result);
            }
            if (isOverride) {
                result.append("override ");
            }
            if (!((ASTMethodDeclaration) stmt).isConstructor()) {
                translateType(((ASTMethodDeclaration) stmt).getReturnType(), result);
                result.append(' ');
            }
            result.append(methodName);
            result.append('(');
            if (formals != null) {
                translateFormals(formals, symbols, true, true, result);
            }
            result.append(") {\n");
            internalTranslateStatement(decl.getStatements(), indent + 4, result);
            addIndent(indent, result);
            result.append("}\n\n");
            popSymbols();
        }
        else if (stmt instanceof ASTAssertStatement s) {
            result.append("Debug.Assert(");
            internalTranslateExpression(s.getCondition(), TranslationContext.UNKNOWN, result);
            result.append(", ");
            ASTExpression m = s.getMessage();
            if (m == null) {
                result.append("\"Assertion failed\"");
            }
            else {
                internalTranslateExpression(m, TranslationContext.UNKNOWN, result);
                if (!(m instanceof ASTPrimaryExpression) || (((ASTPrimaryExpression) m).getLiteral() == null)) {
                    result.append(".ToString()");
                }
                result.append(");\n");
            }
        }
        else if (stmt instanceof ASTTryStatement tryStmt) {
            result.append("try {\n");
            internalTranslateStatement(tryStmt.getBlock(), indent + 4, result);
            closeBrace(indent, result);
            List<ASTExceptionInfo> catchBlocks = tryStmt.getCatchBlocks();
            if (catchBlocks != null) {
                for (ASTExceptionInfo cb: catchBlocks) {
                    addIndent(indent, result);
                    result.append("catch (");
                    List<ASTTypeExpression> infos = cb.getExceptionTypes();
                    int n = infos.size();
                    boolean multiple = n > 1;
                    ASTTypeExpression te;

                    if (multiple) {
                        result.append("Exception ");
                    }
                    else {
                        te = infos.get(0);
                        internalTranslateExpression(te, TranslationContext.TYPE, result);
                    }
                    String excVar = cb.getVariable();
                    result.append(' ').append(excVar).append(')');
                    if (multiple) {
                        result.append(" when (");
                        for (int i = 0; i < n; i++) {
                            te = infos.get(i);
                            result.append(excVar).append(" is ");
                            internalTranslateExpression(te, TranslationContext.TYPE, result);
                            if (i < (n - 1)) {
                                result.append(" || ");
                            }
                        }
                        result.append(')');
                    }
                    result.append(" {\n");
                    internalTranslateStatement(cb.getBlock(), indent + 4, result);
                    closeBrace(indent, result);
                }
            }
            ASTStatement fb = tryStmt.getFinallyBlock();
            if (fb != null) {
                addIndent(indent, result);
                result.append("finally {\n");
                internalTranslateStatement(fb, indent + 4, result);
                closeBrace(indent, result);
            }
        }
        else if (stmt instanceof ASTEnumDeclaration enumDecl) {
            result.append("public enum ");
            result.append(enumDecl.getName());
            result.append(" {\n");
            List<String> values = enumDecl.getValues();
            if (values != null) {
                int n = values.size();
                for (int i = 0; i < n; i++) {
                    addIndent(indent + 4, result);
                    result.append(values.get(i));
                    if (i < (n - 1)) {
                        result.append(',');
                    }
                    result.append('\n');
                }
                closeBrace(indent, result);
            }
        }
        else if (stmt instanceof ASTClassDeclaration classDecl) {
            List<ASTStatement> decls = classDecl.getDeclarations();
            result.append("public class ");
            result.append(classDecl.getName());
            result.append(" {\n");
            if (decls != null) {
                for (ASTStatement decl: decls) {
                    internalTranslateStatement(decl, indent + 4, result);
                }
            }
            closeBrace(indent, result);
        }
        else if (stmt instanceof ASTBreakOrContinueStatement abcs) {
            String s = abcs.isBreak() ? "break" : "continue";
            result.append(s).append(";\n");
        }
        else {
            throw new UnsupportedOperationException("Cannot translate node of type " + getSimpleName(stmt));
        }
        if (addNewline) {
            result.append('\n');
        }
    }

    @Override public void translateProperties(String name, int indent, StringBuilder result) {
        super.translateProperties(name, indent, result);
        if (!properties.isEmpty()) {
            for (Map.Entry<String, ASTTypeExpression> prop : properties.entrySet()) {
                String k = prop.getKey();
                String s = translateIdentifier(k, TranslationContext.FIELD);
                addIndent(indent, result);
                result.append("public ");
                translateType(prop.getValue(), result);
                result.append(' ');
                result.append(s);
                result.append(" { get { return _");
                result.append(k);
                result.append("; } set { _");
                result.append(k);
                result.append(" = value; } }\n\n");
            }
        }
    }

    @Override  public String translateInjectedClass(CodeInjector injector, String name) {
        String qualifiedName = String.format("%s.%s", appSettings.getNodePackage(), name);
        List<String> nameList = injector.getParentClasses(qualifiedName);
        List<ClassOrInterfaceBodyDeclaration> decls = injector.getBodyDeclarations(qualifiedName);
        int n = decls.size();
        int indent = 4;
        StringBuilder result = new StringBuilder();

        inInterface = grammar.nodeIsInterface(name);
        try {
            addIndent(indent, result);
            result.append("public ").append(inInterface ? "interface" : "class").append(' ').append(name).append(" : ");

            result.append(String.join(", ", nameList));
            result.append(" {\n");
            if (n > 0) {
                result.append('\n');
                // Collect all the field declarations
                List<FieldDeclaration> fieldDecls = new ArrayList<>();
                for (ClassOrInterfaceBodyDeclaration decl : decls) {
                    if (decl instanceof FieldDeclaration fd) {
                        fieldDecls.add(fd);
                    }
                }
                clearFields();
                if (!fieldDecls.isEmpty()) {
                    for (FieldDeclaration fd : fieldDecls) {
                        translateStatement(fd, 8, result);
                    }
                }
                translateProperties(name, indent + 4, result);
                for (ClassOrInterfaceBodyDeclaration decl : decls) {
                    if (decl instanceof FieldDeclaration) {
                        continue;
                    }
                    if (decl instanceof MethodDeclaration) {
                        translateStatement(decl, indent + 4, result);
                    }
                    else {
                        String s = String.format("Cannot translate %s at %s", getSimpleName(decl), decl.getLocation());
                        throw new UnsupportedOperationException(s);
                    }
                }
            }
            if (!inInterface) {
                addIndent(indent + 4, result);
                result.append(String.format("public %s(Lexer tokenSource) : base(tokenSource) {}\n", name));
            }
            closeBrace(indent, result);
            return result.toString();
        }
        finally {
            inInterface = false;
        }
    }

    @Override protected void translateCast(ASTTypeExpression cast, StringBuilder result) {
        result.append('(');
        translateType(cast, result);
        result.append(") ");
    }

    @Override
    public void translateFormals(List<FormalParameter> formals, SymbolTable symbols, StringBuilder result) {
        translateFormals(transformFormals(formals), symbols, true, true, result);
    }

    @Override
    public void translateImport(String javaName, StringBuilder result) {
        String prefix = String.format("%s.", appSettings.getParserPackage());
        List<String> parts = getImportParts(javaName, prefix);
        int n = parts.size();
        String aliasName = null;
        for (int i = 0; i < n; i++) {
            String s = parts.get(i);
            if (s.endsWith("Parser")) {
                if (i == (n - 1)) {
                    if (aliasName != null) {
                        s = String.format("Unexpected alias %s", aliasName);
                        throw new UnsupportedOperationException(s);
                    }
                    aliasName = s;
                }
                parts.set(i, "Parser");
            }
            else if (s.endsWith("Lexer")) {
                if (i == (n - 1)) {
                    if (aliasName != null) {
                        s = String.format("Unexpected alias %s", aliasName);
                        throw new UnsupportedOperationException(s);
                    }
                    aliasName = s;
                }
                parts.set(i, "Lexer");
            }
        }
        result.append("    using ");
        String s = String.join(".", parts);
        if (aliasName == null) {
            aliasName = parts.get(n - 1);
        }
        if (aliasName != null) {
            result.append(aliasName).append(" = ");
        }
        result.append(prefix).append(s).append(";\n");
    }
}
