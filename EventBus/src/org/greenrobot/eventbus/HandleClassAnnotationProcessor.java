package org.greenrobot.eventbus;

import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.tools.Diagnostic;

import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.AGGREGATING;

@SupportedAnnotationTypes("org.greenrobot.eventbus.HandleClass")
@IncrementalAnnotationProcessor(AGGREGATING)
public class HandleClassAnnotationProcessor extends AbstractProcessor {
    private final Set<TypeElement> handleClasses = new HashSet<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        Messager messager = processingEnv.getMessager();

        if (roundEnvironment.processingOver()) {
            if (!annotations.isEmpty()) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Unexpected processing state: annotations still available after processing over");
                return false;
            }
        }
        if (annotations.isEmpty()) {
            return false;
        }

        collectHandleClasses(annotations, roundEnvironment, messager);

        if (handleClasses.isEmpty()) {
            messager.printMessage(Diagnostic.Kind.WARNING, "No @HandleClass annotations found");
        }

        return true;
    }

    private void collectHandleClasses(Set<? extends TypeElement> annotations, RoundEnvironment env, Messager messager) {
        for (TypeElement annotation : annotations) {
            Set<? extends Element> elements = env.getElementsAnnotatedWith(annotation);
            for (Element element : elements) {
                if (element instanceof TypeElement) {
                    TypeElement handleClass = (TypeElement) element;
                    handleClasses.add(handleClass);
                } else {
                    messager.printMessage(Diagnostic.Kind.ERROR, "@HandleClass is only valid for classes", element);
                }
            }
        }
    }

    private TypeMirror getParamTypeMirror(VariableElement param, Messager messager) {
        TypeMirror typeMirror = param.asType();
        // Check for generic type
        if (typeMirror instanceof TypeVariable) {
            TypeMirror upperBound = ((TypeVariable) typeMirror).getUpperBound();
            if (upperBound instanceof DeclaredType) {
                if (messager != null) {
                    messager.printMessage(Diagnostic.Kind.NOTE, "Using upper bound type " + upperBound +
                            " for generic parameter", param);
                }
                typeMirror = upperBound;
            }
        }
        return typeMirror;
    }

    private TypeElement getSuperclass(TypeElement type) {
        if (type.getSuperclass().getKind() == TypeKind.DECLARED) {
            TypeElement superclass = (TypeElement) processingEnv.getTypeUtils().asElement(type.getSuperclass());
            String name = superclass.getQualifiedName().toString();
            if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("android.")) {
                // Skip system classes, this just degrades performance
                return null;
            } else {
                return superclass;
            }
        } else {
            return null;
        }
    }

    private String getClassString(TypeElement typeElement, String myPackage) {
        PackageElement packageElement = getPackageElement(typeElement);
        String packageString = packageElement.getQualifiedName().toString();
        String className = typeElement.getQualifiedName().toString();
        if (packageString != null && !packageString.isEmpty()) {
            if (packageString.equals(myPackage)) {
                className = cutPackage(myPackage, className);
            } else if (packageString.equals("java.lang")) {
                className = typeElement.getSimpleName().toString();
            }
        }
        return className;
    }

    private String cutPackage(String paket, String className) {
        if (className.startsWith(paket + '.')) {
            // Don't use TypeElement.getSimpleName, it doesn't work for us with inner classes
            return className.substring(paket.length() + 1);
        } else {
            // Paranoia
            throw new IllegalStateException("Mismatching " + paket + " vs. " + className);
        }
    }

    private PackageElement getPackageElement(TypeElement subscriberClass) {
        Element candidate = subscriberClass.getEnclosingElement();
        while (!(candidate instanceof PackageElement)) {
            candidate = candidate.getEnclosingElement();
        }
        return (PackageElement) candidate;
    }
}
