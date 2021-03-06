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

package org.jboss.errai.enterprise.jaxrs.client.test;

import com.google.gwt.http.client.Response;
import org.jboss.errai.bus.client.api.ErrorCallback;
import org.jboss.errai.bus.client.api.Message;
import org.jboss.errai.enterprise.client.jaxrs.api.ResponseCallback;
import org.jboss.errai.enterprise.client.jaxrs.api.ResponseException;
import org.jboss.errai.enterprise.client.jaxrs.test.AbstractErraiJaxrsTest;
import org.jboss.errai.enterprise.jaxrs.client.TestModule;
import org.jboss.errai.enterprise.jaxrs.client.shared.entity.Entity;
import org.jboss.errai.ioc.client.Container;
import org.junit.Test;

/**
 * Testing caller injection.
 * 
 * @author Christian Sadilek <csadilek@redhat.com>
 */
public class CallerInjectionIntegrationTest extends AbstractErraiJaxrsTest {

  @Override
  public String getModuleName() {
    return "org.jboss.errai.enterprise.jaxrs.TestModule";
  }

  @Override
  protected void gwtSetUp() throws Exception {
    new Container().boostrapContainer();
    super.gwtSetUp();
  }

  @Test
  public void testInjectedPlainMethodService() {
    TestModule.getInstance().getPlainMethodTestService()
        .call(new AssertionCallback<String>("@GET failed", "get")).get();
  }

  @Test
  public void testInjectedCustomTypeMethodService() {
    TestModule.getInstance().getCustomTypeTestService()
        .call(new AssertionCallback<Entity>("@GET using custom type failed", new Entity(1, "entity1"))).getEntity();
  }

  @Test
  public void testInjectedErrorHandlingTestService() {
    TestModule.getInstance().getErrorHandlingTestService()
        .call(
            new ResponseCallback() {
              @Override
              public void callback(Response response) {
                fail("Callback should not be invoked");
              }
            },
            new ErrorCallback() {
              @Override
              public boolean error(Message message, Throwable throwable) {
                try {
                  throw throwable;
                }
                catch (ResponseException e) {
                  assertEquals("Wrong status code received", Response.SC_NOT_FOUND, e.getResponse().getStatusCode());
                  finishTest();
                }
                catch (Throwable t) {
                  fail("Expected ResponseException");
                }
                return false;
              }
            }
          ).error();
  }
}