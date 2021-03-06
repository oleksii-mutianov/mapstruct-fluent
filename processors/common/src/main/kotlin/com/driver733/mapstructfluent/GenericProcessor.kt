package com.driver733.mapstructfluent

import com.squareup.kotlinpoet.FunSpec
import org.mapstruct.AfterMapping
import org.mapstruct.BeforeMapping
import org.mapstruct.Mapper
import java.io.File
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier.ABSTRACT
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.VariableElement
import javax.tools.Diagnostic

class GenericProcessor {

    fun process(roundEnv: RoundEnvironment?, procEnv: ProcessingEnvironment, processor: MapperMethodProcessor) =
        generatedSourcesDirPath(procEnv)
            .also { if (it == null) return false }
            .let { src -> findAndProcessMappers(roundEnv, src, processor) }
            .let { true }

    private fun generatedSourcesDirPath(env: ProcessingEnvironment) =
        env.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]
            .let {
                if (it == null) {
                    env.messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "Can't find the target directory for generated Kotlin files"
                    )
                    null
                } else {
                    it
                }
            }

    private fun findAndProcessMappers(
        roundEnv: RoundEnvironment?,
        src: String?,
        processor: MapperMethodProcessor
    ) =
        roundEnv
            ?.getElementsAnnotatedWith(Mapper::class.java)
            ?.forEach { mapper ->
                mapper
                    .takeIf { it.modifiers.contains(ABSTRACT) }
                    ?.enclosedElements
                    ?.filter { it.kind == ElementKind.METHOD }
                    ?.map { it as ExecutableElement }
                    ?.filter { it.modifiers.contains(PUBLIC) }
                    ?.filter(b())
                    ?.also { methods ->
                        val fileSpecBuilder = processor.fileSpecBuilder(mapper)

                        methods.forEach { method ->
                            val receiver = method.parameters.first().kotlinType()
                            val otherParameters = method.parameters.drop(1).map(VariableElement::toParameterSpec)

                            processor.process(fileSpecBuilder, method, mapper)

                            fileSpecBuilder
                                .addFunction(
                                    FunSpec
                                        .builder("${method.simpleName}")
                                        .receiver(receiver)
                                        .addParameters(otherParameters)
                                        .addStatement(
                                            "return mapper.${method.simpleName}(this${otherParameters.joinToString()})"
                                        )
                                        .build()
                                )
                                .build()
                                .writeTo(File(src!!).apply { mkdir() })
                        }
                    }
            }

    private fun b() = { it: ExecutableElement ->
        it.modifiers.contains(ABSTRACT) || it.hasNotAnnotations(
            BeforeMapping::class.java,
            AfterMapping::class.java
        )
    }

    private fun <T : Annotation> ExecutableElement.hasNotAnnotations(vararg classes: Class<out T>) =
        classes.all {
            hasNotAnnotation(it)
        }

    private fun <T : Annotation> ExecutableElement.hasNotAnnotation(clazz: Class<T>) =
        !hasAnnotation(clazz)

    private fun <T : Annotation> ExecutableElement.hasAnnotation(clazz: Class<T>) =
        getAnnotation(clazz) != null
}
