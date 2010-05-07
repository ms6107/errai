/*
 * Copyright 2009 JBoss, a divison Red Hat, Inc
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
package org.jboss.errai.bus.server.service.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import org.jboss.errai.bus.client.api.ResourceProvider;
import org.jboss.errai.bus.client.framework.MessageBus;
import org.jboss.errai.bus.server.ErraiBootstrapFailure;
import org.jboss.errai.bus.server.annotations.ExtensionComponent;
import org.jboss.errai.bus.server.api.ErraiConfig;
import org.jboss.errai.bus.server.api.ErraiConfigExtension;
import org.jboss.errai.bus.server.service.ErraiServiceConfigurator;
import org.jboss.errai.bus.server.service.ErraiServiceConfiguratorImpl;
import org.jboss.errai.bus.server.util.ConfigUtil;
import org.jboss.errai.bus.server.util.ConfigVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.jboss.errai.bus.server.util.ConfigUtil.visitAllTargets;

/**
 * @author: Heiko Braun <hbraun@redhat.com>
 * @date: May 7, 2010
 */
public class LoadExtensions implements BootstrapExecution
{
  private Logger log = LoggerFactory.getLogger(LoadExtensions.class);

  public void execute(final BootstrapContext context)
  {
    final ErraiServiceConfiguratorImpl config = (ErraiServiceConfiguratorImpl)context.getConfig();

    log.info("beging searching for Errai extensions ...");
    boolean autoScanModules = true;

    final Set<String> loadedComponents = new HashSet<String>();

    /*** Extensions  ***/
    if (config.hasProperty("errai.auto_load_extensions")) {
      autoScanModules = Boolean.parseBoolean(config.getProperty("errai.auto_load_extensions"));
    }
    if (autoScanModules) {

      final ErraiConfig erraiConfig = new ErraiConfig() {
        public void addBinding(Class<?> type, ResourceProvider provider) {
          config.getExtensionBindings().put(type, provider);
        }

        public void addResourceProvider(String name, ResourceProvider provider) {
          config.getResourceProviders().put(name, provider);
        }

        public void addSerializableType(Class<?> type) {
          log.info("Marked " + type + " as serializable.");
          loadedComponents.add(type.getName());
          config.getSerializableTypes().add(type);
        }
      };

      // Search for Errai extensions.
      List<File> configRootTargets = ConfigUtil.findAllConfigTargets();
      visitAllTargets(configRootTargets, new ConfigVisitor() {
        public void visit(Class<?> loadClass) {
          if (ErraiConfigExtension.class.isAssignableFrom(loadClass)
              && loadClass.isAnnotationPresent(ExtensionComponent.class) && !loadedComponents.contains(loadClass.getName())) {

            loadedComponents.add(loadClass.getName());

            // We have an annotated ErraiConfigExtension.  So let's configure it.
            final Class<? extends ErraiConfigExtension> clazz =
                loadClass.asSubclass(ErraiConfigExtension.class);


            log.info("found extension " + clazz.getName());

            try {

              final Runnable create = new Runnable() {
                public void run() {
                  AbstractModule module = new AbstractModule() {
                    @Override
                    protected void configure() {
                      bind(ErraiConfigExtension.class).to(clazz);
                      bind(ErraiServiceConfigurator.class).toInstance(config);
                      bind(MessageBus.class).toInstance(context.getBus());

                      // Add any extension bindings.
                      for (Map.Entry<Class<?>, ResourceProvider> entry : config.getExtensionBindings().entrySet()) {
                        bind(entry.getKey()).toProvider(new GuiceProviderProxy(entry.getValue()));
                      }
                    }
                  };
                  Guice.createInjector(module)
                      .getInstance(ErraiConfigExtension.class)
                      .configure(erraiConfig);
                }
              };

              try {
                create.run();
              }
              catch (CreationException e) {
                log.info("extension " + clazz.getName() + " cannot be bound yet, deferring ...");
                context.deferr(create);
              }

            }
            catch (Throwable e) {
              throw new ErraiBootstrapFailure("could not initialize extension: " + loadClass.getName(), e);
            }
          }
        }
      });


      for (Class bindingType : config.getExtensionBindings().keySet()) {
        log.info("added extension binding: " + bindingType.getName());
      }

      log.info("total extension binding: " + config.getExtensionBindings().keySet().size());

    } else {
      log.info("auto-loading of extensions disabled.");
    }
  }
}