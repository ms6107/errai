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

package org.jboss.errai.ioc.client.container;

/**
 * A simple utility class which provides a static reference in the client to the bean manager.
 *
 * @author Mike Brock
 */
public final class IOC {
  private IOC() {
  }

  private static final IOCBeanManager manager = new IOCBeanManager();

  /**
   * Returns a reference to the bean manager in the client.
   *
   * @return the singleton instance of the client bean manager.
   * @see IOCBeanManager
   */
  public static IOCBeanManager getBeanManager() {
    return manager;
  }
}
