/*
 * Copyright 2011 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.errai.codegen;

import java.util.HashMap;
import java.util.Map;

import org.jboss.errai.codegen.builder.callstack.CallWriter;
import org.jboss.errai.codegen.literal.ClassLiteral;
import org.jboss.errai.codegen.literal.TypeLiteral;
import org.jboss.errai.codegen.meta.MetaClass;
import org.jboss.errai.codegen.meta.MetaClassFactory;
import org.jboss.errai.codegen.meta.MetaMethod;
import org.jboss.errai.codegen.meta.MetaParameterizedType;
import org.jboss.errai.codegen.meta.MetaType;
import org.jboss.errai.codegen.meta.MetaTypeVariable;
import org.jboss.errai.codegen.meta.MetaWildcardType;
import org.mvel2.util.NullType;

/**
 * Represents a method invocation statement.
 *
 * @author Christian Sadilek <csadilek@redhat.com>
 * @author Mike Brock
 */
public class MethodInvocation extends AbstractStatement {
  private final MetaClass inputType;
  private final MetaMethod method;
  private final CallParameters callParameters;
  private Map<String, MetaClass> typeVariables;
  private CallWriter writer;

  public MethodInvocation(CallWriter writer, MetaClass inputType, MetaMethod method, CallParameters callParameters) {
    this.inputType = inputType;
    this.method = method;
    this.callParameters = callParameters;
    this.writer = writer;
  }

  String generatedCache;

  @Override
  public String generate(Context context) {
    if (generatedCache != null) return generatedCache;

    StringBuilder buf = new StringBuilder(128);
    buf.append(method.getName()).append(callParameters.generate(context));
    return generatedCache = buf.toString();
  }

  @Override
  public MetaClass getType() {
    MetaClass returnType = method.getReturnType();

    if (method.getGenericReturnType() != null && method.getGenericReturnType() instanceof MetaTypeVariable) {
      typeVariables = new HashMap<String, MetaClass>();
      resolveTypeVariables();

      MetaTypeVariable typeVar = (MetaTypeVariable) method.getGenericReturnType();
      if (typeVariables.containsKey(typeVar.getName())) {
        returnType = typeVariables.get(typeVar.getName());
      }
      else if (writer.getTypeParm(typeVar.getName()) != null) {
        returnType = writer.getTypeParm(typeVar.getName());
      }
      else {
        // returning NullType as a stand-in for an unbounded wildcard type since this is a parameterized method
        // and there is not RHS qualification for the parameter.
        //
        // ie when calling GWT.create() and assigning it to a concrete type.
        //
        // TODO: might be worth flushing this out for clarify in the future.
        return MetaClassFactory.get(NullType.class);
      }
    }

    assert returnType != null;

    return returnType;
  }

  // Resolves type variables by inspecting call parameters
  private void resolveTypeVariables() {
    MetaParameterizedType gSuperClass = inputType.getGenericSuperClass();
    MetaClass superClass = inputType.getSuperClass();

    if (superClass != null && superClass.getTypeParameters() != null & superClass.getTypeParameters().length > 0
            && gSuperClass != null && gSuperClass.getTypeParameters().length > 0) {
      for (int i = 0; i < superClass.getTypeParameters().length; i++) {
        String varName = superClass.getTypeParameters()[i].getName();
        if (gSuperClass.getTypeParameters()[i] instanceof MetaClass) {
          typeVariables.put(varName, (MetaClass) gSuperClass.getTypeParameters()[i]);
        }
        else if (gSuperClass.getTypeParameters()[i] instanceof MetaWildcardType) {
          typeVariables.put(varName, MetaClassFactory.get(Object.class));
        }
        else {
          MetaClass clazz = writer.getTypeParm(varName);
          if (clazz != null) {
            typeVariables.put(varName, clazz);
          }
        }
      }
    }

    int methodParmIndex = 0;
    for (MetaType methodParmType : method.getGenericParameterTypes()) {
      Statement parm = callParameters.getParameters().get(methodParmIndex);

      MetaType callParmType;
      if (parm instanceof TypeLiteral) {
        callParmType = ((TypeLiteral) parm).getActualType();
      }
      else {
        callParmType = parm.getType();
      }

      resolveTypeVariable(methodParmType, callParmType);
      methodParmIndex++;
    }
  }

  private void resolveTypeVariable(MetaType methodParmType, MetaType callParmType) {
    if (methodParmType instanceof MetaTypeVariable) {
      MetaTypeVariable typeVar = (MetaTypeVariable) methodParmType;
      typeVariables.put(typeVar.getName(), (MetaClass) callParmType);
    }
    else if (methodParmType instanceof MetaParameterizedType) {
      MetaType parameterizedCallParmType;
      if (callParmType instanceof MetaParameterizedType) {
        parameterizedCallParmType = callParmType;
      }
      else {
        parameterizedCallParmType = ((MetaClass) callParmType).getParameterizedType();
      }

      MetaParameterizedType parameterizedMethodParmType = (MetaParameterizedType) methodParmType;
      int typeParmIndex = 0;
      for (MetaType typeParm : parameterizedMethodParmType.getTypeParameters()) {
        if (parameterizedCallParmType != null) {
          resolveTypeVariable(typeParm,
                  ((MetaParameterizedType) parameterizedCallParmType).getTypeParameters()[typeParmIndex++]);
        }
        else {
          resolveTypeVariable(typeParm, callParmType);
        }
      }
    }
  }
}