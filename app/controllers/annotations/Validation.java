package controllers.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Validations.class)
public @interface Validation
{
    String name() default "";
    String type() default "string";
    String rule() default "";
    boolean require() default false;
}
