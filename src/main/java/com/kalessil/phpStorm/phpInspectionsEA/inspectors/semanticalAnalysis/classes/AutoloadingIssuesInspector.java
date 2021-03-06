package com.kalessil.phpStorm.phpInspectionsEA.inspectors.semanticalAnalysis.classes;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpElementVisitor;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpInspection;
import com.kalessil.phpStorm.phpInspectionsEA.utils.NamedElementUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/*
 * This file is part of the Php Inspections (EA Extended) package.
 *
 * (c) Vladimir Reznichenko <kalessil@gmail.com>
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

public class AutoloadingIssuesInspector extends BasePhpInspection {
    private static final String message = "Class autoloading might be broken: file and class names are not identical.";

    final static private Pattern laravelMigration        = Pattern.compile("\\d{4}_\\d{2}_\\d{2}_\\d{6}_.+\\.php");
    private static final Collection<String> ignoredFiles = new HashSet<>();
    static {
        ignoredFiles.add("index.php");
        ignoredFiles.add("actions.class.php"); // Symfony 1.*
    }

    @NotNull
    public String getShortName() {
        return "AutoloadingIssuesInspection";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new BasePhpElementVisitor() {
            @Override
            public void visitPhpFile(@NotNull PhpFile file) {
                final String fileName = file.getName();
                if (fileName.endsWith(".php") && !ignoredFiles.contains(fileName) && !laravelMigration.matcher(fileName).matches()) {
                    /* find out how many named classes has been defined in the file */
                    final List<PhpClass> classes = PsiTreeUtil.findChildrenOfType(file, PhpClass.class).stream()
                                .filter(clazz -> NamedElementUtil.getNameIdentifier(clazz) != null)
                                .collect(Collectors.toList());

                    /* multiple classes defined, do nothing - this is not PSR compatible */
                    if (classes.size() == 1) {
                        final PhpClass clazz = classes.get(0);

                        /* support older PSR classloading (Package_Subpackage_Class) naming */
                        String extractedClassName = clazz.getName();
                        if (clazz.getFQN().lastIndexOf('\\') == 0 && extractedClassName.indexOf('_') != -1) {
                            extractedClassName = extractedClassName.substring(1 + extractedClassName.lastIndexOf('_'));
                        }

                        /* now check if names are identical */
                        final String expectedClassName = fileName.substring(0, fileName.indexOf('.'));
                        if (!expectedClassName.equals(extractedClassName) && !expectedClassName.equals(clazz.getName())) {
                            final PsiElement classNameNode = NamedElementUtil.getNameIdentifier(clazz);
                            if (classNameNode != null) {
                                holder.registerProblem(classNameNode, message);
                            }
                        }
                    }
                    classes.clear();
                }
            }
        };
    }
}
