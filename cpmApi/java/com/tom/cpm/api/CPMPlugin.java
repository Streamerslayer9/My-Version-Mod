package com.tom.cpm.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * STAND-IN for Custom Player Models' real annotation of the same name, used only so this mod can be compiled.
 * It is never put in the mod's jar: at runtime the real class from the CPM mod is used.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CPMPlugin {
}
