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

package org.jboss.errai.codegen.builder.callstack;

import java.util.Map;

import org.jboss.errai.codegen.Context;
import org.jboss.errai.codegen.RenderCacheStore;
import org.jboss.errai.codegen.Statement;
import org.jboss.errai.codegen.exception.GenerationException;
import org.jboss.errai.codegen.meta.MetaClass;
import org.jboss.errai.codegen.meta.MetaParameterizedType;
import org.jboss.errai.codegen.meta.MetaType;
import org.jboss.errai.codegen.meta.MetaTypeVariable;
import org.jboss.errai.codegen.meta.MetaWildcardType;

/**
 * {@link CallElement} to create a class reference.
 *
 * @author Christian Sadilek <csadilek@redhat.com>
 */
public class LoadClassReference extends AbstractCallElement {
  private final MetaClass metaClass;

  public LoadClassReference(MetaClass type) {
    this.metaClass = type;
  }

  @Override
  public void handleCall(CallWriter writer, Context context, Statement statement) {
    writer.reset();

    try {
      nextOrReturn(writer, context, new ClassReference(metaClass));
    } 
    catch (GenerationException e) {
      blameAndRethrow(e);
    }
  }

  public static class ClassReference implements Statement {
    private MetaClass metaClass;

    public ClassReference(MetaClass metaClass) {
      this.metaClass = metaClass;
    }

    @Override
    public String generate(Context context) {
      return getClassReference(metaClass, context);
    }

    @Override
    public MetaClass getType() {
      return metaClass;
    }
  }

  public static String getClassReference(MetaType metaClass, Context context) {
    return getClassReference(metaClass, context, true);
  }


  private static final RenderCacheStore<MetaType, String> CLASS_LITERAL_RENDER_CACHE
          = new RenderCacheStore<MetaType, String>() {
    @Override
    public String getName() {
      return "CLASS_LITERAL_RENDER_CACHE";
    }
  };

  public static String getClassReference(MetaType metaClass, Context context, boolean typeParms) {
    Map<MetaType, String> cacheStore = context.getRenderingCache(CLASS_LITERAL_RENDER_CACHE);

    String result = cacheStore.get(metaClass);

    if (result == null) {
      result = _getClassReference(metaClass, context, typeParms);
    }
    return result;
  }

  private static String _getClassReference(MetaType metaClass, Context context, boolean typeParms) {

    MetaClass erased;
    if (metaClass instanceof MetaClass) {
      erased = ((MetaClass) metaClass).getErased();
    }
    else if (metaClass instanceof MetaParameterizedType) {
      MetaParameterizedType parameterizedType = (MetaParameterizedType) metaClass;
      return parameterizedType.toString();
    }
    else if (metaClass instanceof MetaTypeVariable) {
      MetaTypeVariable parameterizedType = (MetaTypeVariable) metaClass;
      return parameterizedType.getName();
    }
    else if (metaClass instanceof MetaWildcardType) {
      MetaWildcardType wildCardType = (MetaWildcardType) metaClass;
      return wildCardType.toString();
    }
    else {
      throw new RuntimeException("unknown class reference type: " + metaClass);
    }

    String fqcn = erased.getCanonicalName();
    int idx = fqcn.lastIndexOf('.');
    if (idx != -1) {

      if ((context.isAutoImportActive() || "java.lang".equals(erased.getPackageName()))
              && !context.hasImport(erased)) {
        context.addImport(erased);
      }

      if (context.hasImport(erased)) {
        fqcn = fqcn.substring(idx + 1);
      }
    }

    StringBuilder buf = new StringBuilder(fqcn);
    if (typeParms) {
      buf.append(getClassReferencesForParameterizedTypes(((MetaClass) metaClass).getParameterizedType(), context));
    }

    return buf.toString();
  }

  private static final RenderCacheStore<MetaParameterizedType, String> PARMTYPE_LITERAL_RENDER_CACHE =
          new RenderCacheStore<MetaParameterizedType, String>() {
            @Override
            public String getName() {
              return "PARMTYPE_LITERAL_RENDER_CACHE";
            }
          };

  private static String getClassReferencesForParameterizedTypes(MetaParameterizedType parameterizedType,
                                                                Context context) {
    Map<MetaParameterizedType, String> cacheStore = context.getRenderingCache(PARMTYPE_LITERAL_RENDER_CACHE);

    String result = cacheStore.get(parameterizedType);

    if (result == null) {

      StringBuilder buf = new StringBuilder(64);

      if (parameterizedType != null && parameterizedType.getTypeParameters().length != 0) {
        buf.append("<");

        for (int i = 0; i < parameterizedType.getTypeParameters().length; i++) {
          MetaType typeParameter = parameterizedType.getTypeParameters()[i];

          if (typeParameter instanceof MetaParameterizedType) {
            MetaParameterizedType parameterizedTypeParemeter = (MetaParameterizedType) typeParameter;
            buf.append(getClassReference(parameterizedTypeParemeter.getRawType(), context));
            buf.append(getClassReferencesForParameterizedTypes(parameterizedTypeParemeter, context));
          }
          else {
            // fix to a weirdness in the GWT deferred bining API;
            String ref = getClassReference(typeParameter, context);
            if ("Object".equals(ref)) {
              //ignore;
              return "";
            }

            buf.append(ref);
          }

          if (i + 1 < parameterizedType.getTypeParameters().length)
            buf.append(", ");
        }
        buf.append(">");
      }

      result = buf.toString();
      cacheStore.put(parameterizedType, result);
    }

    return result;

  }

  @Override
  public String toString() {
    return "[[LoadClassReference<" + metaClass.getFullyQualifiedName() + ">]" + next + "]";
  }
}