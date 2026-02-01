package com.taoyuanx.common.audit.log.aop;

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author taoyuan @Date 2024/12/24 17:00 @Description spel工具类
 */
@Slf4j
public class SpElUtil {
  public static final String SPRING_EL_PREFIX = "#";
  private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER =
      new LocalVariableTableParameterNameDiscoverer();
  private static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();
  private static final Map<String, Expression> EXPRESSION_CACHE = new ConcurrentHashMap<>();

  private static boolean isSpEl(String originParsableTarget) {
    return StringUtils.hasLength(originParsableTarget)
        && originParsableTarget.contains(SPRING_EL_PREFIX);
  }

  public static <T> T eval(
      MethodInvocation methodInvocation,
      String expression,
      Object result,
      Class<T> evalReturnType) {
    if (methodInvocation == null || expression == null || evalReturnType == null) {
      return null;
    }
    if (!isSpEl(expression)) {
      return null;
    }
    try {
      Expression exp =
          EXPRESSION_CACHE.computeIfAbsent(expression, EXPRESSION_PARSER::parseExpression);
      EvaluationContext context =
          new MethodBasedEvaluationContext(
              methodInvocation.getThis(),
              methodInvocation.getMethod(),
              methodInvocation.getArguments(),
              PARAMETER_NAME_DISCOVERER);
      context.setVariable("result", result);
      return exp.getValue(context, evalReturnType);
    } catch (Exception e) {
      log.error("spElExecute error, expression: {}, result: {}", expression, result, e);
    }
    return null;
  }

  public static String autoEval(
      MethodInvocation methodInvocation, String expression, Object result) {
    if (!isSpEl(expression)) {
      return expression;
    }
    return eval(methodInvocation, expression, result, String.class);
  }
}
