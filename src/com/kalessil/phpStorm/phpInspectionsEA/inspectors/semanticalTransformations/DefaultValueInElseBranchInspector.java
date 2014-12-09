package com.kalessil.phpStorm.phpInspectionsEA.inspectors.semanticalTransformations;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.php.lang.parser.PhpElementTypes;
import com.jetbrains.php.lang.psi.elements.*;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpElementVisitor;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpInspection;
import com.kalessil.phpStorm.phpInspectionsEA.utils.ExpressionSemanticUtil;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;

public class DefaultValueInElseBranchInspector extends BasePhpInspection {
    private static final String strProblemDescription = "Assignment in this branch shall be moved before if";

    @NotNull
    public String getDisplayName() {
        return "Semantics: hidden default value in conditionals";
    }

    @NotNull
    public String getShortName() {
        return "DefaultValueInElseBranchInspection";
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new BasePhpElementVisitor() {
            public void visitPhpIf(If ifStatement) {
                /** skip ifs without else */
                Else objElseStatement = ifStatement.getElseBranch();
                if (null == objElseStatement) {
                    return;
                }


                /** collect all group statement for further analysis */
                LinkedList<GroupStatement> objGroupStatementsList = new LinkedList<>();
                objGroupStatementsList.add(ExpressionSemanticUtil.getGroupStatement(ifStatement));
                for (ControlStatement objElseIf : ifStatement.getElseIfBranches()) {
                    objGroupStatementsList.add(ExpressionSemanticUtil.getGroupStatement(objElseIf));
                }
                objGroupStatementsList.add(ExpressionSemanticUtil.getGroupStatement(objElseStatement));


                /** collect assignments or stop inspecting when structure expectations are not met */
                LinkedList<AssignmentExpression> objAssignmentsList = new LinkedList<>();
                for (GroupStatement objGroup : objGroupStatementsList) {
                    if (null == objGroup || ExpressionSemanticUtil.countExpressionsInGroup(objGroup) != 1) {
                        objGroupStatementsList.clear();
                        return;
                    }

                    AssignmentExpression objAssignmentExpression = null;
                    for (PsiElement objIfChild : objGroup.getChildren()) {
                        if (objIfChild instanceof Statement && objIfChild.getFirstChild() instanceof AssignmentExpression) {
                            objAssignmentExpression = (AssignmentExpression) objIfChild.getFirstChild();
                            break;
                        }
                    }
                    if (null == objAssignmentExpression) {
                        objGroupStatementsList.clear();
                        return;
                    }

                    objAssignmentsList.add(objAssignmentExpression);
                }
                objGroupStatementsList.clear();


                /** ensure all assignments has one subject */
                PhpPsiElement objSubjectToCompareWith = objAssignmentsList.peekFirst().getVariable();
                if (null == objSubjectToCompareWith) {
                    objAssignmentsList.clear();
                    return;
                }

                PhpPsiElement objSubjectFromExpression;
                for (AssignmentExpression objSubjectAssignmentExpression : objAssignmentsList) {
                    objSubjectFromExpression = objSubjectAssignmentExpression.getVariable();
                    if (null == objSubjectFromExpression) {
                        objAssignmentsList.clear();
                        return;
                    }

                    if (!PsiEquivalenceUtil.areElementsEquivalent(objSubjectToCompareWith, objSubjectFromExpression)) {
                        objAssignmentsList.clear();
                        return;
                    }
                }


                /** verify candidate value: array/string/number/constant */
                PhpPsiElement objCandidate = objAssignmentsList.getLast().getValue();
                objAssignmentsList.clear();
                if (!this.isDefaultValueCandidateFits(objCandidate)) {
                    return;
                }


                /** point the problem out */
                holder.registerProblem(objElseStatement.getFirstChild(), strProblemDescription, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }

            /**
             * @param objCandidate to check
             * @return boolean
             */
            private boolean isDefaultValueCandidateFits(PhpPsiElement objCandidate) {
                /** quick check on expression type basis*/
                if (
                    objCandidate instanceof StringLiteralExpression ||
                    objCandidate instanceof ArrayCreationExpression ||
                    objCandidate instanceof ConstantReference
                ) {
                    return true;
                }

                /** numbers check needs more detailed inspection */
                //noinspection RedundantIfStatement
                if (
                    objCandidate instanceof PhpExpression &&
                    objCandidate.getNode().getElementType() == PhpElementTypes.NUMBER
                ) {
                    return true;
                }

                return false;
            }
        };
    }
}