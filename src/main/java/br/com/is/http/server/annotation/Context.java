package br.com.is.http.server.annotation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.annotation.ElementType.TYPE;


import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Documented
@Retention(RUNTIME)
@Target(TYPE)
public @interface Context {
  String urlPattern() default "/";
  String tempDirectory() default "";
  long maxContentLength() default Long.MAX_VALUE;
  boolean acceptEncode() default false;
}
