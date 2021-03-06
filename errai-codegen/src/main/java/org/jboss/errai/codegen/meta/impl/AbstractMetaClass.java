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

package org.jboss.errai.codegen.meta.impl;

import static org.jboss.errai.codegen.meta.MetaClassFactory.asClassArray;
import static org.jboss.errai.codegen.util.GenUtil.classToMeta;
import static org.jboss.errai.codegen.util.GenUtil.getArrayDimensions;
import static org.jboss.errai.codegen.util.GenUtil.getBestConstructorCandidate;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.errai.codegen.meta.MetaClass;
import org.jboss.errai.codegen.meta.MetaClassFactory;
import org.jboss.errai.codegen.meta.MetaConstructor;
import org.jboss.errai.codegen.meta.MetaMethod;
import org.jboss.errai.codegen.meta.MetaParameter;
import org.jboss.errai.codegen.meta.MetaParameterizedType;
import org.jboss.errai.codegen.meta.MetaType;
import org.jboss.errai.codegen.util.GenUtil;
import org.mvel2.util.NullType;
import org.mvel2.util.ParseTools;

/**
 * @author Mike Brock <cbrock@redhat.com>
 * @author Christian Sadilek <csadilek@redhat.com>
 */
public abstract class AbstractMetaClass<T> extends MetaClass {
  private final T enclosedMetaObject;
  protected MetaParameterizedType parameterizedType;
  protected MetaParameterizedType genericSuperClass;

  protected AbstractMetaClass(T enclosedMetaObject) {
    this.enclosedMetaObject = enclosedMetaObject;
  }

  @Override
  public String getFullyQualifiedNameWithTypeParms() {
    StringBuilder buf = new StringBuilder(getFullyQualifiedName());
    buf.append(getTypeParmsString(getParameterizedType()));
    return buf.toString();
  }

  private String getTypeParmsString(MetaParameterizedType parameterizedType) {
    StringBuilder buf = new StringBuilder(512);

    if (parameterizedType != null && parameterizedType.getTypeParameters().length != 0) {
      buf.append("<");
      for (int i = 0; i < parameterizedType.getTypeParameters().length; i++) {

        MetaType typeParameter = parameterizedType.getTypeParameters()[i];
        if (typeParameter instanceof MetaParameterizedType) {
          MetaParameterizedType parameterizedTypeParemeter = (MetaParameterizedType) typeParameter;
          buf.append(((MetaClass) parameterizedTypeParemeter.getRawType()).getFullyQualifiedName());
          buf.append(getTypeParmsString(parameterizedTypeParemeter));
        }
        else {
          buf.append(((MetaClass) typeParameter).getFullyQualifiedName());
        }

        if (i + 1 < parameterizedType.getTypeParameters().length)
          buf.append(", ");
      }

      buf.append(">");
    }
    return buf.toString();
  }

  protected static MetaMethod _getMethod(MetaMethod[] methods, String name, MetaClass... parmTypes) {
    MetaMethod candidate = null;
    int bestScore = 0;
    int score;

    for (MetaMethod method : methods) {
      score = 0;
      if (method.getName().equals(name)) {
        if (method.getParameters().length == parmTypes.length) {
          if (parmTypes.length == 0) {
            score = 1;
            MetaClass retType = method.getReturnType();
            while ((retType = retType.getSuperClass()) != null) score++;
          }
          else {
            for (int i = 0; i < parmTypes.length; i++) {
              if (method.getParameters()[i].getType().isAssignableFrom(parmTypes[i])) {
                score++;
                if (method.getParameters()[i].getType().equals(parmTypes[i])) {
                  score++;
                }
              }
            }
          }
        }
      }

      if (score > bestScore) {
        bestScore = score;
        candidate = method;
      }
    }

    return candidate;
  }

  protected static MetaConstructor _getConstructor(MetaConstructor[] constructors, MetaClass... parmTypes) {
    MetaConstructor candidate = null;
    int bestScore = 0;
    int score;

    for (MetaConstructor constructor : constructors) {
      score = 0;
      if (constructor.getParameters().length == parmTypes.length) {
        if (parmTypes.length == 0) {
          score = 1;
        }
        else {
          for (int i = 0; i < parmTypes.length; i++) {
            if (constructor.getParameters()[i].getType().isAssignableFrom(parmTypes[i])) {
              score++;
              if (constructor.getParameters()[i].getType().equals(parmTypes[i])) {
                score++;
              }
            }
          }
        }
      }

      if (score > bestScore) {
        bestScore = score;
        candidate = constructor;
      }
    }

    return candidate;
  }

  private Map<String, Map<String, MetaMethod>> METHOD_MATCH_CACHE = new HashMap<String, Map<String, MetaMethod>>();

  @Override
  public MetaMethod getMethod(String name, Class... parmTypes) {
    return _getMethod(getMethods(), name, classToMeta(parmTypes));
  }

  @Override
  public MetaMethod getMethod(String name, MetaClass... parameters) {
    return _getMethod(getMethods(), name, parameters);
  }

  @Override
  public MetaMethod getDeclaredMethod(String name, Class... parmTypes) {
    return _getMethod(getDeclaredMethods(), name, classToMeta(parmTypes));
  }

  @Override
  public MetaMethod getDeclaredMethod(String name, MetaClass... parmTypes) {
    return _getMethod(getDeclaredMethods(), name, parmTypes);
  }

  @Override
  public MetaMethod getBestMatchingMethod(String name, Class... parameters) {
    MetaMethod meth = getMethod(name, parameters);
    if (meth == null || meth.isStatic()) {
      meth = null;
    }

    MetaClass[] mcParms = new MetaClass[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      mcParms[i] = MetaClassFactory.get(parameters[i]);
    }

    if (meth == null) {
      meth = getBestMatchingMethod(new GetMethodsCallback() {
        @Override
        public MetaMethod[] getMethods() {
          return AbstractMetaClass.this.getMethods();
        }
      }, name, mcParms);
    }

    return meth;
  }

  @Override
  public MetaMethod getBestMatchingMethod(String name, MetaClass... parameters) {
    return getBestMatchingMethod(new GetMethodsCallback() {
      @Override
      public MetaMethod[] getMethods() {
        return AbstractMetaClass.this.getMethods();
      }
    }, name, parameters);
  }

  @Override
  public MetaMethod getBestMatchingStaticMethod(String name, Class... parameters) {
    MetaMethod meth = getMethod(name, parameters);
    if (meth == null || !meth.isStatic()) {
      meth = null;
    }

    MetaClass[] mcParms = new MetaClass[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      mcParms[i] = MetaClassFactory.get(parameters[i]);
    }

    if (meth == null) {
      meth = getBestMatchingMethod(new GetMethodsCallback() {
        @Override
        public MetaMethod[] getMethods() {
          return getStaticMethods();
        }
      }, name, mcParms);
    }
    return meth;
  }

  @Override
  public MetaMethod getBestMatchingStaticMethod(String name, MetaClass... parameters) {
    return getBestMatchingMethod(new GetMethodsCallback() {
      @Override
      public MetaMethod[] getMethods() {
        return getStaticMethods();
      }
    }, name, parameters);
  }

  private static interface GetMethodsCallback {
    MetaMethod[] getMethods();
  }

  private MetaMethod getBestMatchingMethod(GetMethodsCallback methodsCallback, String name, MetaClass... parameters) {
    MetaMethod meth = GenUtil.getBestCandidate(parameters, name, this, methodsCallback.getMethods(), false);
    if (meth == null) {
      meth = GenUtil.getBestCandidate(parameters, name, this, methodsCallback.getMethods(), false);
    }
    return meth;
  }

  private MetaMethod[] staticMethodCache;

  private MetaMethod[] getStaticMethods() {
    if (staticMethodCache != null) {
      return staticMethodCache;
    }

    List<MetaMethod> methods = new ArrayList<MetaMethod>();

    for (MetaMethod method : getMethods()) {
      if (method.isStatic()) {
        methods.add(method);
      }
    }

    return staticMethodCache = methods.toArray(new MetaMethod[methods.size()]);
  }

  private static final Map<MetaMethod[], Method[]> METAMETHOD_TO_METHOD_CACHE = new HashMap<MetaMethod[], Method[]>();

  private static Method[] fromMetaMethod(MetaMethod[] methods) {
    Method[] result = METAMETHOD_TO_METHOD_CACHE.get(methods);
    if (result == null) {

      if (methods == null || methods.length == 0) {
        return new Method[0];
      }

      List<Method> staticMethods = new ArrayList<Method>();

      for (MetaMethod m : methods) {
        Method javaMethod = getJavaMethodFromMetaMethod(m);
        if (javaMethod != null)
          staticMethods.add(javaMethod);
      }

      result = staticMethods.toArray(new Method[staticMethods.size()]);
      METAMETHOD_TO_METHOD_CACHE.put(methods, result);
    }
    return result;
  }

  private static Method getJavaMethodFromMetaMethod(MetaMethod method) {
    Class<?> declaring = method.getDeclaringClass().asClass();
    Class<?>[] parms = getParmTypes(method.getParameters());

    try {
      return declaring.getMethod(method.getName(), parms);
    }
    catch (NoSuchMethodException e) {
      return null;
    }
  }

  private static Class<?>[] getParmTypes(MetaParameter[] parameters) {
    List<Class<?>> parmTypes = new ArrayList<Class<?>>();

    for (MetaParameter parameter : parameters) {
      parmTypes.add(parameter.getType().asClass());
    }

    return parmTypes.toArray(new Class<?>[parmTypes.size()]);
  }

  @Override
  public MetaConstructor getBestMatchingConstructor(Class... parameters) {
    return getBestMatchingConstructor(MetaClassFactory.fromClassArray(parameters));

//    Class<?> cls = asClass();
//    if (cls != null) {
//      Constructor c = ParseTools.getBestConstructorCandidate(parameters, cls, true);
//      if (c == null) {
//        c = ParseTools.getBestConstructorCandidate(parameters, cls, false);
//        if (c == null) {
//          return null;
//        }
//      }
//      MetaClass metaClass = MetaClassFactory.get(cls);
//      return metaClass.getConstructor(c.getParameterTypes());
//    }
//    else {
//      return getConstructor(parameters);
//    }
  }

  @Override
  public MetaConstructor getBestMatchingConstructor(MetaClass... parameters) {
    return GenUtil.getBestConstructorCandidate(parameters, this, getConstructors(), false);
  }

  @Override
  public MetaConstructor getConstructor(Class... parameters) {
    return _getConstructor(getConstructors(), classToMeta(parameters));
  }

  @Override
  public MetaConstructor getConstructor(MetaClass... parameters) {
    return _getConstructor(getConstructors(), parameters);
  }

  @Override
  public MetaConstructor getDeclaredConstructor(Class... parameters) {
    return _getConstructor(getDeclaredConstructors(), classToMeta(parameters));
  }

  @Override
  public final <A extends Annotation> A getAnnotation(Class<A> annotation) {
    for (Annotation a : getAnnotations()) {
      if (a.annotationType().equals(annotation))
        return (A) a;
    }
    return null;
  }

  @Override
  public final boolean isAnnotationPresent(Class<? extends Annotation> annotation) {
    return getAnnotation(annotation) != null;
  }

  public T getEnclosedMetaObject() {
    return enclosedMetaObject;
  }

  private String _hashString;

  private final String hashString() {
    if (_hashString == null) {
      _hashString = MetaClass.class.getName() + ":" + getFullyQualifiedName();
      if (getParameterizedType() != null) {
        _hashString += getParameterizedType().toString();
      }
    }
    return _hashString;
  }

  private Map<MetaClass, Boolean> ASSIGNABLE_CACHE = new HashMap<MetaClass, Boolean>();

  private static final MetaClass NULL_TYPE = MetaClassFactory.get(NullType.class);

  @Override
  public boolean isAssignableFrom(MetaClass clazz) {
    Boolean assignable = ASSIGNABLE_CACHE.get(clazz);
    if (assignable != null) {
      return assignable;
    }

    // XXX not sure if this is uncached on purpose.
    // FIXME there are no tests or documentation for this case
    if (!isPrimitive() && NULL_TYPE.equals(clazz)) return true;

    if (isArray() && clazz.isArray()) {
      return getOuterComponentType().equals(clazz.getOuterComponentType())
              && getArrayDimensions(this) == getArrayDimensions(clazz);
    }

    MetaClass sup;

    if (MetaClassFactory.get(Object.class).equals(this)) {
      assignable = true;
    }
    else if (this.getFullyQualifiedName().equals(clazz.getFullyQualifiedName())) {
      assignable = true;
    }
    else if (_hasInterface(clazz.getInterfaces(), this.getErased())) {
      assignable = true;
    }
    else if ((sup = clazz.getSuperClass()) != null) {
      assignable = isAssignableFrom(sup);
    }
    else {
      assignable = false;
    }

    ASSIGNABLE_CACHE.put(clazz, assignable);
    return assignable;
  }

  @Override
  public boolean isAssignableTo(MetaClass clazz) {
    if (clazz.equals(MetaClassFactory.get(Object.class)))
      return true;

    MetaClass cls = this;
    do {
      if (cls.equals(clazz))
        return true;
    }
    while ((cls = cls.getSuperClass()) != null);

    return _hasInterface(getInterfaces(), clazz.getErased());
  }

  private static boolean _hasInterface(MetaClass[] from, MetaClass to) {
    for (MetaClass iface : from) {
      if (to.getFullyQualifiedName().equals(iface.getErased().getFullyQualifiedName()))
        return true;
      else if (_hasInterface(iface.getInterfaces(), to))
        return true;
    }

    return false;
  }

  @Override
  public boolean isAssignableFrom(Class clazz) {
    return isAssignableFrom(MetaClassFactory.get(clazz));
  }

  @Override
  public boolean isAssignableTo(Class clazz) {
    return isAssignableTo(MetaClassFactory.get(clazz));
  }


  @Override
  public boolean isDefaultInstantiable() {
    MetaConstructor c = getConstructor(new MetaClass[0]);
    return c != null && c.isPublic();
  }

  @Override
  public MetaParameterizedType getParameterizedType() {
    return parameterizedType;
  }

  @Override
  public MetaParameterizedType getGenericSuperClass() {
    return genericSuperClass;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof AbstractMetaClass && hashString().equals(((AbstractMetaClass) o).hashString());
  }


  int _hashCode;

  @Override
  public int hashCode() {
    if (_hashCode != 0) return _hashCode;
    return _hashCode = hashString().hashCode();
  }

  private volatile transient Class<?> _asClassCache;

  @Override
  public Class<?> asClass() {
    if (_asClassCache != null) {
      return _asClassCache;
    }

    Class<?> cls = MetaClassFactory.PRIMITIVE_LOOKUP.get(getFullyQualifiedName());
    if (cls == null) {
      cls = NullType.class;

    }

    if (enclosedMetaObject instanceof Class) {
      cls = (Class<?>) enclosedMetaObject;
    }
    else if (isArray()) {
      try {
        String name = getInternalName().replaceAll("/", "\\.");

        cls = Class.forName(name, false,
                Thread.currentThread().getContextClassLoader());
      }
      catch (ClassNotFoundException e) {
        e.printStackTrace();
        cls = null;
      }
    }
    else {
      try {
        cls = Thread.currentThread().getContextClassLoader().loadClass(getFullyQualifiedName());
      }
      catch (ClassNotFoundException e) {
        // ignore.
      }
    }


//    if (cls == NullType.class) {
//      System.out.println("[Did Not Resolve " + getFullyQualifiedName() + ">>");
//      new Throwable().printStackTrace();
//      System.out.println("<<Did Not Resolve " + getFullyQualifiedName() + "]");
//
//    }

    return _asClassCache = cls;
  }


  private MetaClass _boxedCache;

  @Override
  public MetaClass asBoxed() {
    if (_boxedCache != null) return _boxedCache;
    return _boxedCache = GenUtil.getPrimitiveWrapper(this);
  }

  private MetaClass _unboxedCache;

  @Override
  public MetaClass asUnboxed() {
    if (_unboxedCache != null) return _unboxedCache;
    return _unboxedCache = GenUtil.getUnboxedFromWrapper(this);
  }

  private MetaClass _erasedCache;

  @Override
  public MetaClass getErased() {
    try {
      return _erasedCache != null ? _erasedCache : (_erasedCache = MetaClassFactory.get(getFullyQualifiedName(), true));
    }
    catch (Exception e) {
      return this;
    }
  }

  private Boolean _isPrimitiveWrapper;

  @Override
  public boolean isPrimitiveWrapper() {
    return _isPrimitiveWrapper != null ? _isPrimitiveWrapper : (_isPrimitiveWrapper = GenUtil.isPrimitiveWrapper(this));
  }

  private String _internalNameCache;

  @Override
  public String getInternalName() {
    if (_internalNameCache != null) return _internalNameCache;

    String name = getFullyQualifiedName();

    String dimString = "";
    MetaClass type = this;
    if (isArray()) {
      type = type.getComponentType();
      int dim = 1;
      while (type.isArray()) {
        dim++;
        type = type.getComponentType();
      }

      for (int i = 0; i < dim; i++) {
        dimString += "[";
      }

      name = type.getFullyQualifiedName();
    }

    if (type.isPrimitive()) {
      name = getInternalPrimitiveNameFrom(name.trim());
    }
    else {
      name = "L" + getInternalPrimitiveNameFrom(name.trim()).replaceAll("\\.", "/") + ";";
    }

    return _internalNameCache = dimString + name;
  }

  private static String getInternalPrimitiveNameFrom(String name) {
    if ("int".equals(name)) {
      return "I";
    }
    else if ("boolean".equals(name)) {
      return "Z";
    }
    else if ("byte".equals(name)) {
      return "B";
    }
    else if ("char".equals(name)) {
      return "C";
    }
    else if ("short".equals(name)) {
      return "S";
    }
    else if ("long".equals(name)) {
      return "J";
    }
    else if ("float".equals(name)) {
      return "F";
    }
    else if ("double".equals(name)) {
      return "D";
    }
    else if ("void".equals(name)) {
      return "V";
    }
    return name;
  }

  private static Class<?> getPrimitiveRefFrom(String name) {
    if ("int".equals(name)) {
      return int.class;
    }
    else if ("boolean".equals(name)) {
      return boolean.class;
    }
    else if ("byte".equals(name)) {
      return byte.class;
    }
    else if ("char".equals(name)) {
      return char.class;
    }
    else if ("short".equals(name)) {
      return short.class;
    }
    else if ("long".equals(name)) {
      return long.class;
    }
    else if ("float".equals(name)) {
      return float.class;
    }
    else if ("double".equals(name)) {
      return double.class;
    }
    else if ("void".equals(name)) {
      return void.class;
    }
    return null;
  }

  private MetaClass _outerComponentCache;

  @Override
  public MetaClass getOuterComponentType() {
    if (_outerComponentCache != null) return _outerComponentCache;

    MetaClass c = this;
    while (c.isArray()) {
      c = c.getComponentType();
    }
    return _outerComponentCache = c;
  }

  @Override
  public String toString() {
    return getCanonicalName();
  }
}
